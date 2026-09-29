package io.github.bookshelf.core.data.tag

/**
 * Названия жанров для показа.
 *
 * В базе жанры хранятся так, как их пишут метаданные: FB2 использует коды (`sf_space`,
 * `detective`). Показывать код в списке нельзя — пользователь не обязан знать, что `sf_litrpg`
 * это «ЛитРПГ». Поэтому код остаётся значением фильтра (`id`), а подпись берётся отсюда.
 *
 * Неизвестный код возвращается как есть: лучше редкий код, чем пустая подпись или «Неизвестно».
 * Список намеренно не полный (в FB2 несколько сотен кодов) — расширяется по мере надобности.
 */
private val FB2_GENRE_NAMES: Map<String, String> = mapOf(
    "sf" to "Фантастика",
    "sf_history" to "Историческая фантастика",
    "sf_action" to "Боевая фантастика",
    "sf_epic" to "Эпическая фантастика",
    "sf_heroic" to "Героическая фантастика",
    "sf_detective" to "Фантастический детектив",
    "sf_cyberpunk" to "Киберпанк",
    "sf_space" to "Космическая фантастика",
    "sf_social" to "Социальная фантастика",
    "sf_horror" to "Фантастический ужас",
    "sf_humor" to "Юмористическая фантастика",
    "sf_fantasy" to "Фэнтези",
    "sf_fantasy_city" to "Городское фэнтези",
    "dark_fantasy" to "Тёмное фэнтези",
    "sf_postapocalyptic" to "Постапокалипсис",
    "sf_litrpg" to "ЛитРПГ",
    "network_literature" to "Сетевая литература",
    "detective" to "Детективы",
    "det_classic" to "Классический детектив",
    "det_police" to "Полицейский детектив",
    "det_irony" to "Иронический детектив",
    "det_history" to "Исторический детектив",
    "det_espionage" to "Шпионский детектив",
    "det_maniac" to "Маньяки",
    "prose" to "Проза",
    "prose_classic" to "Классическая проза",
    "prose_contemporary" to "Современная проза",
    "prose_history" to "Историческая проза",
    "prose_rus_classic" to "Русская классика",
    "prose_su_classics" to "Советская классика",
    "prose_military" to "Военная проза",
    "love" to "Любовные романы",
    "love_contemporary" to "Современные любовные романы",
    "love_detective" to "Остросюжетные любовные романы",
    "love_history" to "Исторические любовные романы",
    "love_sf" to "Любовно-фантастические романы",
    "adv" to "Приключения",
    "adv_history" to "Исторические приключения",
    "adv_maritime" to "Морские приключения",
    "adv_western" to "Вестерн",
    "humor" to "Юмор",
    "humor_fantasy" to "Юмористическое фэнтези",
    "child" to "Детская литература",
    "child_sf" to "Детская фантастика",
    "child_det" to "Детские детективы",
    "child_tale" to "Сказки",
    "nonf" to "Документальное",
    "science" to "Наука",
    "sci_history" to "История",
    "sci_philosophy" to "Философия",
    "sci_psychology" to "Психология",
    "sci_religion" to "Религия",
    "sci_tech" to "Технические науки",
    "comp" to "Компьютеры",
    "ref" to "Справочная литература",
    "religion" to "Религия и духовность",
    "sport" to "Спорт",
    "home" to "Дом и семья",
    "health" to "Здоровье",
    "business" to "Деловая литература",
    "poetry" to "Поэзия",
    "drama" to "Драматургия",
    "periodic" to "Периодика",
)

/** Подпись жанра: известный код переводится, неизвестный показывается как есть. */
fun genreDisplayName(code: String): String {
    val key = code.trim().lowercase()
    return FB2_GENRE_NAMES[key] ?: code.trim()
}
