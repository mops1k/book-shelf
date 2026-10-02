package io.github.bookshelf.core.opds.client

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * HTTP-клиент каталогов на настоящем сервере [MockWebServer].
 *
 * Проверяются свойства, из-за которых клиент писался, а не «просто вернул тело»: условный
 * запрос, `finalUrl` после редиректа, отказ на не-2xx и отсутствие сетевого вызова для
 * некорректного адреса.
 */
class OkHttpOpdsClientTest {

    private lateinit var server: MockWebServer

    // DoH в этих проверках не участвует: адреса MockWebServer резолвит системный DNS.
    private val client = OkHttpOpdsClient(doh = HostResolver { emptyList() }, dispatchers = TestAppDispatchers())

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun returns_body_and_etag() = runTest {
        server.enqueue(MockResponse().setBody("<feed/>").setHeader("ETag", "v1"))

        val response = client.get(server.url("/opds").toString()).getOrNull()

        assertThat(response?.status).isEqualTo(200)
        assertThat(response?.body).isEqualTo("<feed/>")
        assertThat(response?.etag).isEqualTo("v1")
    }

    @Test
    fun conditional_get_is_not_modified() = runTest {
        server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "v1"))

        val result = client.get(
            server.url("/opds").toString(),
            ConditionalGet(etag = "v1", lastModified = null),
        ).getOrNull()

        // 304 — не ошибка: тело пустое, кэш остаётся годным.
        assertThat(result?.status).isEqualTo(304)
        assertThat(result?.body).isEmpty()
    }

    @Test
    fun final_url_reflects_redirects() = runTest {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/opds/feed"))
        server.enqueue(MockResponse().setBody("<feed/>"))

        val response = client.get(server.url("/opds").toString()).getOrNull()

        // По нему разрешаются относительные ссылки фида: по исходному адресу были бы битыми.
        assertThat(response?.finalUrl).endsWith("/opds/feed")
    }

    @Test
    fun server_error_is_a_failure() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        assertThat(client.get(server.url("/opds").toString()).isFailure).isTrue()
    }

    @Test
    fun malformed_url_is_a_failure_without_network_call() = runTest {
        assertThat(client.get("не адрес").isFailure).isTrue()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun sends_user_agent_and_if_none_match() = runTest {
        server.enqueue(MockResponse().setBody("<feed/>"))
        server.enqueue(MockResponse().setResponseCode(304))

        client.get(server.url("/opds").toString())
        client.get(server.url("/opds").toString(), ConditionalGet("v1", null))

        assertThat(server.takeRequest().getHeader("User-Agent")).isNotEmpty()
        assertThat(server.takeRequest().getHeader("If-None-Match")).isEqualTo("v1")
    }

    @Test
    fun unresolvable_catalog_names_the_host_and_the_dns_cause() = runTest {
        // .test — домен верхнего уровня, которого нет в DNS. Клиент обязан пройти через свой
        // резолвер (система + DoH) и сказать, что дело в имени, а не «сервер недоступен».
        val failing = OkHttpOpdsClient(
            doh = HostResolver { host -> emptyList() },
            dispatchers = TestAppDispatchers(),
        )

        val result = failing.get("https://blocked.test/opds")

        assertThat(result.isFailure).isTrue()
        assertThat((result as AppResult.Failure).error.message).contains("blocked.test")
        assertThat((result as AppResult.Failure).error.message).contains("DNS")
    }

    @Test
    fun catalog_resolves_through_doh_when_system_dns_fails() = runTest {
        server.enqueue(MockResponse().setBody("<feed/>"))
        val doh = HostResolver { host ->
            // Тот же приём, что и в бою: система имя не знает, DoH отдаёт адрес.
            java.net.InetAddress.getAllByName("127.0.0.1").toList()
        }
        val throughDoh = OkHttpOpdsClient(doh = doh, dispatchers = TestAppDispatchers())

        val result = throughDoh.get("http://blocked.test:${server.port}/opds")

        assertThat(result.isFailure).isFalse()
        assertThat(result.getOrNull()?.body).isEqualTo("<feed/>")
    }
}
