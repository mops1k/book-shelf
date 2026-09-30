package io.github.bookshelf.feature.catalogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Текст для человека: ресурс и его аргументы.
 *
 * Нужен там, где текст рождается вне Composable — в чистой функции или в модели состояния, — а
 * показать его обязан экран: хранить готовую строку нельзя (она не переводится, ru/en), а ресурс
 * без аргументов не выразит «Загрузка идёт: 45 %». Аргументов не больше двух: этого хватает всем
 * подписям каталога, а список заставил бы раскрывать массив (`SpreadOperator` в detekt).
 */
data class CatalogsText(val res: Int, val arg1: Any? = null, val arg2: Any? = null)

/** Показать текст: `stringResource` с нужным числом аргументов. */
@Composable
fun CatalogsText.resolve(): String = when {
    arg1 == null -> stringResource(res)
    arg2 == null -> stringResource(res, arg1)
    else -> stringResource(res, arg1, arg2)
}
