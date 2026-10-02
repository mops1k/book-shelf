package io.github.bookshelf.core.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Правила умной полки («Архитектура» §3.3, ADR-008).
 *
 * Тот же набор условий, что и у фасетных фильтров, и это не совпадение: состав умной полки
 * считается **тем же предикатом**, что и полка после фильтра. Второй способ отбора книг разошёлся
 * бы с первым — «полка по жанру» показывала бы не то, что показывает фильтр по тому же жанру.
 *
 * Хранятся JSON-ом в `collections.rulesJson`: наборы строк и флаг — устойчивый формат, который
 * можно расширять без миграции схемы.
 *
 * @param authors имена авторов (`books.authorsText`), «ИЛИ» внутри набора;
 * @param series имена серий;
 * @param tags коды жанров/тегов из метаданных (`sf_space`), подпись переводит экран (`genreText`);
 * @param formats расширения файлов (`fb2`, `epub`);
 * @param statuses статусы чтения именами (`UNREAD`, `READING`, `READ`);
 * @param favoritesOnly только избранное.
 */
/**
 * Часть подписи правила умной полки.
 *
 * Разделение обязательно («TDD» §8): значение части — код метаданных (`sf_space`, `UNREAD`)
 * или имя (автор, серия) — данные, а слова выбирает экран, у которого есть ресурсы.
 */
sealed interface SmartRulePart {

    /** Жанр: [code] — код метаданных, подпись переводится на экране. */
    data class Genre(val code: String) : SmartRulePart

    /** Автор: имя из метаданных, данные. */
    data class Author(val name: String) : SmartRulePart

    /** Серия: имя из метаданных, данные. */
    data class Series(val name: String) : SmartRulePart

    /** Формат файла: расширение, данные. */
    data class Format(val extension: String) : SmartRulePart

    /** Статус чтения: [name] — имя значения (`UNREAD`), подпись переводится на экране. */
    data class Status(val name: String) : SmartRulePart

    /** Только избранное. */
    data object Favorites : SmartRulePart
}

@Serializable
data class SmartRules(
    val authors: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
) {

    /**
     * Правил нет.
     *
     * Полка без условий — это не «вся библиотека»: показать 72 книги там, где пользователь
     * ожидал подборку, значит соврать о содержимом полки. Такая полка честно пустая.
     */
    val isEmpty: Boolean
        get() = authors.isEmpty() && series.isEmpty() && tags.isEmpty() &&
            formats.isEmpty() && statuses.isEmpty() && !favoritesOnly

    /** Запрос полки: состав умной полки обязан совпадать с фильтром по тем же условиям. */
    fun toBookQuery(): BookQuery = BookQuery(
        authorNames = authors,
        series = series,
        tags = tags,
        formats = formats,
        // Неизвестное имя статуса пропускаем: правило из старой версии не должно ронять полку.
        statuses = statuses.mapNotNull { name ->
            ReadingStatus.entries.firstOrNull { status -> status.name == name }
        }.toSet(),
        favoritesOnly = favoritesOnly,
    )

    /** Сериализовать для `collections.rulesJson`. */
    fun toJson(): String = json.encodeToString(this)

    /**
     * Подпись правила для бейджа полки: «жанр: Космическая фантастика», «избранное».
     *
     * @param maxParts сколько условий показать. По умолчанию два — столько читается в карточке
     *   и в подписи, где место есть. В строке списка полок места хватает на одно условие: при двух
     *   подпись («Умная полка · жанр: Космическая фантастика, статус: не прочитано») обрывалась
     *   многоточием на середине (проверено на устройстве 28.09.2026), и пользователь видел
     *   «жанр: Космическая фа…» вместо названия жанра.
     */
    /**
     * Части подписи правила: значения и коды, без текста для человека.
     *
     * Текст рождается не здесь («TDD» §8): у слоя данных нет ресурсов, и готовая русская
     * строка («жанр: Космическая фантастика») показывалась бы по-русски и в английском интерфейсе.
     * Экран собирает подпись из этих частей: код жанра переводится `genreText`, имя статуса —
     * `readingStatusText`, имена авторов и серий — данные и показываются как есть.
     *
     * По одному условию каждого вида: список из двух одинаковых условий смысла не имеет.
     */
    fun parts(): List<SmartRulePart> = buildList {
        tags.take(1).forEach { code -> add(SmartRulePart.Genre(code)) }
        authors.take(1).forEach { name -> add(SmartRulePart.Author(name)) }
        series.take(1).forEach { name -> add(SmartRulePart.Series(name)) }
        formats.take(1).forEach { extension -> add(SmartRulePart.Format(extension)) }
        if (statuses.isNotEmpty()) add(SmartRulePart.Status(statuses.first()))
        if (favoritesOnly) add(SmartRulePart.Favorites)
    }

    companion object {
        /** `ignoreUnknownKeys` — чтобы правило из будущей версии не ломало разбор. */
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Разобрать правила; `null` — их нет или они битые (полка тогда считается пустой). */
        fun fromJson(raw: String?): SmartRules? = raw
            ?.takeIf { it.isNotBlank() }
            ?.let { text -> runCatching { json.decodeFromString<SmartRules>(text) }.getOrNull() }

        /** Правила из текущего фильтра — «Сохранить текущий фильтр как полку». */
        fun fromQuery(query: BookQuery): SmartRules = SmartRules(
            authors = query.authorNames,
            series = query.series,
            tags = query.tags,
            formats = query.formats,
            statuses = query.statuses.map { status -> status.name }.toSet(),
            favoritesOnly = query.favoritesOnly,
        )
    }
}
