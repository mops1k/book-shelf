package io.github.bookshelf.core.update.source

import io.github.bookshelf.core.update.model.ReleaseInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Последний релиз с GitHub Releases (ADR-018).
 *
 * Единственный сетевой вызов проверки обновлений. Таймауты короткие: если сеть недоступна,
 * обновление не должно задерживать запуск приложения. Любая ошибка — `null`, без исключений
 * наружу: «не смогли проверить» для пользователя то же, что «обновлений нет», а падать из-за
 * отсутствия интернета приложение не должно.
 */
@Singleton
class GitHubReleaseSource @Inject constructor() : ReleaseSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            // Ошибку отдаём наверх: проверка различает «обновлений нет» и «не смогли проверить».
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            GitHubReleaseJson.parse(response.body?.string().orEmpty())
                ?: throw IOException("unparsable release")
        }
    }

    private companion object {
        const val LATEST_URL = "https://api.github.com/repos/mops1k/book-shelf/releases/latest"
        const val TIMEOUT_SECONDS = 10L
    }
}
