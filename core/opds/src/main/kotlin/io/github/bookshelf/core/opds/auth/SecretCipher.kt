package io.github.bookshelf.core.opds.auth

import io.github.bookshelf.core.common.error.AppResult

/**
 * Шифрование секретов каталогов.
 *
 * Отдельный порт, а не прямая работа с Android Keystore: шифр — единственное место, которое нельзя
 * проверить модульным тестом (в JVM нет настоящего Keystore), а логика хранения — можно и нужно.
 * В тестах подставляется обратимый фейк, в приложении — [AndroidKeystoreCipher].
 */
interface SecretCipher {

    /** Зашифровать значение; ошибка с причиной, если ключ недоступен. */
    fun encrypt(plain: String): AppResult<String>

    /** Расшифровать значение; ошибка с причиной, если данные повреждены или ключ потерян. */
    fun decrypt(stored: String): AppResult<String>
}
