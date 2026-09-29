package io.github.bookshelf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.opds.client.FallbackDns
import io.github.bookshelf.core.opds.client.HostResolver
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Dns
import okhttp3.OkHttpClient

/**
 * HTTP-клиент для обложек OPDS.
 *
 * Отдельный от каталога клиент нужен только потому, что `OkHttpOpdsClient` держит свой приватно.
 * Настройки при этом те же по смыслу: обложки лежат на тех же хостах, что и фид, поэтому
 * разрешение имён идёт через тот же [HostResolver] (системный DNS, при отказе — DoH). Без этого
 * обложки не загружались бы именно там, где каталог открывается через DoH, — а это и есть наш
 * основной сценарий с Флибустой.
 *
 * Таймауты короче каталожных: обложка — не то, ради чего стоит держать экран в ожидании, а
 * `BookCover` рисует заглушку, пока её нет.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoverHttpModule {

    @Provides
    @Singleton
    fun provideCoverHttpClient(hostResolver: HostResolver): OkHttpClient = OkHttpClient.Builder()
        .dns(FallbackDns(system = Dns.SYSTEM, doh = hostResolver))
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 20L
}
