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

    suspend fun updateCatalog(catalog: OpdsCatalog): AppResult<Unit>

    suspend fun removeCatalog(catalogId: String): AppResult<Unit>

    /**
     * Открыть фид каталога.
     *
     * [url] == null — корневая навигация каталога; иначе конкретная страница/раздел фида.
     */
    suspend fun browse(catalogId: String, url: String? = null): AppResult<List<OpdsEntry>>

    /**
     * Открыть страницу фида вместе со ссылкой на следующую (`rel="next"`).
     *
     * Отдельно от [browse], потому что текучей догрузке нужен адрес следующей страницы: без него
     * фид длиннее одной страницы обрывался, и дальше первой страницы пользователь не уходил.
     * [browse] остаётся удобной обёрткой для тех, кому нужны только записи.
     */
    suspend fun browsePage(catalogId: String, url: String? = null): AppResult<OpdsPage>

    fun observeDownloads(): Flow<List<OpdsDownload>>

    /** Поставить загрузку в очередь; имя файла собирается по шаблону из настроек. */
    suspend fun enqueueDownload(
        entry: OpdsEntry,
        catalogId: String,
        targetSourceId: String? = null,
        targetRelativeDir: String? = null,
    ): AppResult<Unit>

    suspend fun cancelDownload(downloadId: String): AppResult<Unit>
}
