package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.OpdsCatalog
import io.github.bookshelf.core.data.model.OpdsDownload
import io.github.bookshelf.core.data.model.OpdsEntry
import io.github.bookshelf.core.data.model.OpdsPage
import kotlinx.coroutines.flow.Flow

/**
 * OPDS: каталоги, браузер фидов и загрузки (docs/architecture.md §3.3, ADR-006).
 *
 * Сеть — только по явному действию пользователя с добавленным им каталогом. Каталог добавляется
 * вручную по URL (с опциональной Basic/Digest-авторизацией; пароль хранится зашифрованным и здесь
 * не фигурирует — только алиас). Загрузка идёт `.part`-файлом с атомарным переименованием, поэтому
 * оборванная загрузка не оставляет «полуфайл» в библиотеке.
 */
interface OpdsRepository {

    fun observeCatalogs(): Flow<List<OpdsCatalog>>

    /** Добавить каталог вручную; [password] используется один раз и сохраняется в шифрованном виде. */
    suspend fun addCatalog(
        title: String,
        url: String,
        username: String? = null,
        password: String? = null,
    ): AppResult<OpdsCatalog>

    /**
     * Изменить каталог.
     *
     * @param password новый пароль; `null` — оставить сохранённый. Пустая строка тоже означает
     *   «не менять»: поле пароля в форме всегда пустое, и стирать секрет от пустого поля нельзя.
     */
    suspend fun updateCatalog(catalog: OpdsCatalog, password: String? = null): AppResult<Unit>

    suspend fun removeCatalog(catalogId: String): AppResult<Unit>

    /**
     * Открыть фид каталога.
     *
     * [url] == null — корневая навигация каталога; иначе конкретная страница/раздел фида.
     */
    suspend fun browse(catalogId: String, url: String? = null): AppResult<List<OpdsEntry>>

    /**
     * Открыть страницу фида вместе со ссылкой на следующую (`rel="next"`) и шаблоном поиска.
     *
     * Отдельно от [browse], потому что текучей догрузке нужен адрес следующей страницы: без него
     * фид длиннее одной страницы обрывался, и дальше первой страницы пользователь не уходил.
     * [browse] остаётся удобной обёрткой для тех, кому нужны только записи.
     */
    suspend fun browsePage(catalogId: String, url: String? = null): AppResult<OpdsPage>

    /**
     * Поиск по каталогу через OpenSearch (docs/opds.md §3.2).
     *
     * [searchTemplate] — то, что каталог объявил ссылкой `rel="search"`: готовый шаблон с
     * `{searchTerms}` либо дескриптор OpenSearch, внутри которого шаблон лежит. Второй случай
     * разбирает репозиторий — экран не должен знать ни про маркер, ни про дескрипторы.
     *
     * Результат — обычная страница фида: у неё есть `rel="next"` для догрузки, а [OpdsPage.url]
     * хранит адрес запроса, по которому карточка записи найдёт её снова.
     */
    suspend fun search(
        catalogId: String,
        query: String,
        searchTemplate: String,
    ): AppResult<OpdsPage>

    fun observeDownloads(): Flow<List<OpdsDownload>>

    /** Поставить загрузку в очередь; имя файла собирается по шаблону из настроек. */
    suspend fun enqueueDownload(
        entry: OpdsEntry,
        catalogId: String,
        targetSourceId: String? = null,
        targetRelativeDir: String? = null,
    ): AppResult<Unit>

    suspend fun cancelDownload(downloadId: String): AppResult<Unit>

    /**
     * Очистить историю загрузок.
     *
     * Убирает завершённые, сбойные и отменённые записи; активные загрузки не трогает, книги в
     * библиотеке остаются. Возвращает число убранных записей.
     */
    suspend fun clearDownloadHistory(): AppResult<Int>
}
