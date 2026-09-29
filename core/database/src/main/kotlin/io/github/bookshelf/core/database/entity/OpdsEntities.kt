package io.github.bookshelf.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.bookshelf.core.database.model.OpdsDownloadState

/**
 * Модель OPDS: каталоги, загрузки и кэш разобранных фидов (docs/architecture.md §3.3,
 * `docs/opds.md`).
 *
 * `:core:database` про сеть ничего не знает: здесь только состояние, которое переживает
 * перезапуск процесса.
 */

/**
 * Каталог OPDS.
 *
 * Пароль в БД не хранится: лежит `passwordAlias` — ключ шифрованного хранилища, сам секрет
 * живёт вне Room (docs/opds.md).
 */
@Entity(tableName = "opds_catalogs", indices = [Index(value = ["url"], unique = true)])
data class OpdsCatalogEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    /** Версия/тип каталога строкой, например `OPDS_1_2`; значение задаёт `:core:opds`. */
    val kind: String = "OPDS_1_2",
    val username: String? = null,
    /** Ключ пароля в шифрованном хранилище, а не сам пароль. */
    val passwordAlias: String? = null,
    val requiresAuth: Boolean = false,
    val enabled: Boolean = true,
    val lastOpenedAt: Long? = null,
    val sortOrder: Int = 0,
)

/** Загрузка записи каталога OPDS в библиотеку. */
@Entity(
    tableName = "opds_downloads",
    indices = [Index(value = ["catalogId"]), Index(value = ["state"]), Index(value = ["targetSourceId"])],
)
data class OpdsDownloadEntity(
    @PrimaryKey val id: String,
    val catalogId: String,
    /** Идентификатор записи фида (`<id>`), нужен для дедупликации. */
    val entryId: String,
    val title: String,
    val authorsText: String? = null,
    /**
     * Серия книги, если каталог её сообщил.
     *
     * Хранится в строке загрузки, а не берётся из фида при скачивании: к моменту работы воркера
     * фида под рукой нет, а заполнитель `{series}` в шаблоне пути (`opds.downloadFileNameTemplate`)
     * раскладывает книги по папкам серий.
     */
    val seriesName: String? = null,
    val downloadUrl: String,
    val mimeType: String? = null,
    val expectedSize: Long? = null,
    /** Источник, в который будет записан файл. */
    val targetSourceId: String,
    /** Каталог назначения внутри источника. */
    val targetRelativeDir: String,
    val state: OpdsDownloadState = OpdsDownloadState.QUEUED,
    val bytesDownloaded: Long = 0L,
    val error: String? = null,
    /** Книга, созданная после успешной загрузки. */
    val bookId: String? = null,
    val createdAt: Long,
)

/**
 * Кэш разобранного фида для мгновенного возврата назад и офлайн-просмотра.
 *
 * PK — хеш URL: URL может быть длинным и содержать query-параметры пагинации.
 */
@Entity(tableName = "opds_feed_cache", indices = [Index(value = ["fetchedAt"])])
data class OpdsFeedCacheEntity(
    @PrimaryKey val urlHash: String,
    val url: String,
    val etag: String? = null,
    /** HTTP `Last-Modified` для conditional GET. */
    val lastModified: String? = null,
    /** Путь к телу ответа в дисковом кэше. */
    val bodyPath: String,
    val fetchedAt: Long,
)
