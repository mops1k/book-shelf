package io.github.bookshelf.core.readerlink.fbreader

/**
 * Сборка доменных значений из ответов сервиса по кодам `ApiMethods`.
 *
 * Вся работа с кодами собрана здесь, а привязка к сервису — в [FbReaderTransport]: так разбор
 * ответов (что именно значит код 505 и что делать, если тип ответа не тот) проверяется тестами
 * без Android и без живой читалки.
 *
 * «Нет данных» и «ответ не того типа» намеренно не различаются: и то и другое означает, что
 * показать нечего, а выдуманное значение попало бы на полку как настоящий прогресс.
 */
class DefaultFbReaderApi(
    private val transport: FbReaderTransport,
) : FbReaderApi {

    override suspend fun connect(): Boolean = transport.connect()

    override suspend fun disconnect() = transport.disconnect()

    override fun isAvailable(): Boolean = transport.isConnected()

    override suspend fun currentBook(): FbReaderOpenBook? {
        // Пустой путь означает, что книга не открыта: сервис отвечает всегда, даже без книги.
        val path = text(FbReaderCodes.GET_BOOK_FILE_PATH)?.takeIf { value -> value.isNotBlank() }
            ?: return null
        return FbReaderOpenBook(
            path = path,
            title = text(FbReaderCodes.GET_BOOK_TITLE),
            language = text(FbReaderCodes.GET_BOOK_LANGUAGE),
        )
    }

    override suspend fun currentPosition(): FbReaderReadingPosition? {
        val position = transport.request(FbReaderCodes.GET_PAGE_START) as? FbReaderValue.Position
            ?: return null
        // Числа абзацев может не быть (читалка ещё не посчитала текст) — тогда 0, а решение
        // «показывать нечего» принимает адаптер.
        val paragraphs = (transport.request(FbReaderCodes.GET_PARAGRAPHS_NUMBER) as? FbReaderValue.Number)
            ?.value
            ?.coerceAtLeast(0)
            ?: 0
        return FbReaderReadingPosition(
            paragraphIndex = position.paragraphIndex,
            elementIndex = position.elementIndex,
            paragraphsNumber = paragraphs,
        )
    }

    /** Строка по коду: `null` и при отсутствии ответа, и при ответе другого типа. */
    private suspend fun text(code: Int): String? = (transport.request(code) as? FbReaderValue.Text)?.value
}
