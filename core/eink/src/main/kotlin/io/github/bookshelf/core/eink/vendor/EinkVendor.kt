package io.github.bookshelf.core.eink.vendor

/**
 * Вендор e-ink устройства («E-ink UX» §8).
 *
 * Список закрытый: по вендору экран «О приложении» называет производителя, а больше приложение
 * о железе ничего не утверждает — режимом обновления и подсветкой управляют системные утилиты
 * устройства (ADR-017).
 *
 * [displayName] — имя производителя для экрана «О приложении» («Onyx Boox», «Bigme»). Это данные,
 * а не текст интерфейса: названия брендов не переводятся. Неизвестный вендор даёт `null`, а слова
 * «неизвестное устройство» выбирает экран приложения из своих ресурсов — раньше строка приходила
 * отсюда готовой и оставалась русской в английском интерфейсе («E-ink UX» §8.5).
 */
enum class EinkVendor(val displayName: String?) {
    ONYX("Onyx Boox"),
    ROCKCHIP_BOYUE("Boyue / Likebook / Meebook"),
    HISENSE("Hisense"),
    TOLINO("Tolino"),
    BIGME("Bigme"),
    DASUNG("Dasung"),
    POCKETBOOK("PocketBook"),
    IREADER("iReader"),
    INKPALM("InkPalm"),
    UNKNOWN(null),
}
