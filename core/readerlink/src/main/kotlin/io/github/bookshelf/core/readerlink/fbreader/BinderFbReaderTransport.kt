package io.github.bookshelf.core.readerlink.fbreader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Транспорт к API-сервису FBReader через `bindService`.
 *
 * Что здесь проверено на живом устройстве (Bigme HiBreak, FBReader Premium 3.8.31, 29.09.2026):
 * * сервис резолвится по действию `com.fbreader.action.API`; действие `android.fbreader.action.API`
 *   на этом устройстве не резолвится вовсе, поэтому пробовать его не нужно;
 * * интерфейс `org.geometerplus.android.fbreader.api.ApiInterface` объявляет три метода, и нужный
 *   нам — первый (`request`), то есть транзакция с кодом 1;
 * * аргументы передаются как `ApiObject[]`; для всех нужных нам кодов аргументов нет, поэтому в
 *   посылку пишется «массив отсутствует» (`-1`), и сервис это принимает;
 * * ответ: `success` (1 — успех), затем тип значения (1 — целое, 2 — строка, 10 — позиция),
 *   затем само значение.
 *
 * Классы `ApiObject` и AIDL-файлы намеренно не вендорятся: транспорт пишет и читает `Parcel`
 * напрямую, потому что нужны только перечисленные выше типы. Если читалка сменит формат, ответ
 * разберётся как [FbReaderValue.Unsupported], и синхронизация честно останется без данных —
 * вместо того чтобы записать книге выдуманный прогресс.
 *
 * Сессия открывается на время синхронизации ([connect]/[disconnect]): держать привязку постоянно
 * нельзя — это мешало бы системе выгружать чужое приложение, а нам ничего не даёт.
 */
class BinderFbReaderTransport(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : FbReaderTransport {

    private var binder: IBinder? = null

    private var connection: ServiceConnection? = null

    override suspend fun connect(): Boolean = withContext(dispatchers.io) {
        if (isConnected()) return@withContext true
        val connected = CompletableDeferred<IBinder?>()
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                connected.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }
        }
        connection = serviceConnection
        val requested = runCatching {
            context.bindService(
                Intent(ACTION_API).setPackage(FBREADER_PACKAGE),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!requested) {
            // Читалки нет или сервис не exported: это не ошибка, а обычная деградация до L1.
            connection = null
            return@withContext false
        }
        // Таймаут привязки: сервис может не ответить вовсе — ждать его вечно нельзя, синхронизация
        // запускается из фона и обязана закончиться.
        val service = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { connected.await() }
        binder = service?.takeIf { it.isBinderAlive }
        if (binder == null) {
            release()
            return@withContext false
        }
        Log.i(TAG, "API читалки подключён: ${binder?.interfaceDescriptor}")
        true
    }

    override suspend fun disconnect() = withContext(dispatchers.io) { release() }

    override fun isConnected(): Boolean = binder?.isBinderAlive == true

    override suspend fun request(code: Int): FbReaderValue? = withContext(dispatchers.io) {
        val service = binder?.takeIf { it.isBinderAlive } ?: return@withContext null
        runCatching { readValue(service, code) }
            .onFailure { error ->
                // Отвалившаяся привязка — не повод падать: следующая синхронизация подключится заново.
                Log.i(TAG, "запрос $code не выполнен: ${error.javaClass.simpleName}: ${error.message}")
                release()
            }
            .getOrNull()
    }

    /** Посылка и разбор ответа. Формат — по фактам спайка, см. KDoc класса. */
    private fun readValue(service: IBinder, code: Int): FbReaderValue {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(INTERFACE)
            data.writeInt(code)
            data.writeInt(NO_ARRAY)
            service.transact(TRANSACTION_REQUEST, data, reply, TRANSACTION_FLAGS)
            reply.readException()
            val success = reply.readInt()
            if (success != SUCCESS) return FbReaderValue.Unsupported
            return when (val type = reply.readInt()) {
                TYPE_INT -> FbReaderValue.Number(reply.readInt())
                TYPE_STRING -> FbReaderValue.Text(reply.readString())
                TYPE_POSITION -> FbReaderValue.Position(
                    paragraphIndex = reply.readInt(),
                    elementIndex = reply.readInt(),
                )

                else -> {
                    Log.i(TAG, "код $code вернул неизвестный тип $type")
                    FbReaderValue.Unsupported
                }
            }
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun release() {
        val serviceConnection = connection ?: return
        connection = null
        binder = null
        runCatching { context.unbindService(serviceConnection) }
    }

    private companion object {
        const val TAG = "BookShelf"

        /** Действие API-сервиса FBReader: проверено на устройстве (второе действие не резолвится). */
        const val ACTION_API = "com.fbreader.action.API"

        /** Пакет FBReader: и Premium, и бесплатная версия используют одно имя пакета. */
        const val FBREADER_PACKAGE = "com.fbreader"

        /** Дескриптор AIDL из пакета FBReader. */
        const val INTERFACE = "org.geometerplus.android.fbreader.api.ApiInterface"

        /** Первый (и единственный нужный) метод интерфейса — `request`. */
        const val TRANSACTION_REQUEST = 1
        const val TRANSACTION_FLAGS = 0

        /** «Массив аргументов отсутствует»: нужные коды аргументов не требуют. */
        const val NO_ARRAY = -1

        const val CONNECT_TIMEOUT_MILLIS = 3_000L

        const val SUCCESS = 1
        const val TYPE_INT = 1
        const val TYPE_STRING = 2
        const val TYPE_POSITION = 10
    }
}
