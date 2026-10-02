package io.github.bookshelf.core.eink.refresh

/**
 * Причина, по которой нужен полный перерисон («E-ink UX» §4).
 *
 * Копия перечисления из дизайн-системы сделана осознанно (`LOCAL`): `:core:eink` не должен зависеть
 * от Compose и `:core:designsystem`. Дизайн-система объявляет `LocalEinkRefresh`, а `:app` связывает
 * его с [EinkRefreshHost] — так UI-модули не знают про вендорские SDK.
 */
enum class RefreshReason {
    SCREEN_CHANGED,
    DIALOG_OPENED,
    DIALOG_CLOSED,
    PAGE_CHANGED,
    SCAN_STARTED,
    SCAN_FINISHED,
    FILTER_APPLIED,
    MANUAL,
}

/**
 * Точка приёма событий «нужен полный refresh» («Архитектура» §6).
 *
 * Реализация держит корневой контейнер экрана и вызывает
 * [io.github.bookshelf.core.eink.EinkController.fullRefresh] по событиям навигации и диалогов.
 * Как именно реагировать — решает контроллер: на устройстве без вендорской поддержки это
 * безвредный `invalidate()`, при выключенных в настройках событиях — no-op.
 *
 * Связывание с Compose: дизайн-система объявляет `LocalEinkRefresh`, `:app` подставляет в него
 * вызов [onReason]. Это единственное место, где UI-слой и e-ink-слой встречаются.
 */
interface EinkRefreshHost {
    fun onReason(reason: RefreshReason)
}
