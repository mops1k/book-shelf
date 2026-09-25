package io.github.bookshelf.core.opds.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Учётные данные OPDS-каталога (Basic/Digest).
 *
 * Пароль **никогда** не хранится в БД открытым текстом: в `opds_catalogs` лежит только
 * `passwordAlias` — ключ в шифрованном хранилище (`EncryptedSharedPreferences`/Keystore либо
 * `EncryptedFile`). Пароль не попадает в логи, в экспорт настроек и в бэкап; при экспорте
 * каталог выгружается без пароля, с пометкой «требуется авторизация» (docs/opds.md §2, §5).
 */
data class OpdsCredentials(
    val username: String,
    val password: String,
)

/** Хранилище паролей каталогов в шифрованном виде. */
interface CredentialStore {

    /** Сохранить учётные данные каталога под его `catalogId`. */
    suspend fun save(catalogId: String, credentials: OpdsCredentials)

    /** Загрузить учётные данные; null — каталог без сохранённого пароля. */
    suspend fun load(catalogId: String): OpdsCredentials?

    /** Удалить учётные данные вместе с каталогом. */
    suspend fun delete(catalogId: String)
}

/**
 * Заглушка хранилища паролей.
 *
 * Для реализации в Sprint 8 нужна зависимость `androidx.security:security-crypto`
 * (`EncryptedSharedPreferences`); сейчас её нет в version catalog — добавить вместе с реализацией.
 */
@Singleton
class DefaultCredentialStore @Inject constructor() : CredentialStore {

    // STUB(Sprint 8): EncryptedSharedPreferences/Keystore; в БД — только passwordAlias

    override suspend fun save(catalogId: String, credentials: OpdsCredentials) {
        TODO("STUB(Sprint 8): сохранить пароль в шифрованном хранилище")
    }

    // STUB(Sprint 8): чтение из шифрованного хранилища по catalogId

    override suspend fun load(catalogId: String): OpdsCredentials? = TODO(
        "STUB(Sprint 8): загрузить учётные данные из шифрованного хранилища",
    )

    // STUB(Sprint 8): удаление записи при удалении каталога

    override suspend fun delete(catalogId: String) {
        TODO("STUB(Sprint 8): удалить учётные данные каталога")
    }
}
