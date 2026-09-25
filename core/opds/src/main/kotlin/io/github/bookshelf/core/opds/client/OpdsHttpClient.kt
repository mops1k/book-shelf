package io.github.bookshelf.core.opds.client

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.auth.OpdsCredentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP-клиент OPDS — единственное место приложения с доступом в сеть (docs/opds.md §4.1).
 *
 * Требования к реализации:
 * * OkHttp + отдельный дисковый `Cache`; conditional GET по `ETag`/`Last-Modified`;
 * * таймауты: connect 10 с, read 30 с; ограниченное число редиректов;
 * * ограничение размера тела фида (порядка 10 МБ), иначе фид обрывается с понятной ошибкой;
 * * `User-Agent` с версией приложения;
 * * **HTTPS по умолчанию**; `http://` допускается только для домашних каталогов в LAN и только
 *   после явного подтверждения пользователя;
 * * аутентификация Basic/Digest + cookie-сессия (`CookieJar` в памяти, сохранение опционально);
 * * никакие внешние ресурсы фида не загружаются автоматически; обложки — только по явному действию.
 */
interface OpdsHttpClient {

    /**
     * GET-запрос фида или файла.
     *
     * @param url абсолютный http/https-адрес.
     * @param conditional заголовки условного запроса; null — обычный GET.
     * @param credentials логин/пароль каталога; null — запрос без авторизации.
     */
    suspend fun get(
        url: String,
        conditional: ConditionalGet? = null,
        credentials: OpdsCredentials? = null,
    ): AppResult<HttpResponse>
}

/**
 * Ответ HTTP-клиента.
 *
 * @param status код ответа; 304 при conditional GET означает «не изменилось».
 * @param body тело ответа (для 304 — пустое).
 * @param etag значение `ETag` для следующего conditional GET.
 * @param lastModified значение `Last-Modified`.
 * @param finalUrl URL после редиректов — база для разрешения относительных ссылок.
 */
data class HttpResponse(
    val status: Int,
    val body: String,
    val etag: String?,
    val lastModified: String?,
    val finalUrl: String,
)

/** Заголовки условного GET; оба поля необязательны, но хотя бы одно обычно задано. */
data class ConditionalGet(
    val etag: String?,
    val lastModified: String?,
)

/**
 * Заглушка клиента на OkHttp.
 *
 * Конструктор без зависимостей: `OkHttpClient` с таймаутами и дисковым кэшем создаётся
 * в Sprint 8 и внедряется через DI, а не собирается внутри класса.
 */
@Singleton
class OkHttpOpdsClient @Inject constructor() : OpdsHttpClient {

    // STUB(Sprint 8): OkHttpClient с кэшем, таймаутами, User-Agent, Basic/Digest и CookieJar.

    override suspend fun get(
        url: String,
        conditional: ConditionalGet?,
        credentials: OpdsCredentials?,
    ): AppResult<HttpResponse> =
        TODO("STUB(Sprint 8): GET через OkHttp с conditional GET и лимитом размера")
}
