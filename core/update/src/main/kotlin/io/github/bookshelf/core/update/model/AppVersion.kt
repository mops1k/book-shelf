package io.github.bookshelf.core.update.model

/**
 * Версия приложения из тега релиза (ADR-018): `v2.0.1` или `2.0.1`.
 *
 * Сравнение по разрядам major/minor/patch. Суффикс сборки (`-debug`, `-rc1`) отбрасывается:
 * он относится к конкретной сборке, а не к релизу. Отдельный тип нужен, потому что строковое
 * сравнение «2.0.10» < «2.0.9» — классическая ошибка, а `versionCode` до скачивания недоступен.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int = compareValuesBy(
        this,
        other,
        { it.major },
        { it.minor },
        { it.patch },
    )

    override fun toString(): String = "$major.$minor.$patch"

    companion object {

        /**
         * Разобрать тег или имя версии.
         *
         * @return версия или `null`, если строка не похожа на `vX.Y.Z` — непонятный тег лучше
         *   проигнорировать, чем предложить пользователю установить что-то не то.
         */
        fun parse(raw: String): AppVersion? {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V")
            // Отбрасываем суффикс сборки: «2.0.1-debug» → «2.0.1».
            val numeric = cleaned.substringBefore('-').substringBefore('+')
            val parts = numeric.split('.')
            if (parts.size != EXPECTED_PARTS) return null
            val numbers = parts.map { part -> part.toIntOrNull() ?: return null }
            return AppVersion(major = numbers[0], minor = numbers[1], patch = numbers[2])
        }

        private const val EXPECTED_PARTS = 3
    }
}
