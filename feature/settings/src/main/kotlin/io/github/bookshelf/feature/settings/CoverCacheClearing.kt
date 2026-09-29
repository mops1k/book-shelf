package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.covers.CoverCache
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
internal suspend fun clearCoverCacheWithReport(cache: CoverCache): String {
    val before = cache.sizeBytes()
    return try {
        cache.clear()
        "Кэш обложек очищен, освобождено ${freedBytesLabel(before - cache.sizeBytes())}"
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        "Не удалось очистить кэш обложек: " + (io.message ?: "ошибка ввода-вывода")
    }
}
