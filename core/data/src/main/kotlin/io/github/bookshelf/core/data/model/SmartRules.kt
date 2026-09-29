package io.github.bookshelf.core.data.model

import io.github.bookshelf.core.data.tag.genreDisplayName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Правила умной полки (docs/architecture.md §3.3, ADR-008).
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
 * @param tags коды жанров/тегов из метаданных (`sf_space`), подпись переводится [genreDisplayName];
 * @param formats расширения файлов (`fb2`, `epub`);
 * @param statuses статусы чтения именами (`UNREAD`, `READING`, `READ`);
 * @param favoritesOnly только избранное.
 */
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
    fun describe(maxParts: Int = DESCRIPTION_PARTS): String {
        val parts = buildList {
            tags.take(1).forEach { code -> add("жанр: ${genreDisplayName(code)}") }
            authors.take(1).forEach { name -> add("автор: $name") }
            series.take(1).forEach { name -> add("серия: $name") }
            formats.take(1).forEach { format -> add("формат: ${format.uppercase()}") }
            if (statuses.isNotEmpty()) {
                add("статус: " + statuses.map(::statusText).joinToString("/"))
            }
            if (favoritesOnly) add("избранное")
        }
        if (parts.isEmpty()) return "без условий"
        val shown = parts.take(maxParts).joinToString(", ")
        val rest = parts.size - maxParts
        return if (rest > 0) "$shown и ещё $rest" else shown
    }

    /** Статус чтения словами: в бейдже `UNREAD` читался бы как код, а не как состояние. */
    private fun statusText(name: String): String =
        ReadingStatus.entries.firstOrNull { status -> status.name == name }?.label ?: name

    companion object {
        /** Сколько условий показывать в подписи. */
        private const val DESCRIPTION_PARTS = 2

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
