package io.github.bookshelf.core.update.model

/**
 * Вариант сборки для выбора APK обновления (ADR-018).
 *
 * `PLAY` не обновляется само: правила Google Play запрещают установку APK в обход магазина,
 * поэтому у него нет ни ассета, ни проверки.
 */
enum class AppFlavor(val assetSuffix: String?) {
    /** Полная сборка: всё, включая комиксы CBR. */
    FULL("full"),

    /** F-Droid: без CBR (лицензия распаковщика RAR не подходит для F-Droid). */
    FDROID("fdroid"),

    /** Google Play: обновления только через магазин. */
    PLAY(null),
}
