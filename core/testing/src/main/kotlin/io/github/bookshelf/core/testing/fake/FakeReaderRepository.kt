package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.repository.ReaderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf

/**
 * Фейк внешних читалок для тестов.
 *
 * Заведён по правилу ADR-012 (свои типы не мокаются): `mockk<ReaderRepository>` привязывал бы
 * проверки к внутренностям, а интерфейс у читалки маленький — свой фейк дешевле. Появился вместе с
 * первым тестом `OnboardingViewModel`: до него у онбординга тестов на ViewModel не было, потому что
 * в `:core:testing` не хватало этой читалки.
 *
 * @param readers что отдаёт [installedReaders]; список можно менять между шагами теста.
 */
class FakeReaderRepository(
    var readers: List<ReaderProfileSummary> = emptyList(),
) : ReaderRepository {

    /** Назначения по умолчанию: формат → id профиля (или `null` — «спрашивать всегда»). */
    val defaults: MutableMap<String, String?> = mutableMapOf()

    private val installed: MutableStateFlow<List<ReaderProfileSummary>> = MutableStateFlow(readers)

    /** Открытия книги: id книги → id профиля. */
    val opened: MutableList<Pair<String, String?>> = mutableListOf()

    override fun installedReaders(): Flow<List<ReaderProfileSummary>> = installed

    override fun defaultForFormat(format: String): Flow<String?> = flowOf(defaults[format])

    override fun readerDefaults(): Flow<Map<String, String>> = flowOf(
        defaults.entries.mapNotNull { (format, profileId) ->
            profileId?.let { id -> format to id }
        }.toMap(),
    )

    override suspend fun setDefault(format: String, profileId: String?): AppResult<Unit> {
        defaults[format] = profileId
        return AppResult.success(Unit)
    }

    override suspend fun openBook(bookId: String, profileId: String?): AppResult<Unit> {
        opened += bookId to profileId
        return AppResult.success(Unit)
    }
}
