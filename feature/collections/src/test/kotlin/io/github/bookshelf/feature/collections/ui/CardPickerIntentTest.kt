package io.github.bookshelf.feature.collections.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.os.Process
import android.provider.DocumentsContract
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.removable.volumeTreeUri
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Системный выбор папки для полки съёмного носителя.
 *
 * Проверяется то, от чего зависит, попадёт ли человек сразу на карту: на API 29+ пикер открывается
 * платформенным интентом тома, найденного по UUID из адреса дерева; если том системе неизвестен
 * или система старая (26–28), выбор открывается на адресе дерева; на 24–25 начального адреса нет
 * вовсе — система откроет выбор там, где помнит.
 */
@RunWith(RobolectricTestRunner::class)
class CardPickerIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [35])
    fun `the picker opens on the volume with the platform helper`() {
        givenVolume(CARD_UUID)

        val intent = folderPickerIntent(context, volumeTreeUri(CARD_UUID))

        assertThat(intent.action).isEqualTo(Intent.ACTION_OPEN_DOCUMENT_TREE)
        // Платформенный помощник отдаёт КОРЕНЬ тома (`/root/1A2B-3C4D`), а не адрес дерева
        // (`/tree/1A2B-3C4D%3A`): система открывает выбор сразу на карте. Именно на этом ветка
        // помощника отличается от фолбэка — по одному UUID их не различить.
        assertThat(Uri.parse(initialUriOf(intent)).lastPathSegment).isEqualTo(CARD_UUID)
    }

    @Test
    @Config(sdk = [29])
    fun `a volume unknown to the system falls back to the tree address`() {
        val treeUri = volumeTreeUri(CARD_UUID)

        val intent = folderPickerIntent(context, treeUri)

        assertThat(intent.action).isEqualTo(Intent.ACTION_OPEN_DOCUMENT_TREE)
        // Тома в системе нет (Robolectric отдаёт пустой список) — адрес дерева всё равно нацеливает выбор.
        assertThat(initialUriOf(intent)).isEqualTo(treeUri)
    }

    @Test
    @Config(sdk = [24])
    fun `an old system has no initial address to offer`() {
        val intent = folderPickerIntent(context, volumeTreeUri(CARD_UUID))

        assertThat(intent.action).isEqualTo(Intent.ACTION_OPEN_DOCUMENT_TREE)
        // EXTRA_INITIAL_URI появился в API 26: на 24–25 система его не поймёт, и класть его нельзя.
        assertThat(initialUriOf(intent)).isNull()
    }

    private fun initialUriOf(intent: Intent): String? =
        IntentCompat.getParcelableExtra(intent, DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java)
            ?.toString()

    /**
     * Завести съёмный том в системе.
     *
     * `StorageVolume` открытым API не собирается: строитель помечен `@SystemApi` и в SDK-заглушках
     * его нет (в android-all он появляется только с API 30 — поэтому тест ветки помощника идёт на 35).
     * Берём строитель отражением: в Robolectric исполняется настоящий фреймворк, а проверяем мы своё
     * поведение, а не скрытый API.
     */
    private fun givenVolume(uuid: String) {
        val builderClass = Class.forName("android.os.storage.StorageVolume\$Builder")
        // Строитель берёт путь, подпись, владельца и состояние: без любого из них `StorageVolume`
        // не создаётся (проверки `Preconditions.checkNotNull`), а владелец берётся у процесса.
        val builder = builderClass
            .getConstructor(
                String::class.java,
                File::class.java,
                String::class.java,
                android.os.UserHandle::class.java,
                String::class.java,
            )
            .newInstance("card", File("/storage/$uuid"), DESCRIPTION, Process.myUserHandle(), "mounted")
        builderClass.getMethod("setUuid", String::class.java).invoke(builder, uuid)
        builderClass.getMethod("setRemovable", Boolean::class.java).invoke(builder, true)
        val volume = builderClass.getMethod("build").invoke(builder) as StorageVolume

        val manager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        shadowOf(manager).addStorageVolume(volume)
        // Проверка самого теста: без заведённого тома ветка «том найден» не выполнялась бы, и тест
        // зеленел бы на фолбэке, ничего не проверяя.
        assertThat(manager.storageVolumes.map { it.uuid }).contains(uuid)
    }

    private companion object {
        const val CARD_UUID = "1A2B-3C4D"
        const val DESCRIPTION = "SD-карта"
    }
}
