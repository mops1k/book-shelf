package io.github.bookshelf.core.data.sync

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.datastore.model.ReaderSettings
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import org.junit.Test

/**
 * Какие источники прогресса включены (настройки → набор источников).
 *
 * Проверяется связь настроек с обходом: выключенный источник нельзя спрашивать — это и обещание
 * пользователю («интеграция выключена»), и расход: у каждой читалки чтение состояния стоит открытия
 * чужих файлов, а часть из них требует доступа ко всей памяти.
 *
 * Значение по умолчанию у новых интеграций — выключено: прогресс — это чтение чужих данных, и
 * включать его без ведома человека нельзя (решение по FBReader, 29.09.2026).
 */
class EnabledProgressSourcesTest {

    @Test
    fun `nothing is enabled by default`() {
        assertThat(ReaderSettings().enabledProgressSources()).isEmpty()
    }

    @Test
    fun `the fbReader switch enables the aidl source`() {
        val settings = ReaderSettings(enableFbReaderProgress = true)

        assertThat(settings.enabledProgressSources()).containsExactly(ProgressSourceId.FBREADER_AIDL)
    }

    @Test
    fun `the koReader switch enables the sidecar source`() {
        val settings = ReaderSettings(enableKoReaderProgress = true)

        assertThat(settings.enabledProgressSources()).containsExactly(ProgressSourceId.KOREADER_SIDECAR)
    }

    @Test
    fun `the coolReader switch enables its database source`() {
        val settings = ReaderSettings(enableCoolReaderProgress = true)

        // CoolReader живёт в своей базе на общей памяти: отдельный флаг, потому что и условия
        // доступа у него другие (нужен доступ ко всей памяти).
        assertThat(settings.enabledProgressSources()).containsExactly(ProgressSourceId.COOLREADER_DB)
    }

    @Test
    fun `every switched-on reader gives its source in a stable order`() {
        val settings = ReaderSettings(
            enableFbReaderProgress = true,
            enableKoReaderProgress = true,
            enableCoolReaderProgress = true,
        )

        assertThat(settings.enabledProgressSources().toList()).containsExactly(
            ProgressSourceId.FBREADER_AIDL,
            ProgressSourceId.KOREADER_SIDECAR,
            ProgressSourceId.COOLREADER_DB,
        ).inOrder()
    }

    @Test
    fun `both switches give both sources in a stable order`() {
        val settings = ReaderSettings(enableFbReaderProgress = true, enableKoReaderProgress = true)

        // Порядок задаёт ProgressSourceId, а не порядок полей в настройках: иначе журнал и
        // диагностика зависели бы от того, как записаны флаги.
        assertThat(settings.enabledProgressSources().toList())
            .containsExactly(ProgressSourceId.FBREADER_AIDL, ProgressSourceId.KOREADER_SIDECAR)
            .inOrder()
    }

    @Test
    fun `the background chain runs while at least one source is on`() {
        // Цепочка фоновых работ ставится по этому признаку: выключенный FBReader при включённом
        // KOReader не должен останавливать синхронизацию — прогресс читается из sidecar.
        assertThat(ReaderSettings(enableKoReaderProgress = true).isProgressSyncEnabled()).isTrue()
        assertThat(ReaderSettings(enableFbReaderProgress = true).isProgressSyncEnabled()).isTrue()
    }

    @Test
    fun `the background chain is off when every source is off`() {
        assertThat(ReaderSettings().isProgressSyncEnabled()).isFalse()
    }
}
