package io.github.bookshelf.core.opds.client

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Обход блокировки DNS: системный резолвер, затем DoH («OPDS»).
 *
 * Дефект, ради которого это появилось (найден на Bigme B6 28.09.2026): провайдер не резолвит имена
 * библиотечных зеркал (`flibusta.is` → «unknown host», при этом сеть работает), а менять системные
 * настройки пользователя приложение не вправе. Проверяется цепочка «система → DoH», порядок
 * серверов, кэш и то, что учётные данные не уходят по открытому каналу.
 */
class DohDnsTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `system resolver wins and doh is not asked`() {
        val doh = RecordingResolver(addresses = listOf(address("9.9.9.9")))
        val dns = FallbackDns(system = FixedDns(addresses = listOf(address("10.0.0.1"))), doh = doh)

        val result = dns.lookup("flibusta.is")

        assertThat(result.map { it.hostAddress }).containsExactly("10.0.0.1")
        assertThat(doh.calls).isEmpty()
    }

    @Test
    fun `doh is used when the system resolver fails`() {
        val doh = RecordingResolver(addresses = listOf(address("179.43.150.83")))
        val dns = FallbackDns(system = FailingDns, doh = doh)

        val result = dns.lookup("flibusta.is")

        assertThat(result.map { it.hostAddress }).containsExactly("179.43.150.83")
        assertThat(doh.calls).containsExactly("flibusta.is")
    }

    @Test
    fun `unknown host is reported when both resolvers fail`() {
        val dns = FallbackDns(system = FailingDns, doh = RecordingResolver(addresses = emptyList()))

        val error = runCatching { dns.lookup("flibusta.is") }.exceptionOrNull()

        assertThat(error).isInstanceOf(UnknownHostException::class.java)
        assertThat(error).hasMessageThat().contains("flibusta.is")
    }

    @Test
    fun `doh without a resolver still throws instead of returning empty`() {
        val dns = FallbackDns(system = FailingDns, doh = null)

        assertThat(runCatching { dns.lookup("flibusta.is") }.isFailure).isTrue()
    }

    @Test
    fun `doh parses answer and caches it`() {
        server.enqueue(jsonResponse(ANSWERS_TWO_ADDRESSES))
        val resolver = DohResolver(
            http = DohResolver.defaultHttpClient(),
            endpoints = listOf(server.url("/dns-query").toString()),
        )

        val first = resolver.resolve("flibusta.is")
        val second = resolver.resolve("flibusta.is")

        assertThat(first.map { it.hostAddress }).containsExactly("179.43.150.83", "185.130.107.45")
        assertThat(second.map { it.hostAddress }).containsExactly("179.43.150.83", "185.130.107.45")
        // Второй раз адрес берётся из кэша: открытие каталога не должно ждать DNS на каждый запрос.
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `doh tries the next endpoint when the first one fails`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(jsonResponse(ANSWERS_ONE_ADDRESS))
        val endpoint = server.url("/dns-query").toString()
        val resolver = DohResolver(
            http = DohResolver.defaultHttpClient(),
            endpoints = listOf(endpoint, endpoint),
        )

        val result = resolver.resolve("flibusta.is")

        assertThat(result.map { it.hostAddress }).containsExactly("179.43.150.83")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `doh survives a broken body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("это не JSON"))
        val resolver = DohResolver(
            http = DohResolver.defaultHttpClient(),
            endpoints = listOf(server.url("/dns-query").toString()),
        )

        assertThat(resolver.resolve("flibusta.is")).isEmpty()
    }

    @Test
    fun `request asks for an A record with the json content type`() {
        server.enqueue(jsonResponse(ANSWERS_ONE_ADDRESS))
        val resolver = DohResolver(
            http = DohResolver.defaultHttpClient(),
            endpoints = listOf(server.url("/dns-query").toString()),
        )

        resolver.resolve("flibusta.site")

        val recorded = server.takeRequest()
        assertThat(recorded.path).contains("name=flibusta.site")
        assertThat(recorded.path).contains("type=1")
        assertThat(recorded.getHeader("Accept")).isEqualTo("application/dns-json")
    }

    @Test
    fun `default endpoints include a bootstrap by ip`() {
        // Если системный DNS не резолвит даже имя DoH-провайдера, спасает адрес по IP.
        assertThat(DohResolver.DEFAULT_ENDPOINTS).contains("https://1.1.1.1/dns-query")
    }

    @Test
    fun `plain retry switches the scheme but never with credentials`() {
        val https = "https://flibusta.is/opds".toHttpUrl()

        assertThat(plainRetryUrl(https, hasCredentials = false)?.toString()).isEqualTo("http://flibusta.is/opds")
        // Пароль каталога по открытому каналу не отправляется — лучше 401, чем утечка.
        assertThat(plainRetryUrl(https, hasCredentials = true)).isNull()
        assertThat(plainRetryUrl("http://flibusta.is/opds".toHttpUrl(), hasCredentials = false)).isNull()
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(body).addHeader("Content-Type", "application/dns-json")

    private fun address(ip: String): InetAddress = InetAddress.getByName(ip)

    private class FixedDns(private val addresses: List<InetAddress>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> = addresses
    }

    private object FailingDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException(hostname)
    }

    private class RecordingResolver(private val addresses: List<InetAddress>) : HostResolver {
        val calls = mutableListOf<String>()

        override fun resolve(host: String): List<InetAddress> {
            calls += host
            return addresses
        }
    }

    private companion object {
        const val ANSWERS_ONE_ADDRESS = """
            {"Status":0,"Answer":[{"name":"flibusta.is.","type":1,"TTL":300,"data":"179.43.150.83"}]}
        """

        const val ANSWERS_TWO_ADDRESSES = """
            {"Status":0,"Answer":[
              {"name":"flibusta.is.","type":1,"TTL":300,"data":"179.43.150.83"},
              {"name":"flibusta.is.","type":1,"TTL":300,"data":"185.130.107.45"}
            ]}
        """
    }
}
