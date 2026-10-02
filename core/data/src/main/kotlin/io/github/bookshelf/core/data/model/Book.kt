package io.github.bookshelf.core.data.model

import io.github.bookshelf.core.common.error.ParseFailure
/**
 * Доменная модель книги для UI (docs/architecture.md §3.1).
 *
 * Это **не** Room-entity: feature-модули видят только `:core:data` и не знают про БД. Отсюда
 * следствия:
 * * `authors: List<String>` уже развёрнуты из связи `book_authors` (в entity лежит только
 *   денормализованный `authorsText`);
 * * `progressPercent` — из таблицы `reading_progress`, а не из `books`;
 * * enum'ы — свои ([ReadingStatus]), а не entity-перечисления (см. [MetadataOrigin], [ParseState]).
 *
 * @param authorsText денормализованная строка авторов «Фамилия И. О., …» для быстрой отрисовки;
 * @param coverPath путь к файлу в кэше обложек (docs/architecture.md §5) или `null`;
 * @param extension расширение без точки в нижнем регистре;
 * @param isMissing файл исчез, но запись сохранена (история чтения и коллекции не теряются);
 * @param progressPercent прогресс чтения 0..100, если читалка его отдаёт.
 */
data class Book(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authorsText: String,
    val authors: List<String> = emptyList(),
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val coverPath: String? = null,
    val extension: String,
    /**
     * `content://`-URI документа в дереве SAF — по нему книга открывается читалкой.
     *
     * В доменную модель выведено для карточки книги: строки «Путь» и «Документ» были
     * пустыми всегда, потому что UI брал эти значения из состояния, а модель их не отдавала.
     */
    val documentUri: String = "",
    /** Путь внутри источника: «Лем/Солярис.fb2». */
    val relativePath: String = "",
    /**
     * Папка-источник, из которой пришла книга.
     *
     * Нужна карточке книги: по одному относительному пути не понять, где именно лежит файл,
     * когда папок несколько. Имя папки берётся из `sources.displayName` по этому идентификатору.
     */
    val sourceId: String = "",
    val sizeBytes: Long,
    val language: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val description: String? = null,
    val pageCount: Int? = null,
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val favorite: Boolean = false,
    val rating: Int? = null,
    val isMissing: Boolean = false,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val progressPercent: Float? = null,
    /**
     * Состояние разбора файла.
     *
     * Нужно экранам, а не только данным: карточка показывает причину отказа разбора, а отчёт
     * сканирования — список проблемных файлов. Раньше этих полей в модели не было, и оба места
     * рисовали пустоту: поле причины в карточке никто не заполнял, а раздел «Проблемные файлы»
     * всегда сообщал «Проблемных файлов нет», даже когда не разобралась не одна книга
     * (проверено на устройстве 30.09.2026 — 19 таких книг, раздел пуст).
     */
    val parseState: ParseState = ParseState.PENDING,
    /**
     * Код причины отказа разбора; `null`, если разбор прошёл или ещё не было.
     *
     * Показывается словами на экране (`ParseFailure` → ресурс): сама причина переживает
     * перезапуск, а готовый текст в базе ломал бы английский интерфейс.
     */
    val parseFailure: ParseFailure? = null,
    /**
     * Диагностика разбора от парсера: путь, лимит, ответ провайдера. В интерфейс не выводится
     * (docs/tdd.md §7) — человеку показывается [parseFailure], а подробность уходит в журнал.
     */
    val parseError: String? = null,
)
