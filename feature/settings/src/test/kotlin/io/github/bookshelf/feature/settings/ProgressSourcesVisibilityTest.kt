package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import org.junit.Test

/**
 * Какие переключатели «Прогресс из …» показывает подэкран.
 *
 * Требование Александра (06.10.2026): «если читалка удалена с устройства, её в приложении нигде не
 * должно быть видно». Наблюдалось на устройстве: после удаления читалок их переключатели оставались на
 * месте, и настройка выглядела как рабочая, хотя читать прогресс было некому.
 */
class ProgressSourcesVisibilityTest {

    @Test
    fun `only sources of installed readers are shown`() {
        val state = stateWith(COOLREADER)

        assertThat(visibleProgressSources(state)).containsExactly(ProgressSourceId.COOLREADER_DB)
    }

    @Test
    fun `the order is the order of the sources`() {
        // Порядок объявления источников: FBReader → KOReader → CoolReader → … — тот же, что был у
        // переключателей, чтобы настройка не перескакивала при установке читалки.
        val state = stateWith(COOLREADER, FBREADER, XREADER)

        assertThat(visibleProgressSources(state)).containsExactly(
            ProgressSourceId.FBREADER_AIDL,
            ProgressSourceId.COOLREADER_DB,
            ProgressSourceId.XREADER_CACHE,
        ).inOrder()
    }

    @Test
    fun `a reader that is not installed hides its source`() {
        val state = stateWith(COOLREADER)

        assertThat(visibleProgressSources(state))
            .doesNotContain(ProgressSourceId.FBREADER_AIDL)
        assertThat(visibleProgressSources(state))
            .doesNotContain(ProgressSourceId.KOREADER_SIDECAR)
    }

    @Test
    fun `without readers there is nothing to show`() {
        assertThat(visibleProgressSources(stateWith())).isEmpty()
    }

    private fun stateWith(vararg packages: String) = SettingsUiState(
        installedReaders = packages.map { packageName ->
            ReaderItemUi(id = packageName, name = packageName, packageName = packageName)
        },
    )
}

private const val FBREADER = "com.fbreader"
private const val COOLREADER = "org.coolreader"
private const val XREADER = "com.xrz.xreaderV3"
