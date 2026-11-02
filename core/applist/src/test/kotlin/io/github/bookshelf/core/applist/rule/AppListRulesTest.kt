package io.github.bookshelf.core.applist.rule

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.applist.model.InstalledApp
import org.junit.Test

/**
 * Правила списка приложений: отбор, поиск и порядок (ADR-015).
 *
 * Тесты чистые: `PackageManager` и Android здесь не участвуют, поэтому проверяются все ветки —
 * исключение себя, скрытые, фильтр системных, регистронезависимый поиск, избранное сверху
 * и частота запуска с равенством по имени.
 */
class AppListRulesTest {

    private fun app(
        packageName: String,
        label: String,
        isSystem: Boolean = false,
        versionCode: Long = 1,
    ) = InstalledApp(
        packageName = packageName,
        activityName = "$packageName.Main",
        label = label,
        versionCode = versionCode,
        isSystem = isSystem,
    )

    private val own = app("io.github.bookshelf.debug", "BookShelf")
    private val azbuka = app("ru.azbuka", "Азбука")
    private val bukva = app("ru.bukva", "Буква")
    private val yabloko = app("ru.yabloko", "Яблоко")
    private val settings = app("com.android.settings", "Настройки", isSystem = true)
    private val vendorJunk = app("com.test.logcollect", "Отчет о регистрации", isSystem = true)
    private val hiddenApp = app("ru.hidden", "Скрытое")
    private val all = listOf(own, azbuka, bukva, yabloko, settings, hiddenApp)

    @Test
    fun visible_excludes_own_app_and_hidden_packages() {
        val visible = AppListRules.visible(
            apps = all,
            hiddenPackages = setOf(hiddenApp.packageName),
            showSystem = false,
            ownPackage = own.packageName,
        )

        assertThat(visible.map { it.packageName }).containsExactly(
            azbuka.packageName,
            bukva.packageName,
            yabloko.packageName,
        )
    }

    @Test
    fun visible_excludes_release_copy_of_own_app_next_to_debug_build() {
        // На устройстве могут стоять обе сборки: отладочная и релизная. Обе — «Книжная полка»,
        // и обе не должны показываться в собственном списке приложений.
        val release = app("io.github.bookshelf", "Книжная полка")

        val visible = AppListRules.visible(
            apps = listOf(own, release, azbuka),
            hiddenPackages = emptySet(),
            showSystem = false,
            ownPackage = own.packageName,
        )

        assertThat(visible.map { it.packageName }).containsExactly(azbuka.packageName)
    }

    @Test
    fun visible_shows_only_whitelisted_system_apps_when_asked() {
        val withSystem = AppListRules.visible(
            apps = all + vendorJunk,
            hiddenPackages = emptySet(),
            showSystem = true,
            ownPackage = own.packageName,
        )

        // Полезное системное — показываем, служебную запись вендора — нет даже с галочкой.
        assertThat(withSystem).contains(settings)
        assertThat(withSystem).doesNotContain(vendorJunk)
    }

    @Test
    fun search_matches_substring_ignoring_case() {
        // Запрос «Букв», а не «бук»: «Азбука» тоже содержит «бук», и тест ловил бы не то.
        val found = AppListRules.search(listOf(azbuka, bukva, yabloko), "БУКВ")

        assertThat(found).containsExactly(bukva)
    }

    @Test
    fun search_returns_everything_for_blank_query() {
        val apps = listOf(azbuka, bukva)

        assertThat(AppListRules.search(apps, "  ")).isEqualTo(apps)
    }

    @Test
    fun search_returns_empty_list_when_nothing_matches() {
        assertThat(AppListRules.search(listOf(azbuka, bukva), "нет такого")).isEmpty()
    }

    @Test
    fun by_name_sorts_alphabetically_and_pins_favorites() {
        val sorted = AppListRules.byName(
            apps = listOf(yabloko, bukva, azbuka),
            favorites = setOf(yabloko.packageName),
        )

        assertThat(sorted.map { it.label }).containsExactly("Яблоко", "Азбука", "Буква").inOrder()
    }

    @Test
    fun by_launch_frequency_puts_frequent_first_and_breaks_ties_by_name() {
        val sorted = AppListRules.byLaunchFrequency(
            apps = listOf(azbuka, bukva, yabloko),
            launchCounts = mapOf(bukva.packageName to 5, azbuka.packageName to 5),
        )

        // У «Азбуки» и «Буквы» по пять запусков, «Яблоко» не запускали ни разу:
        // при равенстве решает алфавит, а не порядок в исходном списке.
        assertThat(sorted.map { it.label }).containsExactly("Азбука", "Буква", "Яблоко").inOrder()
    }

    @Test
    fun by_launch_frequency_keeps_favorites_on_top() {
        val sorted = AppListRules.byLaunchFrequency(
            apps = listOf(bukva, azbuka, yabloko),
            launchCounts = mapOf(bukva.packageName to 9),
            favorites = setOf(yabloko.packageName),
        )

        assertThat(sorted.map { it.label }).containsExactly("Яблоко", "Буква", "Азбука").inOrder()
    }

    @Test
    fun by_label_uses_russian_alphabet_not_char_codes() {
        val apps = listOf(app("ru.ya", "Яблоко"), app("ru.a", "Азбука"), app("ru.b", "Буква"))

        val sorted = apps.sortedWith(AppListRules.byLabel)

        assertThat(sorted.map { it.label }).containsExactly("Азбука", "Буква", "Яблоко").inOrder()
    }
}
