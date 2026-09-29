package io.github.bookshelf.feature.catalogs

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.DownloadState
import io.github.bookshelf.core.data.model.OpdsDownload
import org.junit.Test

/**
 * Состояние загрузок в браузере каталога.
 *
 * Дефект, ради которого появились тесты (жалоба Александра, 28.09.2026): после «Скачать» карточка
 * молча закрывалась, и там, где пользователь стоял, не было ни признака начала загрузки, ни дороги
 * к очереди. Теперь состояние видно в карточке записи и в списке фида, а в шапке фида есть
 * «Загрузки (N)» со числом идущих загрузок.
 */
class CatalogsDownloadsTest {

    @Test
    fun `entry without downloads has no state`() {
        assertThat(entryDownloadOf(entryId = "entry-1", downloads = emptyList())).isNull()
        assertThat(
            entryDownloadOf(entryId = "entry-1", downloads = listOf(download(id = "d1", entryId = "other"))),
        ).isNull()
    }

    @Test
    fun `active download wins over the finished one of the same entry`() {
        // Повторное скачивание той же книги оставляет и завершённую строку: показывать надо то,
        // что происходит сейчас, иначе карточка писала бы «Скачано», пока файл качается.
        val downloads = listOf(
            download(id = "old", entryId = "entry-1", state = DownloadState.DONE),
            download(id = "new", entryId = "entry-1", state = DownloadState.RUNNING, progressPercent = 45),
        )

        val state = entryDownloadOf(entryId = "entry-1", downloads = downloads)

        assertThat(state?.label).isEqualTo("Загрузка идёт: 45%")
        assertThat(state?.isActive).isTrue()
        assertThat(state?.isDone).isFalse()
    }

    @Test
    fun `labels describe each download state`() {
        assertThat(label(DownloadState.QUEUED)).isEqualTo("Загрузка началась: в очереди")
        assertThat(label(DownloadState.RUNNING, progressPercent = 12)).isEqualTo("Загрузка идёт: 12%")
        assertThat(label(DownloadState.PAUSED, progressPercent = 12)).isEqualTo("Загрузка на паузе: 12%")
        assertThat(label(DownloadState.DONE)).isEqualTo("Скачано")
        assertThat(label(DownloadState.FAILED)).isEqualTo("Ошибка загрузки")
        assertThat(label(DownloadState.CANCELLED)).isEqualTo("Загрузка отменена")
    }

    @Test
    fun `feed button counts only the downloads that are running`() {
        assertThat(downloadsButtonLabel(emptyList())).isEqualTo("Загрузки")
        assertThat(
            downloadsButtonLabel(
                listOf(
                    download(id = "d1", entryId = "e1", state = DownloadState.RUNNING),
                    download(id = "d2", entryId = "e2", state = DownloadState.QUEUED),
                    download(id = "d3", entryId = "e3", state = DownloadState.DONE),
                ),
            ),
        ).isEqualTo("Загрузки (2)")
    }

    @Test
    fun `feed entries get the state of their own download`() {
        val entries = listOf(feedEntry("e1"), feedEntry("e2"))
        val downloads = listOf(download(id = "d1", entryId = "e1", state = DownloadState.DONE))

        val mapped = withDownloads(entries, downloads)

        assertThat(mapped.first { it.id == "e1" }.download?.label).isEqualTo("Скачано")
        assertThat(mapped.first { it.id == "e2" }.download).isNull()
    }

    @Test
    fun `download percent is counted from bytes and falls back to zero`() {
        // Размер отдаёт не каждый каталог: процент тогда честнее показать нулём, чем выдумать.
        assertThat(opdsDownload(state = DownloadState.RUNNING).toDownloadUi().progressPercent).isEqualTo(0)
        assertThat(
            opdsDownload(
                state = DownloadState.RUNNING,
                bytesDownloaded = 500,
                expectedSize = 1_000,
            ).toDownloadUi().progressPercent,
        ).isEqualTo(50)
        assertThat(opdsDownload(state = DownloadState.DONE).toDownloadUi().progressPercent).isEqualTo(100)
    }

    private fun opdsDownload(
        state: DownloadState,
        bytesDownloaded: Long = 0,
        expectedSize: Long? = null,
    ): OpdsDownload = OpdsDownload(
        id = "d1",
        catalogId = "catalog-1",
        entryId = "e1",
        title = "Солярис",
        downloadUrl = "https://host/1.fb2",
        state = state,
        bytesDownloaded = bytesDownloaded,
        expectedSize = expectedSize,
    )

    private fun label(state: DownloadState, progressPercent: Int = 0): String =
        entryDownloadLabel(download(id = "d1", entryId = "e1", state = state, progressPercent = progressPercent))

    private fun download(
        id: String,
        entryId: String,
        state: DownloadState = DownloadState.QUEUED,
        progressPercent: Int = 0,
    ): DownloadUi = DownloadUi(
        id = id,
        entryId = entryId,
        state = state,
        title = "Солярис",
        stateLabel = state.name,
        progressPercent = progressPercent,
        isActive = state == DownloadState.QUEUED ||
            state == DownloadState.RUNNING ||
            state == DownloadState.PAUSED,
    )

    private fun feedEntry(id: String): FeedEntryUi = FeedEntryUi(
        id = id,
        title = "Солярис",
        authors = "Станислав Лем",
    )
}
