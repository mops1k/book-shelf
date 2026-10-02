package io.github.bookshelf.core.opds.client

import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Источник адресов по имени: системный DNS или DoH.
 *
 * Интерфейс существует ради тестов: цепочку «система → DoH» проверяют подставными источниками,
 * без реальной сети.
 */
fun interface HostResolver {

    /** @return адреса хоста или пустой список, если имя не разрешилось. */
    fun resolve(host: String): List<InetAddress>
}

/**
 * Резолвер DNS-over-HTTPS («OPDS»).
 *
 * Зачем: у части провайдеров имена библиотечных зеркал не резолвятся системным DNS
 * (на Bigme B6 `flibusta.is` даёт «unknown host», при этом сеть работает). Менять системные
 * настройки пользователя приложение не имеет права — WRITE_SECURE_SETTINGS ему не выдан, —
 * поэтому обходим блокировку внутри своего трафика: спрашиваем адрес по HTTPS у публичных
 * DoH-серверов (RFC 8484, JSON-ответ).
 *
 * Порядок источников: сначала обычные имена, потом `1.1.1.1` — он нужен на случай, когда системный
 * DNS не резолвит даже имя самого DoH-провайдера. Ответы кэшируются в памяти, чтобы открытие
 * каталога не ждало DNS на каждый запрос.
 */
class DohResolver(
    private val http: OkHttpClient,
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
    private val clock: () -> Long = System::currentTimeMillis,
) : HostResolver {

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    override fun resolve(host: String): List<InetAddress> {
        cache[host]?.let { entry ->
            if (entry.expiresAt > clock()) return entry.addresses
        }
        endpoints.forEach { endpoint ->
            val addresses = query(endpoint, host)
            if (addresses.isNotEmpty()) {
                cache[host] = Entry(addresses = addresses, expiresAt = clock() + CACHE_MILLIS)
                return addresses
            }
        }
        return emptyList()
    }

    /** Один DoH-запрос; любая ошибка (сеть, HTTP-код, битый JSON) означает «нет ответа». */
    private fun query(endpoint: String, host: String): List<InetAddress> = runCatching {
        val url = "$endpoint?name=$host&type=$TYPE_A"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return emptyList()
            }
            parseAnswers(response.body?.string().orEmpty())
        }
    }.getOrDefault(emptyList())

    private fun parseAnswers(body: String): List<InetAddress> = runCatching {
        DohJson.decodeFromString<DohResponse>(body).answer
            .filter { it.type == TYPE_A }
            // Данные приходят текстом: «1.2.3.4». InetAddress.getByName здесь не ходит в DNS —
            // строка уже является литеральным адресом.
            .mapNotNull { answer -> runCatching { InetAddress.getByName(answer.data) }.getOrNull() }
    }.getOrDefault(emptyList())

    private data class Entry(val addresses: List<InetAddress>, val expiresAt: Long)

    @Serializable
    private data class DohResponse(
        @SerialName("Answer") val answer: List<DohAnswer> = emptyList(),
    )

    @Serializable
    private data class DohAnswer(
        @SerialName("type") val type: Int = 0,
        @SerialName("data") val data: String = "",
    )

    companion object {
        /** Тип A: адрес IPv4. AAAA (28) отдельным запросом — когда понадобится IPv6. */
        const val TYPE_A: Int = 1

        private const val CACHE_MILLIS = 60_000L

        private val DohJson = Json { ignoreUnknownKeys = true }

        /** Публичные DoH-серверы: сначала по имени, затем по IP (не зависит от системного DNS). */
        val DEFAULT_ENDPOINTS: List<String> = listOf(
            "https://cloudflare-dns.com/dns-query",
            "https://1.1.1.1/dns-query",
            "https://dns.google/dns-query",
        )

        /** Клиент для DoH: свои короткие таймауты, чтобы каталог не ждал резолвер бесконечно. */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(DOH_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(DOH_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        private const val DOH_TIMEOUT_SECONDS = 5L
    }
}

/**
 * DNS для OkHttp: системный резолвер, а при его отказе — DoH.
 *
 * Система в приоритете: там кэш ОС, локальные имена и private DNS, если пользователь его включил.
 * DoH — только страховка, и только когда имя не разрешилось.
 */
class FallbackDns(
    private val system: Dns = Dns.SYSTEM,
    private val doh: HostResolver? = null,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val systemAddresses = runCatching { system.lookup(hostname) }.getOrNull()
        if (!systemAddresses.isNullOrEmpty()) {
            return systemAddresses
        }
        val dohAddresses = doh?.resolve(hostname).orEmpty()
        if (dohAddresses.isNotEmpty()) {
            return dohAddresses
        }
        throw UnknownHostException("Не удалось разрешить $hostname: ни системный DNS, ни DoH не ответили")
    }
}

/**
 * Адрес для повторной попытки по http — или `null`, если повторять нечем.
 *
 * Повтор только с https и только без учётных данных: отправлять логин и пароль каталога по
 * открытому каналу нельзя, а без них сервер сам ответит 401 — это честнее, чем утечка.
 */
internal fun plainRetryUrl(target: HttpUrl, hasCredentials: Boolean): HttpUrl? =
    target.takeIf { it.isHttps && !hasCredentials }
        ?.newBuilder()
        ?.scheme("http")
        ?.build()
