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
    fun `titles and summaries are the approved ones`() {
        val pairs = defaultSettingsSections().map { section -> section.title to section.summary }

        assertThat(pairs).containsExactly(
            "Внешний вид" to "Тема, плотность, масштаб шрифта и инверсия обложек",
            "Экран (e-ink)" to "Режим обновления, события полного кадра, подсветка и клавиши",
            "Библиотека и сканирование" to
                "Вид и порядок полки, лимиты диска, папки-источники и разрешения",
            "Читалки" to "Установленные читалки и читалка по умолчанию",
            "Каталоги OPDS" to "Переход к каталогам: список, добавление по адресу, загрузки",
            "Хранилище и кэш" to "Очистка кэша обложек и экспорт библиотеки",
            "О приложении и диагностика" to "Версия, диагностика e-ink и системные разрешения",
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
