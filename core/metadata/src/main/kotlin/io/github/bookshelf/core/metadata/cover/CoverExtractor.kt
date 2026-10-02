package io.github.bookshelf.core.metadata.cover

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.metadata.model.CoverBytes
import io.github.bookshelf.core.metadata.parser.BookInput

/**
 * Извлечение обложки из книги.
 *
 * Парсеры отдают обложку как [CoverBytes] в своём [io.github.bookshelf.core.metadata.parser.ParseResult];
 * этот интерфейс нужен для случаев, когда обложку достаёт отдельный шаг (например, PDF через
 * `PdfRenderer`). Декодирование и кэш — ответственность `:core:covers`.
 */
interface CoverExtractor {

    /**
     * @param maxSidePx ограничение длинной стороны, по умолчанию 600 px («Архитектура» §4.4);
     * @return `null` в успешном результате означает «обложки в файле нет» — это не ошибка.
     */
    suspend fun extract(input: BookInput, maxSidePx: Int = DEFAULT_MAX_SIDE_PX): AppResult<CoverBytes?>
}

/**
 * Реализация по умолчанию; появляется в Sprint 3 вместе с кэшем обложек и парсерами MVP.
 */
class DefaultCoverExtractor : CoverExtractor {

    override suspend fun extract(input: BookInput, maxSidePx: Int): AppResult<CoverBytes?> {
        // STUB(Sprint 3): взять CoverBytes у парсера, проверить лимит maxSidePx и обернуть ошибки.
        TODO("STUB(Sprint 3): извлечь CoverBytes через парсер, ограничить maxSidePx, обернуть ошибки")
    }
}

/** Длинная сторона обложки по умолчанию: 600 px — компромисс качества и памяти (e-ink). */
private const val DEFAULT_MAX_SIDE_PX = 600
