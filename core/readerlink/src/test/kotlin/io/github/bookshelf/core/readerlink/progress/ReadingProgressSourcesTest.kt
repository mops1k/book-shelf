package io.github.bookshelf.core.readerlink.progress

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import org.junit.Test

/**
 * Реестр источников прогресса (`ReadingProgressSources`).
 *
 * Зачем реестр, а не один источник: читалок с доступным прогрессом уже несколько (KOReader, дальше
 * CoolReader, PocketBook, Librera, XReader), и у каждой он лежит по-своему. Реестр отвечает на
 * вопрос «кого спрашивать прямо сейчас», и здесь ошибиться дорого:
 * * если во время чтения спрашивать **все** читалки, мы читаем чужие базы и каталоги, пока человек
 *   читает, — на слабом e-ink это ровно та нагрузка, которая уже роняла читалку (ANR 30.09.2026);
 * * если спрашивать **не ту** читалку, прогресс книги не обновится, и на полке останется старый
 *   процент, хотя человек читал полчаса.
 *
 * Поэтому порядок обхода источников детерминированный (по `ProgressSourceId`), а не «как придёт
 * из DI»: иначе поведение зависело бы от порядка биндингов в Hilt, который меняется от сборки к
 * сборке.
 */
class ReadingProgressSourcesTest {

    @Test
    fun `without a session every source is asked`() {
        val sources = ReadingProgressSources(setOf(koReader, fbReader))

        assertThat(sources.forPass(activeSourceId = null).map { it.id })
            .containsExactly(ProgressSourceId.FBREADER_AIDL, ProgressSourceId.KOREADER_SIDECAR)
            .inOrder()
    }

    @Test
    fun `during reading only the reader that is open now is asked`() {
        val sources = ReadingProgressSources(setOf(koReader, fbReader))

        val asked = sources.forPass(activeSourceId = ProgressSourceId.KOREADER_SIDECAR)

        // Ровно один источник: пока читают в KOReader, состояние остальных читалок не разбираем.
        assertThat(asked.map { it.id }).containsExactly(ProgressSourceId.KOREADER_SIDECAR)
    }

    @Test
    fun `an open reader we have no source for asks nobody`() {
        val sources = ReadingProgressSources(setOf(koReader, fbReader))

        // Читалка открыта, но её источника у нас нет (например, открыли в Moon+ Reader): спрашивать
        // чужие читалки «на всякий случай» нельзя — это и есть нагрузка, от которой отказались.
        assertThat(sources.forPass(activeSourceId = ProgressSourceId.POCKETBOOK_DB)).isEmpty()
    }

    @Test
    fun `sources are ordered by id regardless of registration order`() {
        val one = ReadingProgressSources(setOf(koReader, fbReader))
        val another = ReadingProgressSources(setOf(fbReader, koReader))

        assertThat(one.all().map { it.id }).isEqualTo(another.all().map { it.id })
    }

    @Test
    fun `byId finds its source and is null for a missing one`() {
        val sources = ReadingProgressSources(setOf(koReader))

        assertThat(sources.byId(ProgressSourceId.KOREADER_SIDECAR)).isSameInstanceAs(koReader)
        assertThat(sources.byId(ProgressSourceId.FBREADER_AIDL)).isNull()
    }

    /**
     * Значения ключей — это строки в `reading_progress.source`, то есть данные, уже лежащие в базе
     * у пользователей. Менять их молча нельзя: полка и диагностика читают именно их.
     */
    @Test
    fun `storage keys of progress sources stay stable`() {
        assertThat(ProgressSourceId.FBREADER_AIDL.storageKey).isEqualTo("FBREADER_AIDL")
        assertThat(ProgressSourceId.KOREADER_SIDECAR.storageKey).isEqualTo("KOREADER_SIDECAR")
    }

    private class FakeSource(
        override val id: ProgressSourceId,
        override val knowsOpenBook: Boolean = true,
    ) : ReadingProgressSource {

        override suspend fun connect(): Boolean = true

        override suspend fun disconnect() = Unit

        override suspend fun openedBook(): ReaderBookAddress? = null

        override suspend fun progressFor(book: ProgressBookRef): AppResult<ReadingProgressSnapshot> =
            AppResult.failure(AppError.NotFound(what = "прогресс"))
    }

    private companion object {

        val fbReader = FakeSource(ProgressSourceId.FBREADER_AIDL)
        val koReader = FakeSource(ProgressSourceId.KOREADER_SIDECAR)
    }
}
