package io.github.bookshelf.core.opds.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.error.isSuccess
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Хранилище паролей каталогов.
 *
 * Дефект, ради которого это появилось: хранилище было заглушкой с `TODO`, а все запросы уходили без
 * авторизации — каталог с паролем нельзя было ни добавить, ни открыть. Шифр подставлен фейком:
 * настоящий Keystore живёт только на устройстве, а проверять здесь нужно логику хранения — ключи,
 * изоляцию каталогов, удаление и честные отказы.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class DefaultCredentialStoreTest {

    private val cipher = FakeCipher()
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var store: DefaultCredentialStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences(DefaultCredentialStore.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = DefaultCredentialStore(prefs, cipher)
    }

    @Test
    fun `saved credentials are loaded back`() = runTest {
        store.save(CATALOG_ID, CREDENTIALS).also { assertThat(it.isSuccess).isTrue() }

        assertThat(store.load(CATALOG_ID).getOrNull()).isEqualTo(CREDENTIALS)
    }

    @Test
    fun `password is not kept in plain text`() = runTest {
        store.save(CATALOG_ID, CREDENTIALS)

        // В файле настроек лежит шифротекст: пароль не должен читаться даже глазами.
        val raw = prefs.all.values.joinToString(" ")
        assertThat(raw).doesNotContain(CREDENTIALS.password)
        assertThat(raw).doesNotContain(CREDENTIALS.username)
        assertThat(raw).contains(cipher.encryptedPrefix)
    }

    @Test
    fun `catalogs are isolated from each other`() = runTest {
        store.save("catalog-1", CREDENTIALS)
        store.save("catalog-2", OpdsCredentials("second", "second-pass"))

        assertThat(store.load("catalog-1").getOrNull()).isEqualTo(CREDENTIALS)
        assertThat(store.load("catalog-2").getOrNull())
            .isEqualTo(OpdsCredentials("second", "second-pass"))
    }

    @Test
    fun `a catalog without saved password loads as empty`() = runTest {
        // Успех с `null`, а не ошибка: публичные каталоги вроде Флибусты пароля не требуют.
        assertThat(store.load("catalog-without-secret").isSuccess).isTrue()
        assertThat(store.load("catalog-without-secret").getOrNull()).isNull()
    }

    @Test
    fun `deleting credentials removes them`() = runTest {
        store.save(CATALOG_ID, CREDENTIALS)

        store.delete(CATALOG_ID).also { assertThat(it.isSuccess).isTrue() }

        assertThat(store.load(CATALOG_ID).getOrNull()).isNull()
        assertThat(prefs.all).isEmpty()
    }

    @Test
    fun `encryption failure is reported instead of pretending to save`() = runTest {
        cipher.failEncrypt = true

        val result = store.save(CATALOG_ID, CREDENTIALS)

        assertThat(result.isFailure).isTrue()
        assertThat(prefs.all).isEmpty()
    }

    @Test
    fun `broken secret is reported instead of going unauthenticated`() = runTest {
        store.save(CATALOG_ID, CREDENTIALS)
        cipher.failDecrypt = true

        val result = store.load(CATALOG_ID)

        // Молчаливый `null` означал бы «каталог без пароля»: человек увидел бы 401 без причины.
        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()?.message).contains("пароль")
    }

    @Test
    fun `damaged payload is reported`() = runTest {
        store.save(CATALOG_ID, CREDENTIALS)
        // Шифр вернул строку без разделителя — это признак испорченных данных, а не «нет пароля».
        cipher.decryptOverride = "без разделителя"

        assertThat(store.load(CATALOG_ID).isFailure).isTrue()
    }

    /**
     * Обратимый фейк шифра.
     *
     * Настоящий Keystore в JVM недоступен, а проверять надо логику хранения; поэтому шифр здесь —
     * «префикс + значение наоборот», и отдельно проверяется, что в настройках нет открытого текста.
     */
    private class FakeCipher : SecretCipher {

        val encryptedPrefix = "enc:"
        var failEncrypt = false
        var failDecrypt = false
        var decryptOverride: String? = null

        override fun encrypt(plain: String): AppResult<String> = when {
            failEncrypt -> AppResult.failure(AppError.Unknown(message = "ключ недоступен"))
            else -> AppResult.success(encryptedPrefix + plain.reversed())
        }

        override fun decrypt(stored: String): AppResult<String> = when {
            failDecrypt -> AppResult.failure(AppError.Unknown(message = "не удалось прочитать пароль"))
            decryptOverride != null -> AppResult.success(decryptOverride!!)
            !stored.startsWith(encryptedPrefix) -> AppResult.failure(AppError.Unknown(message = "чужой блоб"))
            else -> AppResult.success(stored.removePrefix(encryptedPrefix).reversed())
        }
    }

    private companion object {
        const val CATALOG_ID = "catalog-1"
        val CREDENTIALS = OpdsCredentials(username = "reader", password = "s3cret-pass")
    }
}
