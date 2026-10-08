package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Паритет переводов: у каждой строки из `values` есть перевод в `values-en`.
 *
 * Дефект, ради которого это проверяется (08.10.2026): экран «Читалки» переделали в короткое меню и
 * добавили ключи `settings_readers_defaults_*`, `settings_readers_progress_*` и
 * `settings_about_progress_*` только в русский файл. Английский остался без них, `lint` валил
 * сборку 33 ошибками `MissingTranslation` — то есть гейт был красным, и заметить это без
 * `lint` было нечем. Тест держит паритет в юнит-тестах модуля и сразу называет забытые ключи.
 *
 * Проверка файловая, а не через `R.string`: непереведённая строка отдаётся из `values`, и по
 * значению её от перевода не отличить (имена читалок совпадают в обоих языках намеренно).
 */
class StringsTranslationTest {

    @Test
    fun `every settings string has an english translation`() {
        val default = keysOf(resourceFile(DEFAULT_VALUES))
        val english = keysOf(resourceFile(ENGLISH_VALUES))

        val missing = default.filterNot { it in english }.sorted()

        assertThat(missing).isEmpty()
        // Строк в файле меньше, чем ключей, быть не может: значит, разбор файла не сломался.
        assertThat(default).isNotEmpty()
    }

    private fun keysOf(file: File): Set<String> =
        KEY.findAll(file.readText()).map { match -> match.groupValues[1] }.toSet()

    /**
     * Файл ресурсов от каталога запуска тестов вверх.
     *
     * Рабочий каталог тестовой задачи — каталог модуля, но закладываться на это не стоит: поиск
     * идёт по нескольким уровням вверх, а ненайденный файл — это ошибка теста, а не «переводов нет».
     */
    private fun resourceFile(relative: String): File {
        val start = System.getProperty("user.dir") ?: "."
        var dir: File? = File(start).absoluteFile
        repeat(SEARCH_UP_LEVELS) {
            val candidate = dir?.let { File(it, relative) }
            if (candidate?.isFile == true) return candidate
            dir = dir?.parentFile
        }
        error("Не найден файл ресурсов $relative (искал вверх от $start)")
    }

    private companion object {
        const val DEFAULT_VALUES = "src/main/res/values/strings.xml"
        const val ENGLISH_VALUES = "src/main/res/values-en/strings.xml"

        /** Сколько уровней вверх от рабочего каталога искать модуль. */
        const val SEARCH_UP_LEVELS = 4

        /** Ключи строк и множественных форм: переводить нужно и то, и другое. */
        val KEY = Regex("<(?:string|plurals)\\s+name=\"([^\"]+)\"")
    }
}
