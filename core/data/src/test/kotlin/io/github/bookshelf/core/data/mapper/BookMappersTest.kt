package io.github.bookshelf.core.data.mapper

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataOrigin
import io.github.bookshelf.core.data.model.ParseState
import io.github.bookshelf.core.data.model.ReadingStatus
import org.junit.Test

/**
 * Тест маппинга entity ↔ домен (`BookMappers.kt`).
 *
 * Проверяется чистое ядро `bookToDomain` и преобразования перечислений по `name`. Сам
 * `BookEntity.toDomain` — тонкий адаптер над этим ядром, поэтому здесь не требуется поднимать
 * `:core:database`; имена entity-полей проверяются компиляцией `:core:data`.
 */
class BookMappersTest {

    @Test
    fun book_to_domain_keeps_all_mapped_fields() {
        val book = bookToDomain(
            id = "book-1",
            title = "Заголовок",
            authorsText = "Иванов И.",
            extension = "fb2",
            sizeBytes = 1024L,
            addedAt = 10L,
            authors = listOf("Иванов И."),
            seriesName = "Серия",
            seriesIndex = 2.0,
            coverPath = "/covers/book-1.webp",
            language = "ru",
            publishedYear = 2020,
            publisher = "Издатель",
            description = "Аннотация",
            pageCount = 300,
            readingStatusName = "READING",
            favorite = true,
            rating = 5,
            isMissing = true,
            lastOpenedAt = 20L,
        )

        assertThat(book.id).isEqualTo("book-1")
        assertThat(book.title).isEqualTo("Заголовок")
        assertThat(book.authors).containsExactly("Иванов И.")
        assertThat(book.seriesName).isEqualTo("Серия")
        assertThat(book.seriesIndex).isEqualTo(2.0)
        assertThat(book.coverPath).isEqualTo("/covers/book-1.webp")
        assertThat(book.extension).isEqualTo("fb2")
        assertThat(book.sizeBytes).isEqualTo(1024L)
        assertThat(book.publishedYear).isEqualTo(2020)
        assertThat(book.pageCount).isEqualTo(300)
        assertThat(book.readingStatus).isEqualTo(ReadingStatus.READING)
        assertThat(book.favorite).isTrue()
        assertThat(book.rating).isEqualTo(5)
        assertThat(book.isMissing).isTrue()
        assertThat(book.addedAt).isEqualTo(10L)
        assertThat(book.lastOpenedAt).isEqualTo(20L)
    }

    @Test
    fun progress_is_not_taken_from_books_row() {
        // Прогресс живёт в reading_progress: строка books его не содержит.
        val book = bookToDomain(
            id = "book-1",
            title = "Заголовок",
            authorsText = "",
            extension = "epub",
            sizeBytes = 0L,
            addedAt = 0L,
        )

        assertThat(book.progressPercent).isNull()
        assertThat(book.subtitle).isNull()
    }

    @Test
    fun parse_state_and_reason_reach_the_domain_model() {
        val book = bookToDomain(
            id = "book-1",
            title = "Битый.fb2",
            authorsText = "",
            extension = "fb2",
            sizeBytes = 10L,
            addedAt = 1L,
            parseStateName = "FAILED",
            parseError = "FB2 внутри архива превышает допустимый размер XML",
        )

        // Раньше этих полей в модели не было, и карточка книги рисовала баннер «Не удалось
        // разобрать», который никто не заполнял.
        assertThat(book.parseState).isEqualTo(ParseState.FAILED)
        assertThat(book.parseError).contains("превышает допустимый размер")
    }

    @Test
    fun reading_status_is_mapped_by_name_with_safe_fallback() {
        assertThat(readingStatusFromName("READ")).isEqualTo(ReadingStatus.READ)
        assertThat(readingStatusFromName("READING")).isEqualTo(ReadingStatus.READING)
        assertThat(readingStatusFromName(null)).isEqualTo(ReadingStatus.UNREAD)
        assertThat(readingStatusFromName("NO_SUCH_STATUS")).isEqualTo(ReadingStatus.UNREAD)
    }

    @Test
    fun parse_state_is_mapped_by_name_with_safe_fallback() {
        assertThat(parseStateFromName("OK")).isEqualTo(ParseState.OK)
        assertThat(parseStateFromName("PARTIAL")).isEqualTo(ParseState.PARTIAL)
        assertThat(parseStateFromName("FAILED")).isEqualTo(ParseState.FAILED)
        assertThat(parseStateFromName("UNSUPPORTED")).isEqualTo(ParseState.UNSUPPORTED)
        assertThat(parseStateFromName(null)).isEqualTo(ParseState.PENDING)
        assertThat(parseStateFromName("NO_SUCH_STATE")).isEqualTo(ParseState.PENDING)
    }

    @Test
    fun metadata_origin_is_mapped_by_name_with_safe_fallback() {
        assertThat(metadataOriginFromName("EMBEDDED")).isEqualTo(MetadataOrigin.EMBEDDED)
        assertThat(metadataOriginFromName("MANUAL")).isEqualTo(MetadataOrigin.MANUAL)
        assertThat(metadataOriginFromName(null)).isEqualTo(MetadataOrigin.NONE)
        assertThat(metadataOriginFromName("NO_SUCH_ORIGIN")).isEqualTo(MetadataOrigin.NONE)
    }

    @Test
    fun domain_enums_round_trip_through_entity_names() {
        ReadingStatus.entries.forEach { status ->
            assertThat(readingStatusFromName(status.toEntityName())).isEqualTo(status)
        }
        ParseState.entries.forEach { state ->
            assertThat(parseStateFromName(state.toEntityName())).isEqualTo(state)
        }
        MetadataOrigin.entries.forEach { origin ->
            assertThat(metadataOriginFromName(origin.toEntityName())).isEqualTo(origin)
        }
    }

    @Test
    fun library_query_is_converted_to_page_request() {
        val request = LibraryQuery(page = 2, pageSize = 25).toPageRequest()

        assertThat(request.offset).isEqualTo(50)
        assertThat(request.limit).isEqualTo(25)
        assertThat(request.page).isEqualTo(2)
    }
}
