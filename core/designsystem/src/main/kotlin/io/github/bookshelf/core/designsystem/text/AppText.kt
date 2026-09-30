package io.github.bookshelf.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

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

/** Показать текст: `stringResource` с нужным числом аргументов. */
@Composable
fun AppText.resolve(): String = when {
    arg1 == null -> stringResource(res)
    arg2 == null -> stringResource(res, arg1)
    else -> stringResource(res, arg1, arg2)
}
