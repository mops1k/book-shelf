package io.github.bookshelf.core.opds.client

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.auth.OpdsCredentials

/**
 * HTTP-клиент OPDS — единственное место приложения с доступом в сеть («OPDS» §4.1).
 *
 * Требования к реализации:
 * * OkHttp + отдельный дисковый `Cache`; conditional GET по `ETag`/`Last-Modified`;
 * * таймауты: connect 10 с, read 30 с; ограниченное число редиректов;
 * * ограничение размера тела фида (порядка 10 МБ), иначе фид обрывается с понятной ошибкой;
 * * `User-Agent` с версией приложения;
 * * **HTTPS по умолчанию**; `http://` допускается только для домашних каталогов в LAN и только
 *   после явного подтверждения пользователя;
 * * аутентификация Basic/Digest + cookie-сессия (`CookieJar` в памяти, сохранение опционально);
 * * никакие внешние ресурсы фида не загружаются автоматически; обложки — только по явному действию.
 */
interface OpdsHttpClient {

    /**
     * GET-запрос фида или файла.
     *
     * @param url абсолютный http/https-адрес.
     * @param conditional заголовки условного запроса; null — обычный GET.
     * @param credentials логин/пароль каталога; null — запрос без авторизации.
     */
    suspend fun get(
        url: String,
        conditional: ConditionalGet? = null,
        credentials: OpdsCredentials? = null,
    ): AppResult<HttpResponse>
}

/**
 * Ответ HTTP-клиента.
 *
 * @param status код ответа; 304 при conditional GET означает «не изменилось».
 * @param body тело ответа (для 304 — пустое).
 * @param etag значение `ETag` для следующего conditional GET.
 * @param lastModified значение `Last-Modified`.
 * @param finalUrl URL после редиректов — база для разрешения относительных ссылок.
 */
data class HttpResponse(
    val status: Int,
    val body: String,
    val etag: String?,
    val lastModified: String?,
    val finalUrl: String,
)

/** Заголовки условного GET; оба поля необязательны, но хотя бы одно обычно задано. */
data class ConditionalGet(
    val etag: String?,
    val lastModified: String?,
)
