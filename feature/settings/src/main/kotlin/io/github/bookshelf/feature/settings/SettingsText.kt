package io.github.bookshelf.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Текст для человека: ресурс и его аргументы.
 *
 * Нужен там, где текст рождается вне Composable — в модели состояния или в чистой функции, — а
 * показать его обязан экран. Хранить готовую строку нельзя: она не переводится (ru/en), а ресурс
 * без аргументов не выразит «освобождено 12,4 МБ». Аргументов не больше двух — этого хватает всем
 * подписям настроек, а список заставил бы раскрывать массив (`SpreadOperator` в detekt).
 */
internal data class SettingsText(val res: Int, val arg1: Any? = null, val arg2: Any? = null)

/** Показать текст: `stringResource` с нужным числом аргументов. */
@Composable
internal fun SettingsText.resolve(): String = when {
    arg1 == null -> stringResource(res)
    arg2 == null -> stringResource(res, arg1)
    else -> stringResource(res, arg1, arg2)
}
