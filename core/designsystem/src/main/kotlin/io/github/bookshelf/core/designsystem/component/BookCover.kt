package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens

/** Пропорция книжной обложки — общая константа, чтобы сетка не «плясала» между экранами. */
const val COVER_ASPECT_RATIO = 0.66f

/**
 * Обложка книги.
 *
 * Особенности под e-ink:
 * * нет crossfade и анимаций placeholder;
 * * без файла рисуется рамка с названием — читаемо и не «шумит»;
 * * [inverted] инвертирует изображение при отрисовке, не создавая второй файл в кэше
 *   (docs/architecture.md §5).
 */
@Composable
fun BookCover(
    coverPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    contentDescription: String? = null,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .border(dimens.border, colors.divider)
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = java.io.File(coverPath),
                contentDescription = contentDescription ?: title,
                contentScale = ContentScale.Fit,
                colorFilter = if (inverted) INVERT_FILTER else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(dimens.gutterSmall),
            )
        }
    }
}

/** Заглушка обложки: нужна, пока размер ячейки ещё не вычислен (первый кадр экрана). */
@Composable
fun BookCoverPlaceholder(modifier: Modifier = Modifier) {
    val colors = LocalEinkColors.current
    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .background(colors.background)
            .border(LocalEinkDimens.current.border, colors.divider),
    )
}

/** Инверсия через цветовую матрицу: один и тот же файл годится и для тёмной темы. */
private val INVERT_FILTER: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)
