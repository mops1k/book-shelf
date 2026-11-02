package io.github.bookshelf.core.update

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.update.model.AppFlavor
import io.github.bookshelf.core.update.model.ReleaseAsset
import io.github.bookshelf.core.update.source.AssetPicker
import org.junit.Test

/**
 * Выбор APK из ассетов релиза (ADR-018).
 *
 * У релиза два APK: `-full` (с CBR) и `-fdroid` (без него). Скачивать «первый попавшийся» нельзя:
 * на F-Droid-сборку встанет APK с несвободным распаковщиком.
 */
class AssetPickerTest {

    private val assets = listOf(
        ReleaseAsset("BookShelf-2.0.1-full.apk", "https://example.org/full.apk"),
        ReleaseAsset("BookShelf-2.0.1-fdroid.apk", "https://example.org/fdroid.apk"),
    )

    @Test
    fun full_flavor_gets_full_asset() {
        assertThat(AssetPicker.pick(assets, AppFlavor.FULL)?.name).isEqualTo("BookShelf-2.0.1-full.apk")
    }

    @Test
    fun fdroid_flavor_gets_fdroid_asset() {
        assertThat(AssetPicker.pick(assets, AppFlavor.FDROID)?.name).isEqualTo("BookShelf-2.0.1-fdroid.apk")
    }

    @Test
    fun play_flavor_never_gets_an_asset() {
        // В Google Play самообновление запрещено: APK с GitHub там не нужен (ADR-018).
        assertThat(AssetPicker.pick(assets, AppFlavor.PLAY)).isNull()
    }

    @Test
    fun missing_asset_gives_null() {
        assertThat(AssetPicker.pick(emptyList(), AppFlavor.FULL)).isNull()
    }
}
