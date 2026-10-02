package io.github.bookshelf.core.designsystem.text

import io.github.bookshelf.core.designsystem.R

/**
 * Подпись жанра по коду из метаданных.
 *
 * Раньше таблица названий жила русским текстом в `:core:data` (`FB2_GENRE_NAMES`), и в английском
 * интерфейсе подписи фасетов, уровня обзора и правила умной полки оставались русскими: у слоя
 * данных нет ресурсов. Теперь код жанра — значение фильтра, а слова выбирает интерфейс.
 *
 * Карта, а не `when`: шестьдесят две ветки уронили бы порог `CyclomaticComplexMethod`.
 * Неизвестный код даёт `null` — экран покажет его как есть: это значение метаданных, а не текст.
 */
fun genreText(code: String): AppText? {
    val key = code.trim().lowercase()
    if (key.isEmpty()) return null
    val resource = GENRE_RESOURCES[key] ?: return null
    return AppText(resource)
}

private val GENRE_RESOURCES: Map<String, Int> = mapOf(
    "sf" to R.string.genre_sf,
    "sf_history" to R.string.genre_sf_history,
    "sf_action" to R.string.genre_sf_action,
    "sf_epic" to R.string.genre_sf_epic,
    "sf_heroic" to R.string.genre_sf_heroic,
    "sf_detective" to R.string.genre_sf_detective,
    "sf_cyberpunk" to R.string.genre_sf_cyberpunk,
    "sf_space" to R.string.genre_sf_space,
    "sf_social" to R.string.genre_sf_social,
    "sf_horror" to R.string.genre_sf_horror,
    "sf_humor" to R.string.genre_sf_humor,
    "sf_fantasy" to R.string.genre_sf_fantasy,
    "sf_fantasy_city" to R.string.genre_sf_fantasy_city,
    "dark_fantasy" to R.string.genre_dark_fantasy,
    "sf_postapocalyptic" to R.string.genre_sf_postapocalyptic,
    "sf_litrpg" to R.string.genre_sf_litrpg,
    "network_literature" to R.string.genre_network_literature,
    "detective" to R.string.genre_detective,
    "det_classic" to R.string.genre_det_classic,
    "det_police" to R.string.genre_det_police,
    "det_irony" to R.string.genre_det_irony,
    "det_history" to R.string.genre_det_history,
    "det_espionage" to R.string.genre_det_espionage,
    "det_maniac" to R.string.genre_det_maniac,
    "prose" to R.string.genre_prose,
    "prose_classic" to R.string.genre_prose_classic,
    "prose_contemporary" to R.string.genre_prose_contemporary,
    "prose_history" to R.string.genre_prose_history,
    "prose_rus_classic" to R.string.genre_prose_rus_classic,
    "prose_su_classics" to R.string.genre_prose_su_classics,
    "prose_military" to R.string.genre_prose_military,
    "love" to R.string.genre_love,
    "love_contemporary" to R.string.genre_love_contemporary,
    "love_detective" to R.string.genre_love_detective,
    "love_history" to R.string.genre_love_history,
    "love_sf" to R.string.genre_love_sf,
    "adv" to R.string.genre_adv,
    "adv_history" to R.string.genre_adv_history,
    "adv_maritime" to R.string.genre_adv_maritime,
    "adv_western" to R.string.genre_adv_western,
    "humor" to R.string.genre_humor,
    "humor_fantasy" to R.string.genre_humor_fantasy,
    "child" to R.string.genre_child,
    "child_sf" to R.string.genre_child_sf,
    "child_det" to R.string.genre_child_det,
    "child_tale" to R.string.genre_child_tale,
    "nonf" to R.string.genre_nonf,
    "science" to R.string.genre_science,
    "sci_history" to R.string.genre_sci_history,
    "sci_philosophy" to R.string.genre_sci_philosophy,
    "sci_psychology" to R.string.genre_sci_psychology,
    "sci_religion" to R.string.genre_sci_religion,
    "sci_tech" to R.string.genre_sci_tech,
    "comp" to R.string.genre_comp,
    "ref" to R.string.genre_ref,
    "religion" to R.string.genre_religion,
    "sport" to R.string.genre_sport,
    "home" to R.string.genre_home,
    "health" to R.string.genre_health,
    "business" to R.string.genre_business,
    "poetry" to R.string.genre_poetry,
    "drama" to R.string.genre_drama,
    "periodic" to R.string.genre_periodic,
)
