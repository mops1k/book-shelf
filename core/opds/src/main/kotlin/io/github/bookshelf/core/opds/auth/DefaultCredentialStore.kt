package io.github.bookshelf.core.opds.auth

import android.content.SharedPreferences
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище паролей каталогов: шифрованный текст в обычных `SharedPreferences`.
 *
 * Ключ шифрования живёт в Android Keystore и наружу не выходит; в файле настроек лежит только
 * зашифрованный блоб под ключом `catalog:<id>`. Так пароль не попадает ни в БД, ни в бэкап базы, ни
 * в экспорт настроек.
 *
 * Пишем через `commit()`, а не `apply()`: результат нужен сразу — «сохранено» и «не сохранено»
 * для пароля разные вещи, а `apply()` пишет в фоне и об ошибке не расскажет.
 */
@Singleton
class DefaultCredentialStore @Inject constructor(
    private val prefs: SharedPreferences,
    private val cipher: SecretCipher,
) : CredentialStore {

    override suspend fun save(catalogId: String, credentials: OpdsCredentials): AppResult<Unit> {
        val payload = encode(credentials)
        val encrypted = cipher.encrypt(payload)
        val blob = when (encrypted) {
            is AppResult.Failure -> return encrypted
            is AppResult.Success -> encrypted.value
        }
        val written = prefs.edit().putString(key(catalogId), blob).commit()
        return if (written) {
            AppResult.success(Unit)
        } else {
            AppResult.failure(
                AppError.Io(message = "Не удалось сохранить пароль каталога: файл настроек недоступен"),
            )
        }
    }

    override suspend fun load(catalogId: String): AppResult<OpdsCredentials?> {
        val blob = prefs.getString(key(catalogId), null) ?: return AppResult.success(null)
        val decrypted = when (val opened = cipher.decrypt(blob)) {
            is AppResult.Failure -> return opened
            is AppResult.Success -> opened.value
        }
        return decode(decrypted)
    }

    override suspend fun delete(catalogId: String): AppResult<Unit> {
        val removed = prefs.edit().remove(key(catalogId)).commit()
        return if (removed) {
            AppResult.success(Unit)
        } else {
            AppResult.failure(
                AppError.Io(message = "Не удалось удалить пароль каталога: файл настроек недоступен"),
            )
        }
    }

    /**
     * Логин и пароль одной строкой.
     *
     * Разделитель — перевод строки, а не JSON: пароль может содержать что угодно, а разбор JSON
     * добавил бы вторую точку отказа там, где нужны ровно два поля.
     */
    private fun encode(credentials: OpdsCredentials): String =
        credentials.username + SEPARATOR + credentials.password

    private fun decode(payload: String): AppResult<OpdsCredentials?> {
        val separator = payload.indexOf(SEPARATOR)
        if (separator < 0) {
            return AppResult.failure(
                AppError.Unknown(
                    message = "Сохранённые учётные данные каталога повреждены: нет разделителя",
                ),
            )
        }
        return AppResult.success(
            OpdsCredentials(
                username = payload.substring(0, separator),
                password = payload.substring(separator + SEPARATOR.length),
            ),
        )
    }

    private fun key(catalogId: String): String = KEY_PREFIX + catalogId

    internal companion object {
        const val KEY_PREFIX = "catalog:"
        const val SEPARATOR = "\n"

        /** Имя файла настроек с секретами: отдельно от обычных настроек, чтобы его было видно. */
        const val PREFS_NAME = "bookshelf_opds_secrets"
    }
}
