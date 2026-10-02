package io.github.bookshelf.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.bookshelf.core.designsystem.R

/**
 * Имя папки-источника для экрана.
 *
 * Имя папки — данные (`sources.displayName`), и оно показывается как есть. Пустое имя означает, что
 * вывести его не из чего: в этом случае слова подставляет интерфейс. Так решено, чтобы в базе не
 * оставалось русского текста («Папка»), который не переводится в английском интерфейсе
 * («TDD» §8).
 */
@Composable
fun sourceNameOrFolder(name: String): String =
    name.ifBlank { stringResource(R.string.source_default_name) }
