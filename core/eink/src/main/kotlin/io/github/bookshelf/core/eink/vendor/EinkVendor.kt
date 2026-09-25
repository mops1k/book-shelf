package io.github.bookshelf.core.eink.vendor

/**
 * Вендор e-ink устройства (docs/eink-ux.md §8).
 *
 * Список закрытый: от вендора зависит, какой контроллер обновления экрана доступен
 * ([io.github.bookshelf.core.eink.onyx.OnyxEinkController] или
 * [io.github.bookshelf.core.eink.reflection.RockchipEinkController]) и можно ли управлять подсветкой.
 *
 * [displayName] — человекочитаемая строка для экрана «О приложении»: «Устройство: Onyx Boox Nova Air,
 * e-ink: да, быстрый режим: поддерживается (Onyx SDK)» (docs/eink-ux.md §8.5).
 */
enum class EinkVendor(val displayName: String) {
    ONYX("Onyx Boox"),
    ROCKCHIP_BOYUE("Boyue / Likebook / Meebook"),
    HISENSE("Hisense"),
    TOLINO("Tolino"),
    BIGME("Bigme"),
    DASUNG("Dasung"),
    POCKETBOOK("PocketBook"),
    IREADER("iReader"),
    INKPALM("InkPalm"),
    UNKNOWN("Неизвестное устройство"),
}
