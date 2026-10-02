package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.covers.CoverCache
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Очистить кэш обложек и вернуть текст результата для строки настроек.
 *
 * Вынесено из [SettingsViewModel] отдельной функцией, потому что это вся логика команды:
 * посчитать занятое до и после, очистить, честно сказать, сколько освободилось. Так она
 * проверяется с одним фейковым кэшем, а не с фейками всех шести репозиториев ViewModel.
 *
 * Отмена пробрасывается дальше: перехват вокруг suspend-вызова проглотил бы `CancellationException`
 * и превратил уход с экрана в «не удалось очистить».
 *
 * Ловится именно [IOException] — очистка кэша это работа с файлами; перехват шире скрыл бы и
 * ошибку программиста, а detekt считает такой перехват слишком широким справедливо.
 */
internal suspend fun clearCoverCacheWithReport(cache: CoverCache): AppText {
    val before = cache.sizeBytes()
    return try {
        cache.clear()
        // Объём — переводимая часть: число считает `freedBytesText`, единицу даёт ресурс («TDD» §5).
        AppText(R.string.settings_cover_cache_cleared, freedBytesText(before - cache.sizeBytes()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        // Причина — текст по типу ошибки: сообщение нижнего слоя человеку не показывают («TDD» §7).
        AppText(R.string.settings_cover_cache_failed, io.toAppError().text())
    }
}
