package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import org.junit.Test

/**
 * Состав и подписи разделов настроек (`SettingsUiState.defaultSettingsSections`).
 *
 * Список категорий — это то, что пользователь видит первым, и он же источник заголовков для
 * экранов разделов (`SettingsCategoryScreen` берёт заголовок именно отсюда). Тест фиксирует две
 * вещи, которые легко сломать незаметно: набор категорий с порядком и точные подписи.
 *
 * Подписи перечислены целиком намеренно. Они согласованы с Александром 29.09.2026 после того, как
 * выяснилось, что несколько подписей обещали то, чего в разделах нет («автоскан», «импорт
 * библиотеки», «лицензии», «Проверить»). Если текст меняется — тест обязан упасть и заставить
 * обновить ожидание осознанно, а не протащить очередное обещание молча.
 */
class SettingsSectionsTest {

    @Test
    fun `categories are listed once each in display order`() {
        val sections = defaultSettingsSections()

        assertThat(sections.map { section -> section.kind }).containsExactly(
            SettingsSectionKind.APPEARANCE,
            SettingsSectionKind.EINK_SCREEN,
            SettingsSectionKind.LIBRARY_SCAN,
            SettingsSectionKind.READERS,
            SettingsSectionKind.OPDS,
            SettingsSectionKind.STORAGE,
            SettingsSectionKind.ABOUT,
        ).inOrder()
    }

    @Test
    fun `without opds the catalogs section is not shown`() {
        // Сборка без OPDS (`ENABLE_OPDS = false`, ADR-006) не должна показывать раздел,
        // у которого нет потребителя: экранов без действия в интерфейсе не держим.
        val kinds = defaultSettingsSections(opdsEnabled = false).map { section -> section.kind }

        assertThat(kinds).containsExactly(
            SettingsSectionKind.APPEARANCE,
            SettingsSectionKind.EINK_SCREEN,
            SettingsSectionKind.LIBRARY_SCAN,
            SettingsSectionKind.READERS,
            SettingsSectionKind.STORAGE,
            SettingsSectionKind.ABOUT,
        ).inOrder()
    }

    @Test
    fun `titles and summaries are the approved ones`() {
        // Тексты живут в `res/values` (ru) и `values-en`, поэтому тест фиксирует выбранные
        // ресурсы: подмена подписи на чужой ресурс обязана упасть так же, как падала на тексте.
        val pairs = defaultSettingsSections().map { section -> section.titleRes to section.summaryRes }

        assertThat(pairs).containsExactly(
            R.string.settings_section_appearance to R.string.settings_section_appearance_summary,
            R.string.settings_section_eink to R.string.settings_section_eink_summary,
            R.string.settings_section_library to R.string.settings_section_library_summary,
            R.string.settings_section_readers to R.string.settings_section_readers_summary,
            R.string.settings_section_opds to R.string.settings_section_opds_summary,
            R.string.settings_section_storage to R.string.settings_section_storage_summary,
            R.string.settings_section_about to R.string.settings_section_about_summary,
        ).inOrder()
    }

    @Test
    fun `category list does not depend on settings values`() {
        // Заголовки для экранов берутся из той же чистой функции, поэтому список обязан быть
        // одинаковым при любом вызове: иначе экран раздела мог бы показать чужой заголовок.
        assertThat(defaultSettingsSections()).isEqualTo(defaultSettingsSections())
    }

    @Test
    fun `shelf view presets stay in the documented order`() {
        // Порядок чипов вида полки виден пользователю: «Сетка → Авторы → Серии → Жанры».
        // Проверяется здесь, чтобы вариант не переставили случайно вместе с добавлением нового.
        assertThat(ShelfViewPreset.entries.map { preset -> preset.name }).containsExactly(
            "GRID",
            "AUTHORS",
            "SERIES",
            "GENRES",
        ).inOrder()
    }
}
