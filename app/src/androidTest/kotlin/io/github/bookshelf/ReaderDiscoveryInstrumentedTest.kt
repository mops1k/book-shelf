package io.github.bookshelf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.RequiresDevice
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.readerlink.discovery.AndroidReaderDiscovery
import io.github.bookshelf.core.readerlink.discovery.NonReaderApps
import io.github.bookshelf.core.readerlink.discovery.ReaderRank
import io.github.bookshelf.core.readerlink.discovery.readerRankOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Поиск читалок на живом устройстве: видно ВСЁ, что умеет открывать книги, а не список из кода.
 *
 * ДЕФЕКТ (Александр, 01.10.2026): «не видит других читалок, как будто он зашит в коде… должно
 * видеть и открывать в выбранной, к примеру в устройстве есть ещё XReader». Проверка идёт на
 * настоящем `PackageManager`: сравниваем найденное с тем, что система отдаёт по книжным MIME
 * напрямую, — тест не повторяет логику discovery, а сверяет её с системой.
 *
 * `@RequiresDevice`: на чистом эмуляторе не установлено ни одной читалки, ожидание пустеет, и
 * `containsAtLeastElementsIn(∅)` проходит всегда — такая зелень ничего не доказывает (проверено
 * 09.10.2026). Смысл у проверки появляется только на устройстве, где читалки есть.
 */
@RunWith(AndroidJUnit4::class)
@RequiresDevice
class ReaderDiscoveryInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun every_app_that_views_books_is_found() {
        val found = runBlocking { AndroidReaderDiscovery(context).installedReaders() }
            .map { reader -> reader.profile.packageName }
            .toSet()

        // Ожидание считаем сами: все приложения, которые система готова запустить на книжный тип.
        val expected = expectedReaders()
        assertThat(found).containsAtLeastElementsIn(expected)
        assertThat(found.size).isAtLeast(expected.size)
    }

    @Test
    fun a_dictionary_that_views_books_is_not_offered_as_a_reader() {
        val nonReaders = appsViewingBooks().filter(NonReaderApps::isKnownNonReader)
        val offered = runBlocking { AndroidReaderDiscovery(context).installedReaders() }
            .map { reader -> reader.profile.packageName }
            .toSet()

        // На устройстве без словарей ожидание пустое и проверка ничего не доказывает — это
        // допустимо: она ловит именно случай «система готова, приложение не должно».
        assertThat(offered).containsNoneIn(nonReaders)
    }

    @Test
    fun the_readers_are_ordered_before_other_apps() {
        val readers = runBlocking { AndroidReaderDiscovery(context).installedReaders() }

        val ranks = readers.map { reader ->
            readerRankOf(reader.profile.packageName, reader.profile.mimeTypes)
        }
        // Список не перемешан: сначала известные читалки, потом найденные по форматам, потом прочие.
        assertThat(ranks).isInOrder()
        assertThat(ranks.firstOrNull()).isNotEqualTo(ReaderRank.OTHER)
    }

    /**
     * Кто, по мнению системы, открывает книги.
     *
     * Минус известные не-читалки — тем же признаком, что и в приложении ([NonReaderApps]):
     * словарь `com.eusoft.eudic` объявляет `application/epub+zip` и отвечает на запрос о книге,
     * но читалкой не является, и приложение его не предлагает. Без этого вычитания тест краснел
     * на живом устройстве со словарём (02.10.2026), хотя поведение приложения было верным.
     */
    private fun expectedReaders(): Set<String> =
        appsViewingBooks().filterNot(NonReaderApps::isKnownNonReader).toSet()

    /**
     * Что система готова запустить на книжный тип.
     *
     * Список книжных MIME берём из самих профилей приложения (`context.packageName` исключаем):
     * так ожидание опирается на систему, а не на реализацию discovery.
     */
    private fun appsViewingBooks(): Set<String> {
        val packageManager = context.packageManager
        val probes = listOf(
            "application/epub+zip",
            "application/fb2",
            "application/x-mobipocket-ebook",
            "application/vnd.comicbook+zip",
        )
        return probes.flatMap { mime ->
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                addCategory(android.content.Intent.CATEGORY_DEFAULT)
                setDataAndType(
                    android.net.Uri.Builder()
                        .scheme("content")
                        .authority("io.github.bookshelf.probe")
                        .appendPath("probe.$mime")
                        .build(),
                    mime,
                )
            }
            packageManager.queryIntentActivities(intent, 0)
                .map { info -> info.activityInfo.packageName }
                .filter { name -> name != context.packageName }
        }.toSet()
    }
}
