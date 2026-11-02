package io.github.bookshelf.core.update.source

import io.github.bookshelf.core.update.model.AppFlavor
import io.github.bookshelf.core.update.model.ReleaseAsset

/**
 * Какой APK из релиза скачивать (ADR-018).
 *
 * Правило одно: имя файла заканчивается на `-<flavor>.apk`. Иначе на F-Droid-сборку прилетит APK
 * с несвободным распаковщиком RAR.
 */
object AssetPicker {

    /** APK для flavor или `null`, если такого ассета нет (и для `PLAY` всегда `null`). */
    fun pick(assets: List<ReleaseAsset>, flavor: AppFlavor): ReleaseAsset? {
        val suffix = flavor.assetSuffix ?: return null
        return assets.firstOrNull { asset -> asset.name.endsWith("-$suffix.apk") }
    }
}
