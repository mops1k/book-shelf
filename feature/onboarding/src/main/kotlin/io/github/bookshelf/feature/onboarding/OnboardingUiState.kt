package io.github.bookshelf.feature.onboarding


import io.github.bookshelf.core.designsystem.text.AppText
/**
 * Шаги онбординга («E-ink UX» §6.1).
 *
 * Порядок шагов — часть контракта экрана, поэтому переходы описаны чистыми функциями
 * в [OnboardingSteps.kt]: их проверяет JUnit-тест без Compose, Hilt и устройства.
 */
enum class OnboardingStep {
    /** Приветствие и объяснение «приложение само книги не читает». */
    WELCOME,

    /** Выбор папок с книгами через SAF. */
    SOURCES,

    /** Выбор читалки по умолчанию для форматов. */
    READERS,
    ;

    /** Первый шаг: кнопки «Назад» и аппаратный BACK на нём не нужны. */
    val isFirst: Boolean get() = ordinal == 0

    /** Последний шаг: вместо «Далее» показывается «Собрать библиотеку». */
    val isLast: Boolean get() = ordinal == entries.lastIndex
}

/**
 * Строка списка выбранных папок на шаге «Папки с книгами».
 *
 * UI-модель, а не доменная сущность: экран не должен знать про `sources` из Room.
 */
data class SourceListItem(
    /** Идентификатор источника (`sources.id`). */
    val id: String,
    /** Отображаемое имя папки, например «Books». */
    val displayName: String,
    /** `content://`-URI дерева SAF. */
    val uri: String,
    /** Число найденных документов, если скан уже был. */
    val documentCount: Int? = null,
    /** true — persistable-разрешение потеряно: папку переместили или удалили. */
    val accessLost: Boolean = false,
)

/** Строка списка найденных в системе читалок на шаге «Читалка по умолчанию». */
data class ReaderListItem(
    /** Идентификатор профиля читалки (`reader_profiles.id`). */
    val id: String,
    /** Название приложения, как его показывает `PackageManager`. */
    val name: String,
    /** Имя пакета — по нему строится `Intent`. */
    val packageName: String,
    /** Форматы, которые эта читалка декларирует. */
    val formats: List<String> = emptyList(),
    /** true — читалка выбрана по умолчанию хотя бы для одного формата. */
    val isDefault: Boolean = false,
)

/**
 * Состояние экрана онбординга.
 *
 * На этапе каркаса заполнено только значение по умолчанию: запросов к репозиториям нет.
 * После Sprint 2 поля [sources] и [readers] наполняются из `SourceRepository` и
 * `ReaderRepository`, а [canFinish] считается функцией [canFinishOnboarding].
 */
data class OnboardingUiState(
    /** Текущий шаг мастера. */
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** Уже выбранные папки с книгами. */
    val sources: List<SourceListItem> = emptyList(),
    /** Установленные в системе читалки. */
    val readers: List<ReaderListItem> = emptyList(),
    /** Можно ли завершить мастер: нужна хотя бы одна папка. */
    val canFinish: Boolean = false,
    /** Идёт операция (запрос SAF, проверка читалки) — кнопки блокируются. */
    val isBusy: Boolean = false,
    /** Текст ошибки для инвертированной плашки (`EinkErrorBanner`). */
    val error: AppText? = null,
) {
    /**
     * Текст баннера ошибки для экрана.
     *
     * Отдельным свойством, а не чтением [error] прямо в Composable: так у связи «отказ в состоянии →
     * баннер на экране» есть имя и тест, а не только надежда, что экран не забудет это поле
     * (дефект 01.10.2026: отказы онбординга не показывались вовсе).
     */
    val errorBanner: AppText? get() = error
}
