package io.github.bookshelf.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Заглушка читалки для инструментальных тестов запуска (только debug-сборка).
 *
 * Настоящая читалка (FBReader и подобные) в тестовом окружении недоступна, а проверить надо
 * именно доставку намерения: с каким URI, типом и правами приложение отдаёт книгу чужому
 * приложению. Заглушка объявлена в debug-манифесте в своём же пакете и принимает `ACTION_VIEW`
 * с книжными MIME-типами — как это делает читалка.
 */
class DebugReaderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        received = intent
        latch.countDown()
        finish()
    }

    companion object {
        /** Полное имя для профиля читалки в тесте. */
        const val NAME = "io.github.bookshelf.debug.DebugReaderActivity"

        /** Последнее полученное намерение: тест читает его после запуска. */
        @Volatile
        var received: Intent? = null

        private var latch = CountDownLatch(1)

        /** Сброс перед проверкой: намерение и ожидание относятся к одному запуску. */
        fun reset() {
            received = null
            latch = CountDownLatch(1)
        }

        /**
         * Дождаться намерения от заглушки.
         *
         * Запуск чужой activity асинхронный: `startActivity` возвращается раньше, чем она создана,
         * поэтому тест ждёт сигнал, а не «спит» (сон в тестах запрещён, «TDD» §2).
         */
        fun awaitIntent(timeoutMs: Long): Intent? {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            return received
        }
    }
}
