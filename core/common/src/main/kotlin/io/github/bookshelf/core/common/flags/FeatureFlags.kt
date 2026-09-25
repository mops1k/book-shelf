package io.github.bookshelf.core.common.flags

/**
 * Возможности, которые зависят от **flavor-сборки**, а не от настроек пользователя.
 *
 * Зачем отдельный тип: `:core:*` и `:feature:*` не видят `BuildConfig` приложения, а различать
 * сборки через `if (flavor == …)` в бизнес-логике запрещено (docs/architecture.md §9).
 * Поэтому flavor-различия приходят одной зависимостью, а в тестах подменяются фейком —
 * то есть поведение «CBR выключен» можно проверить тестом, а не только вручную в другой сборке.
 *
 * Биндинг — в `:app` (`BuildConfigFeatureFlags`), потому что только там доступен `BuildConfig`.
 */
interface FeatureFlags {

    /**
     * Поддержка CBR. Включена только во flavor `full`: единственная библиотека для RAR
     * (`junrar`) распространяется под несвободной UnRar License, которую не принимает F-Droid
     * (ADR-007). В остальных сборках парсер CBR не должен предлагаться пользователю.
     */
    val cbrEnabled: Boolean

    /**
     * Доступ ко всем файлам (`MANAGE_EXTERNAL_STORAGE`). В `play`-flavor выключен: разрешение
     * требует Declaration Form (docs/architecture.md §4.1, §9).
     */
    val allowAllFilesAccess: Boolean

    /** OPDS. Выключен в офлайн-сборке (docs/opds.md). */
    val opdsEnabled: Boolean
}

/**
 * Значения по умолчанию для превью, тестов и модульных проверок: самая ограниченная сборка.
 *
 * В проде не используется — там работает биндинг из `:app`, который читает `BuildConfig`.
 */
object DefaultFeatureFlags : FeatureFlags {
    override val cbrEnabled: Boolean = false
    override val allowAllFilesAccess: Boolean = false
    override val opdsEnabled: Boolean = true
}
