package io.github.bookshelf.core.update

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.update.source.GitHubReleaseJson
import org.junit.Test

/**
 * Разбор ответа GitHub Releases (ADR-018).
 *
 * Фикстура — реальный ответ `releases/latest` с нужными полями и лишними ключами, которых
 * в модели нет: API GitHub добавляет поля без предупреждения, и парсер не должен на них падать.
 */
class GitHubReleaseJsonTest {

    private val base = "https://github.com/mops1k/book-shelf/releases/download/v2.0.1"

    private val json = """
        {
          "tag_name": "v2.0.1",
          "name": "BookShelf 2.0.1",
          "body": "Исправлен возврат скрытых приложений",
          "html_url": "https://github.com/mops1k/book-shelf/releases/tag/v2.0.1",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "BookShelf-2.0.1-full.apk",
              "browser_download_url": "$base/BookShelf-2.0.1-full.apk"
            },
            {
              "name": "BookShelf-2.0.1-fdroid.apk",
              "browser_download_url": "$base/BookShelf-2.0.1-fdroid.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parses_release_with_assets() {
        val release = GitHubReleaseJson.parse(json)

        assertThat(release).isNotNull()
        assertThat(release!!.version.toString()).isEqualTo("2.0.1")
        assertThat(release.notes).contains("скрытых")
        assertThat(release.pageUrl).contains("releases/tag/v2.0.1")
        assertThat(release.assets).hasSize(2)
        assertThat(release.assets.map { it.name }).contains("BookShelf-2.0.1-full.apk")
    }

    @Test
    fun draft_release_is_not_an_update() {
        val draft = json.replace("\"draft\": false", "\"draft\": true")

        assertThat(GitHubReleaseJson.parse(draft)).isNull()
    }

    @Test
    fun broken_json_returns_null_instead_of_throwing() {
        assertThat(GitHubReleaseJson.parse("{oops")).isNull()
        assertThat(GitHubReleaseJson.parse("")).isNull()
    }

    @Test
    fun release_without_parseable_tag_is_ignored() {
        val bad = json.replace("\"v2.0.1\"", "\"nightly\"")

        assertThat(GitHubReleaseJson.parse(bad)).isNull()
    }
}
