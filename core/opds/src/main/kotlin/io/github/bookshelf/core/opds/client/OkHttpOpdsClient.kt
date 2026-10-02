package io.github.bookshelf.core.opds.client

import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.auth.CredentialsHeader
import io.github.bookshelf.core.opds.auth.OpdsCredentials
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/**
 * HTTP-клиент каталогов на OkHttp (docs/opds.md §2).
 *
 * Три решения, каждое из которых иначе дало бы тихую поломку:
 *
 * **1. Тело ответа ограничено.** Каталог — это недоверенный вход: без предела сервер может
 * отдать сотни мегабайт, и на устройстве с 1–2 ГБ RAM это убийство процесса. Читаем потоково и
 * обрываем на [MAX_BODY_BYTES].
 *
 * **2. Редиректы до 5 и только http/https.** Каталог сам указывает, куда его ведут ссылки;
 * без проверки схемы редирект на `file://` или `content://` увёл бы запрос за пределы сети.
 *
 * **3. `finalUrl` возвращается вместе с телом.** Относительные ссылки разрешаются относительно
 * адреса **после** редиректов: иначе фид, отданный с `301` на другой путь, дал бы битые ссылки.
 *
 * **4. Имена резолвятся через DoH, если системный DNS отказал.** У части провайдеров имена
 * библиотечных зеркал не резолвятся вовсе (на Bigme B6 `flibusta.is` — «unknown host»), и менять
 * системные настройки пользователя приложение не вправе. Свои настройки не нужны: адрес
 * спрашивается по HTTPS у публичного DoH ([DohResolver]) только тогда, когда система не ответила.
 *
 * **5. Транспортная ошибка https повторяется по http.** Только без учётных данных: пароль каталога
 * по открытому каналу не отправляется никогда.
 */
@Singleton
class OkHttpOpdsClient @Inject constructor(
    /** Резолвер для случаев, когда системный DNS имя не отдал (docs/opds.md). */
    private val doh: HostResolver,
    /** Диспетчеры приложения: свой `Dispatchers.IO` в модуле запрещён (docs/tdd.md §5). */
    private val dispatchers: AppDispatchers,
) : OpdsHttpClient {

    private val http: OkHttpClient by lazy { buildClient() }

    override suspend fun get(
        url: String,
        conditional: ConditionalGet?,
        credentials: OpdsCredentials?,
    ): AppResult<HttpResponse> = withContext(dispatchers.io) {
        val target = url.toHttpUrlOrNull()
            ?: return@withContext AppResult.failure(
                AppError.InvalidArgument(what = "url", message = "Некорректный адрес: $url"),
            )

        when (val attempt = execute(request(target, conditional, credentials))) {
            is Attempt.Completed -> attempt.result

            is Attempt.Transport -> {
                // Первая ошибка относится к адресу, который ввёл пользователь: если и повтор
                // не удался, показываем именно её.
                val plain = plainRetryUrl(target, hasCredentials = credentials != null)
                if (plain == null) {
                    AppResult.failure(attempt.error.toAppError(target.host))
                } else {
                    when (val retry = execute(request(plain, conditional, null))) {
                        is Attempt.Completed -> retry.result
                        is Attempt.Transport -> AppResult.failure(attempt.error.toAppError(target.host))
                    }
                }
            }
        }
    }

    private fun request(
        target: HttpUrl,
        conditional: ConditionalGet?,
        credentials: OpdsCredentials?,
    ): Request {
        val builder = Request.Builder().url(target).get()
        CredentialsHeader.apply(builder, credentials)
        conditional?.etag?.let { builder.header("If-None-Match", it) }
        conditional?.lastModified?.let { builder.header("If-Modified-Since", it) }
        return builder.build()
    }

    /**
     * Один запрос: отличаем «сервер ответил» от «транспорт не поднялся».
     *
     * Ответ с кодом 404 или 500 — это [Attempt.Completed]: повторять его по http бессмысленно,
     * ошибку нужно показать пользователю как есть.
     */
    private fun execute(request: Request): Attempt = runCatching {
        http.newCall(request).execute()
    }.fold(
        onSuccess = { response -> Attempt.Completed(response.toResult(request.url)) },
        onFailure = { error -> Attempt.Transport(error) },
    )

    private sealed interface Attempt {
        data class Completed(val result: AppResult<HttpResponse>) : Attempt
        data class Transport(val error: Throwable) : Attempt
    }

    private fun Response.toResult(requested: HttpUrl): AppResult<HttpResponse> = use { response ->
        when {
            // 304 — не ошибка: условный запрос отработал, тело пустое, кэш остаётся годным.
            response.code == HTTP_NOT_MODIFIED ->
                AppResult.success(response.toHttpResponse(body = ""))

            !response.isSuccessful -> AppResult.failure(
                AppError.Network(
                    httpCode = response.code,
                    message = "Сервер каталога ответил ${response.code}",
                ),
            )

            else -> response.readBody()
        }
    }

    /**
     * Тело успешного ответа: пустой ответ и слишком большой фид — отказы, иначе текст и заголовки.
     *
     * Вынесено из `toResult` отдельной функцией: вложенные проверки тела давали порог
     * `NestedBlockDepth`, а чтение ответа читается отдельно от разбора кода состояния.
     */
    private fun Response.readBody(): AppResult<HttpResponse> {
        val body = body ?: return AppResult.failure(AppError.Network(message = "Пустой ответ сервера"))
        val text = readCapped(body) ?: return AppResult.failure(
            AppError.Network(message = "Фид больше ${MAX_BODY_BYTES / 1024 / 1024} МБ"),
        )
        return AppResult.success(toHttpResponse(body = text))
    }

    /** Заголовки и текст ответа в одном виде: адрес берётся после редиректов. */
    private fun Response.toHttpResponse(body: String): HttpResponse = HttpResponse(
        status = code,
        body = body,
        etag = header("ETag"),
        lastModified = header("Last-Modified"),
        finalUrl = request.url.toString(),
    )

    /** Тело с пределом: возврат `null` означает «превышен лимит», а не «прочитано пусто». */
    private fun readCapped(body: okhttp3.ResponseBody): String? = runCatching {
        val buffer = Buffer()
        val source = body.source()
        var total = 0L
        while (true) {
            // Байты копируются В БУФЕР: source.request() лишь наполняет внутренний буфер
            // источника, и без read в Buffer тело читалось пустым — каталог не открылся бы.
            val read = source.read(buffer, COPY_CHUNK)
            if (read == -1L) break
            total += read
            if (total > MAX_BODY_BYTES) return null
        }
        buffer.readUtf8()
    }.getOrNull()

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        // Системный DNS, а при его отказе — DoH: зеркала библиотек бывают недоступны именно
        // из-за блокировки имён, и тогда TLS даже не начинается.
        .dns(FallbackDns(system = okhttp3.Dns.SYSTEM, doh = doh))
        // MODERN_TLS (1.2+) сначала, COMPATIBLE_TLS — для старых зеркал, CLEARTEXT — для http.
        .connectionSpecs(
            listOf(
                okhttp3.ConnectionSpec.MODERN_TLS,
                okhttp3.ConnectionSpec.COMPATIBLE_TLS,
                okhttp3.ConnectionSpec.CLEARTEXT,
            ),
        )
        .cookieJar(InMemoryCookieJar)
        .addInterceptor { chain ->
            // Без User-Agent некоторые серверы отдают 403 на незнакомый клиент.
            chain.proceed(
                chain.request().newBuilder().header("User-Agent", USER_AGENT).build(),
            )
        }
        .build()

    /**
     * Ошибка транспорта — с именем хоста и причиной.
     *
     * Общее «сервер недоступен» не даёт понять, что случилось: блокировка DNS, TLS или таймаут —
     * это разные проблемы и лечатся по-разному (docs/opds.md).
     */
    private fun Throwable.toAppError(host: String): AppError = when (this) {
        is java.net.SocketTimeoutException -> AppError.Network(
            message = "Сервер $host не отвечает (таймаут ${CONNECT_TIMEOUT_SECONDS} с)",
            cause = this,
        )

        is java.net.UnknownHostException -> AppError.Network(
            message = "Сервер не найден (DNS): $host. Проверьте адрес и DNS в сети.",
            cause = this,
        )

        is javax.net.ssl.SSLException -> AppError.Network(
            message = "Ошибка TLS с $host: ${message ?: "рукопожатие не удалось"}",
            cause = this,
        )

        is IOException -> AppError.Network(message = message ?: "Сеть недоступна: $host", cause = this)

        else -> AppError.Unknown(message = message, cause = this)
    }

    /** Куки в памяти: сессия каталога не должна переживать перезапуск приложения. */
    private object InMemoryCookieJar : CookieJar {
        private val store = mutableMapOf<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val incoming = cookies.associateBy { it.name }
            val kept = store[url.host].orEmpty().filterNot { it.name in incoming }
            store[url.host] = kept + cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()
    }

    private companion object {
        const val USER_AGENT = "BookShelf/1.0 (Android)"
        const val MAX_BODY_BYTES = 16L * 1024L * 1024L
        const val COPY_CHUNK = 64L * 1024L
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
        const val HTTP_NOT_MODIFIED = 304
    }
}

