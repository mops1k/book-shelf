package io.github.bookshelf.core.opds.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Шифрование секретов ключом из Android Keystore (AES-256-GCM).
 *
 * Ключ создаётся один раз и хранится системой: приложение получает только ссылку на него, сам
 * материал ключа недоступен даже нам. GCM выбран потому, что он проверяет целостность: подмена
 * блоба в файле настроек не превратится в «другой пароль», а честно не расшифруется.
 *
 * Формат блоба: `IV (12 байт) + шифротекст`, всё в Base64 без переносов.
 */
@Singleton
class AndroidKeystoreCipher @Inject constructor() : SecretCipher {

    override fun encrypt(plain: String): AppResult<String> = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // IV уникален для каждой записи и нужен для расшифровки: он не секрет, поэтому лежит рядом.
        Base64.encodeToString(cipher.iv + encrypted, BASE64_FLAGS)
    }.fold(
        onSuccess = { AppResult.success(it) },
        onFailure = { AppResult.failure(keystoreError("зашифровать пароль каталога", it)) },
    )

    override fun decrypt(stored: String): AppResult<String> = runCatching {
        val blob = Base64.decode(stored, BASE64_FLAGS)
        if (blob.size <= IV_LENGTH) {
            error("блоб короче, чем вектор инициализации")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, blob, 0, IV_LENGTH),
        )
        String(cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH), Charsets.UTF_8)
    }.fold(
        onSuccess = { AppResult.success(it) },
        onFailure = { AppResult.failure(keystoreError("прочитать пароль каталога", it)) },
    )

    /**
     * Ключ из Keystore, создаётся при первом обращении.
     *
     * `setUserAuthenticationRequired(false)`: каталог должен открываться и без разблокировки, иначе
     * фоновая синхронизация и загрузка книг молча ломались бы на заблокированном экране.
     */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keystoreError(action: String, error: Throwable): AppError = AppError.Unknown(
        message = "Не удалось $action: ${error.message ?: error::class.java.simpleName}",
        cause = error,
    )

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "bookshelf-opds-secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val TAG_BITS = 128
        const val IV_LENGTH = 12
        const val BASE64_FLAGS = Base64.NO_WRAP
    }
}
