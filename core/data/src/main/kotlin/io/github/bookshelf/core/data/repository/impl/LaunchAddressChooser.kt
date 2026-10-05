package io.github.bookshelf.core.data.repository.impl

import android.net.Uri
import io.github.bookshelf.core.common.log.APP_LOG_TAG
import io.github.bookshelf.core.common.log.AppLogger
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import io.github.bookshelf.core.readerlink.profile.needsExportedCopy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Каким адресом отдать книгу читалке.
 *
 * Адрес выбирается **до** запуска, и попытка одна: повторить запуск нельзя — когда читалка уже на
 * переднем плане, Android запрещает старт её activity из фона (`result code=102`, `BAL_BLOCK`;
 * проверено на устройстве 05.10.2026). Поэтому «запустить, проверить, при неудаче откатиться» не
 * работает в принципе, и решение принимается здесь.
 *
 * * FBReader ([needsExportedCopy]) получает копию книги: нашу ссылку SAF он принимает, но книгу по
 *   ней не открывает — остаётся на прежней (дефект Александра 05.10.2026: «открываю предыстория
 *   книга 1, открывается в фбридере вселенная неудачников»). Копию читалка читает через наш
 *   `FileProvider`: имя с расширением, грант на чтение.
 * * остальные читалки получают `content://` от SAF, как раньше;
 * * читалке, которая ссылок не принимает, тоже нужна копия.
 *
 * `null` в ответе и означает «копия книги»: её готовит `ReaderExport`.
 */
@Singleton
class LaunchAddressChooser @Inject constructor(
    private val logger: AppLogger,
) {

    fun addressFor(profile: ReaderProfile, book: BookEntity): Uri? {
        val address = when {
            profile.needsExportedCopy() -> null
            profile.supportsContentUri -> Uri.parse(book.documentUri)
            else -> null
        }
        // Строка нужна для разбора: снаружи «открылась не та книга» и «книга не открылась» выглядят
        // одинаково, а разница — в адресе.
        logger.d(
            APP_LOG_TAG,
            "запуск «${book.title}» (${book.extension}) в ${profile.packageName}: " +
                "адрес ${address ?: "копия книги"}",
        )
        return address
    }
}
