package io.github.bookshelf.core.readerlink.fbreader

import io.github.bookshelf.core.common.error.AppResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Подключение к сервисам FBReader (уровни L2/L3).
 *
 * Клиент делает `bindService` к двум сервисам:
 * * `android.fbreader.action.API` — API-сервис (AIDL `ApiInterface`);
 * * `android.fbreader.action.LIBRARY_SERVICE` — сервис библиотеки (AIDL `LibraryInterface`).
 *
 * События приходят широковещательно на `android.fbreader.action.API_CALLBACK` с `int`-дополнительным
 * полем `event.type`; подписка оформляется через `ApiListener`. События не гарантированы:
 * если сервис не exported или `bindService` вернул `false`, клиент молча деградирует до L1 и
 * публикует [FbReaderEvent.ApiUnavailable] (docs/reader-integration.md §3, §4.2).
 *
 * Таймаут привязки — 3 с, после чего привязка отменяется и фиксируется деградация до L1.
 */
interface FbReaderConnection {

    /** Привязаться к сервисам. Неудача — не исключение, а `AppResult.Failure`. */
    suspend fun connect(): AppResult<Unit>

    /** Отвязаться и освободить ресурсы; повторный вызов безопасен. */
    suspend fun disconnect()

    /** true, если хотя бы один сервис FBReader сейчас доступен. */
    fun isAvailable(): Boolean

    /** Поток событий читалки; пустой поток, пока нет подписки/соединения. */
    val events: Flow<FbReaderEvent>
}

/** События, приходящие от FBReader. */
sealed interface FbReaderEvent {

    /**
     * Открыта книга.
     *
     * @param uniqueId идентификатор книги в FBReader (`getBookUniqueId`); может быть null.
     * @param filePath путь открытого файла; может быть null.
     */
    data class BookOpened(
        val uniqueId: String?,
        val filePath: String?,
    ) : FbReaderEvent

    /**
     * Изменилось положение в книге — момент, когда читаем и сохраняем прогресс.
     *
     * @param progress прогресс книги, 0..1.
     * @param pageIndex номер абзаца/страницы, если FBReader его отдал.
     */
    data class PageChanged(
        val progress: Float,
        val pageIndex: Int?,
    ) : FbReaderEvent

    /** Книга закрыта. */
    data object Closed : FbReaderEvent

    /** Сервис перестал отвечать: тихая деградация до L1, запись в диагностику. */
    data object ApiUnavailable : FbReaderEvent
}

/** Заглушка подключения к FBReader. */
@Singleton
class DefaultFbReaderConnection @Inject constructor() : FbReaderConnection {

    // STUB(Sprint 7): bindService к ACTION_API и ACTION_LIBRARY_SERVICE с таймаутом 3 с,
    // регистрация BroadcastReceiver на ACTION_API_CALLBACK.

    override suspend fun connect(): AppResult<Unit> =
        TODO("STUB(Sprint 7): bindService к сервисам FBReader с таймаутом 3 с")

    // STUB(Sprint 7): unbindService и снятие подписки на ACTION_API_CALLBACK

    override suspend fun disconnect() {
        TODO("STUB(Sprint 7): unbindService и отписка от API_CALLBACK")
    }

    // STUB(Sprint 7): реальное состояние привязки вместо константы

    override fun isAvailable(): Boolean = false

    // STUB(Sprint 7): MutableSharedFlow, наполняемый из BroadcastReceiver

    override val events: Flow<FbReaderEvent> =
        TODO("STUB(Sprint 7): поток событий из ACTION_API_CALLBACK")
}
