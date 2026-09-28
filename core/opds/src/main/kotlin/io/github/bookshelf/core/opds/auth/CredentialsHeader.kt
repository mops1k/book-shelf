package io.github.bookshelf.core.opds.auth

import okhttp3.Request

/**
 * Заголовок авторизации для каталога.
 *
 * Схема выбрана по спецификации OPDS: используется HTTP Basic. Логин и пароль кодируются
 * Base64 — это **не** шифрование, поэтому такие каталоги годятся только для личных учётных
 * данных на доверенном сервере; для публичных каталогов вроде Flibusta заголовок не нужен вовсе.
 */
internal object CredentialsHeader {

    fun apply(builder: Request.Builder, credentials: OpdsCredentials?) {
        if (credentials == null) return
        if (credentials.username.isBlank()) return
        val token = okhttp3.Credentials.basic(credentials.username, credentials.password)
        builder.header("Authorization", token)
    }
}
