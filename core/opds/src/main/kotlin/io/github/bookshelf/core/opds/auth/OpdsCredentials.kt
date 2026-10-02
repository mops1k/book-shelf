package io.github.bookshelf.core.opds.auth

import io.github.bookshelf.core.common.error.AppResult

/**
 * Учётные данные OPDS-каталога (Basic/Digest).
 *
 * Пароль **никогда** не хранится в БД открытым текстом: в `opds_catalogs` лежит только
 * `passwordAlias` — ключ в шифрованном хранилище. Пароль не попадает в логи, в экспорт настроек и
 * в бэкап; при экспорте каталог выгружается без пароля, с пометкой «требуется авторизация»
 * («OPDS» §2, §5).
 */
data class OpdsCredentials(
    val username: String,
    val password: String,
)

/**
 * Хранилище паролей каталогов в шифрованном виде.
 *
 * Методы возвращают [AppResult], а не «тихо ничего»: если пароль не удалось сохранить или
 * расшифровать, человек должен увидеть причину. Молчаливый отказ выглядел бы как «каталог
 * добавлен», а потом отвечал 401 без объяснений.
 */
interface CredentialStore {

    /** Сохранить учётные данные каталога под его `catalogId`. */
    suspend fun save(catalogId: String, credentials: OpdsCredentials): AppResult<Unit>

    /** Загрузить учётные данные; успех с `null` — каталог без сохранённого пароля. */
    suspend fun load(catalogId: String): AppResult<OpdsCredentials?>

    /** Удалить учётные данные вместе с каталогом. */
    suspend fun delete(catalogId: String): AppResult<Unit>
}
