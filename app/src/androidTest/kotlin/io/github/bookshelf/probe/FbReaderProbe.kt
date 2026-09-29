package io.github.bookshelf.probe

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context.RECEIVER_EXPORTED
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

/**
 * СПАЙК (одноразовый код, в `main` не идёт): что реально отдают внешние API FBReader на живом
 * устройстве. Обычные юнит-тесты этого не проверяют: тут нужна привязка к чужому сервису и запросы
 * к чужому ContentProvider. Тест ничего не утверждает — он пишет в журнал то, что нашёл.
 *
 * Запуск на устройстве:
 * `adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk`
 * `adb install -r app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk`
 * `adb shell am instrument -w -e class io.github.bookshelf.probe.FbReaderProbe io.github.bookshelf.debug.test/androidx.test.runner.AndroidJUnitRunner`
 */
@RunWith(AndroidJUnit4::class)
class FbReaderProbe {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun probe_api_service_binding() {
        for (action in listOf("com.fbreader.action.API", "android.fbreader.action.API")) {
            val intent = Intent(action).setPackage(FBREADER_PACKAGE)
            val latch = CountDownLatch(1)
            var binder: IBinder? = null
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    binder = service
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }
            val bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
                .getOrDefault(false)
            val connected = latch.await(5, TimeUnit.SECONDS)
            log("API action=$action bindService=$bound connected=$connected resolver=${intent.resolveActivity(context.packageManager)}")
            val service = binder
            if (service != null) {
                log("API action=$action descriptor=${service.interfaceDescriptor} alive=${service.isBinderAlive}")
                log("API action=$action title=${requestText(service, GET_BOOK_TITLE_CODE)}")
                log("API action=$action path=${requestText(service, GET_BOOK_FILE_PATH_CODE)}")
                log("API action=$action language=${requestText(service, GET_BOOK_LANGUAGE_CODE)}")
                log("API action=$action paragraphs=${requestInt(service, GET_PARAGRAPHS_NUMBER_CODE)}")
                log("API action=$action pageStart=${requestPosition(service, GET_PAGE_START_CODE)}")
                log("API action=$action pageEnd=${requestPosition(service, GET_PAGE_END_CODE)}")
            }
            runCatching { context.unbindService(connection) }
        }
    }

    /** Ответ-строка: `request(505/502/501)`. Формат: успех, тип значения, строка. */
    private fun requestText(service: IBinder, code: Int): String = request(service, code) { reply ->
        val success = reply.readInt()
        val type = reply.readInt()
        "success=$success type=$type value=${reply.readString()}"
    }

    /** Ответ-целое: `request(601)`. */
    private fun requestInt(service: IBinder, code: Int): String = request(service, code) { reply ->
        val success = reply.readInt()
        val type = reply.readInt()
        "success=$success type=$type value=${reply.readInt()}"
    }

    /** Ответ-позиция: `request(701/702)` — два числа (параграф и элемент). */
    private fun requestPosition(service: IBinder, code: Int): String = request(service, code) { reply ->
        val success = reply.readInt()
        val type = reply.readInt()
        "success=$success type=$type value=${reply.readInt()}:${reply.readInt()}"
    }

    /** Общая часть всех запросов: интерфейс, код, `null` вместо массива аргументов. */
    private fun request(service: IBinder, code: Int, read: (Parcel) -> String): String = runCatching {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("org.geometerplus.android.fbreader.api.ApiInterface")
            data.writeInt(code)
            data.writeInt(-1)
            service.transact(TRANSACTION_request, data, reply, 0)
            reply.readException()
            read(reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrElse { error -> "ошибка: ${error.javaClass.simpleName}: ${error.message}" }

    /**
     * Сырой вызов `request(int code, ApiObject[] args)` с `args = null`.
     *
     * Классы `ApiObject` мы пока не вендорили, поэтому передаём пустой массив: коды, которым
     * аргументы не нужны (название, путь, позиция), обязаны ответить и так. Возвращаем первые
     * четыре байта ответа — этого достаточно, чтобы понять, отвечает ли сервис и какого типа
     * значение вернул.
     */
    private fun rawRequest(service: IBinder, code: Int): String = runCatching {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("org.geometerplus.android.fbreader.api.ApiInterface")
            data.writeInt(code)
            data.writeInt(-1) // null вместо ApiObject[]
            val sent = service.transact(TRANSACTION_request, data, reply, 0)
            reply.readException()
            val head = List(4) { if (reply.dataAvail() > 0) reply.readInt() else -1 }
            "sent=$sent head=$head bytes=${reply.dataSize()}"
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrElse { error -> "ошибка: ${error.javaClass.simpleName}: ${error.message}" }

    /**
     * Приходят ли события читалки чужому приложению.
     *
     * Если приходят — синхронизация может быть по событию, а не по таймеру: читалка сама сообщает,
     * что позиция изменилась. Тест ждёт полминуты; в это время книгу листают снаружи
     * (`adb shell input keyevent KEYCODE_DPAD_RIGHT`).
     */
    @Test
    fun probe_api_callback() {
        val received = mutableListOf<String>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val extras = intent?.extras?.keySet()?.joinToString(",") { key ->
                    "$key=${intent.extras?.get(key)}"
                }
                val line = "action=${intent?.action} extras=$extras"
                received += line
                log("CALLBACK $line")
            }
        }
        for (action in listOf("com.fbreader.action.API_CALLBACK", "android.fbreader.action.API_CALLBACK")) {
            val filter = IntentFilter(action)
            runCatching { context.registerReceiver(receiver, filter, RECEIVER_EXPORTED) }
                .onSuccess { log("CALLBACK подписка на $action оформлена") }
                .onFailure { error -> log("CALLBACK подписка на $action не удалась: ${error.message}") }
        }
        Thread.sleep(30_000)
        log("CALLBACK всего событий: ${received.size}")
        runCatching { context.unregisterReceiver(receiver) }
    }

    @Test
    fun probe_library_provider() {
        val candidates = listOf(
            "books_count", "bookmarks_count", "books", "book", "book_by_uri", "book_by_hash",
            "book_by_id", "book_by_file", "all_hashes", "book_hashes", "covers", "authors", "tags",
            "series", "bookmarks", "currentbook", "search", "has_books_for_query", "can_remove_book",
        )
        for (relative in candidates) {
            val result = query(Uri.parse("content://$PROVIDER_AUTHORITY/$relative"))
            log("PROVIDER /$relative → $result")
        }

        // Ключи с аргументами: адрес книги в их собственном виде и наш SAF-адрес.
        val ourDocumentUri = firstBookDocumentUri()
        log("PROVIDER наш documentUri = $ourDocumentUri")
        for (value in listOfNotNull(ourDocumentUri, "metro", "1")) {
            val withArgs = query(
                Uri.parse("content://$PROVIDER_AUTHORITY/book_by_uri"),
                selectionArgs = arrayOf(value),
            )
            log("PROVIDER book_by_uri(args=$value) → $withArgs")
            val hasBooks = query(
                Uri.parse("content://$PROVIDER_AUTHORITY/has_books_for_query"),
                selectionArgs = arrayOf(value),
            )
            log("PROVIDER has_books_for_query(args=$value) → $hasBooks")
            val canRemove = query(
                Uri.parse("content://$PROVIDER_AUTHORITY/can_remove_book"),
                selectionArgs = arrayOf(value),
            )
            log("PROVIDER can_remove_book(args=$value) → $canRemove")
        }
    }

    /** Запрос к провайдеру: печатаем колонки и первые строки либо текст ошибки. */
    private fun query(uri: Uri, selectionArgs: Array<String>? = null): String = runCatching {
        val projection = null
        context.contentResolver.query(uri, projection, null, selectionArgs, null)?.use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = mutableListOf<List<String?>>()
            while (cursor.moveToNext() && rows.size < 5) {
                rows += columns.indices.map { index -> cursor.getString(index) }
            }
            "колонки=$columns строк=${cursor.count} данные=$rows"
        } ?: "null-курсор"
    }.getOrElse { error -> "ошибка: ${error.javaClass.simpleName}: ${error.message}" }

    /** Адрес первой книги из нашей базы — чтобы было что подставить в запрос провайдера. */
    private fun firstBookDocumentUri(): String? = runCatching {
        android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath("bookshelf.db").path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery("SELECT documentUri FROM books LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
    }.getOrNull()

    private fun log(message: String) = Log.i(TAG, message)

    private companion object {
        const val TAG = "BookShelfProbe"
        const val FBREADER_PACKAGE = "com.fbreader"
        const val PROVIDER_AUTHORITY = "com.fbreader.library"
        const val TRANSACTION_request = 1
        const val GET_BOOK_TITLE_CODE = 502
        const val GET_BOOK_FILE_PATH_CODE = 505
        const val GET_BOOK_LANGUAGE_CODE = 501
        const val GET_PARAGRAPHS_NUMBER_CODE = 601
        const val GET_PAGE_START_CODE = 701
        const val GET_PAGE_END_CODE = 702
    }
}
