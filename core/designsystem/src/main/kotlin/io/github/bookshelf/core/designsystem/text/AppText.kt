package io.github.bookshelf.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.bookshelf.core.designsystem.R

/**
 * Текст для человека: ресурс и его аргументы.
 *
 * Нужен там, где текст рождается вне Composable — в ViewModel, в чистой функции или в модели
 * состояния, — а показать его обязан экран. Хранить готовую строку нельзя: она не переводится
 * (ru/en), а ресурс без аргументов не выразит «освобождено 12,4 МБ».
 *
 * Аргументов не больше двух: этого хватает всем подписям приложения, а список заставил бы
 * раскрывать массив на каждом вызове `stringResource` (`SpreadOperator` в detekt).
 */
data class AppText(val res: Int, val arg1: Any? = null, val arg2: Any? = null)

/**
 * Показать текст: `stringResource` с нужным числом аргументов.
 *
 * Аргументом может быть вложенный [AppText] — так собирается сообщение из двух переводимых частей
 * («Не удалось создать полку» + «нет доступа»), не превращаясь в склейку готовых строк.
 */
@Composable
fun AppText.resolve(): String {
    val args = arguments { nested -> nested.resolve() }
    return when (args.size) {
        0 -> stringResource(res)
        1 -> stringResource(res, args[0])
        else -> stringResource(res, args[0], args[1])
    }
}

/**
 * Аргументы для форматирования: вложенный текст заменяется его переводом.
 *
 * Пустые аргументы опускаются, порядок остальных сохраняется. `inline` — потому что разворачивание
 * вложенного текста само обращается к ресурсам (`stringResource` доступен только в Composable), а
 * чистая форма функции позволяет проверить это правило без Compose (см. `AppTextArgumentsTest`).
 */
internal inline fun AppText.arguments(resolveNested: (AppText) -> String): List<Any> = listOfNotNull(
    arg1?.let { value -> if (value is AppText) resolveNested(value) else value },
    arg2?.let { value -> if (value is AppText) resolveNested(value) else value },
)

/**
 * Показать сообщение из нескольких переводимых частей.
 *
 * Длинный итог («Оптимизировано книг: 2, сэкономлено 2,0 МБ, уже оптимальных: 1») собирается из
 * частей, потому что в нём бывает до пяти чисел, а [AppText] несёт не больше двух аргументов.
 * Разделитель — тоже ресурс: в другом языке между частями может стоять другое.
 */
@Composable
fun List<AppText>.resolveJoined(): String {
    val separator = stringResource(R.string.app_text_parts_separator)
    return joined(separator = separator) { part -> part.resolve() }
}

/**
 * Части сообщения одной строкой.
 *
 * `inline` — потому что часть переводится через ресурсы (`stringResource` доступен только в
 * Composable), а чистая форма позволяет проверить порядок частей и разделитель без Compose
 * (см. `AppTextListTest`).
 */
internal inline fun List<AppText>.joined(
    separator: String,
    resolvePart: (AppText) -> String,
): String {
    val result = StringBuilder()
    forEachIndexed { index, part ->
        if (index > 0) result.append(separator)
        result.append(resolvePart(part))
    }
    return result.toString()
}
