package io.github.bookshelf.core.designsystem.component

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.bookshelf.core.designsystem.theme.EinkColors
import io.github.bookshelf.core.designsystem.theme.EinkDimens
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.core.designsystem.theme.LocalInvertCovers

/** Пропорция книжной обложки — общая константа, чтобы сетка не «плясала» между экранами. */
const val COVER_ASPECT_RATIO = 0.66f

/**
 * Обложка книги.
 *
 * Особенности под e-ink:
 * * нет crossfade и анимаций placeholder;
 * * без файла рисуется рамка с названием — читаемо и не «шумит» (если не задан [formatBadge]);
 * * [inverted] и настройка `LocalInvertCovers` инвертируют изображение при отрисовке, не создавая
 *   второй файл в кэше («Архитектура» §5).
 *
 * @param formatBadge формат книги («FB2», «PDF») в правом верхнем углу обложки. Для плиток полки
 *   он заменяет название внутри заглушки: название и так стоит строкой ниже, а на самой обложке
 *   полезнее формат.
 * @param statusBadge статус чтения («не прочитано», «читаю 42%») полосой внизу обложки. Полоса
 *   живёт **внутри** обложки, а не отдельной строкой под плиткой: на e-ink каждая строка стоит
 *   высоты экрана, и внутри обложки статус не отнимает места у следующего ряда книг.
 */
@Composable
fun BookCover(
    coverPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    contentDescription: String? = null,
    /**
     * Адрес обложки в сети (OPDS-фид).
     *
     * Раньше параметра не было, и записи каталога показывались без обложек: в фиде обложка
     * приходит ссылкой, а не файлом в кэше, и подставить её было некуда.
     */
    coverUrl: String? = null,
    formatBadge: String? = null,
    statusBadge: String? = null,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val context = LocalContext.current

    // Инверсия включается любым из двух путей: настройкой приложения (локал) и точечным
    // параметром [inverted]. Фильтр строится только при включённой инверсии: он завязан
    // на `android.graphics` и без надобности не нужен.
    val invert = inverted || LocalInvertCovers.current
    val invertFilter = remember(invert) {
        if (invert) ColorFilter.colorMatrix(ColorMatrix(invertedColorMatrix())) else null
    }
    val imageModel = rememberCoverModel(context = context, coverPath = coverPath, coverUrl = coverUrl)

    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .border(dimens.border, colors.divider)
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription ?: title,
                contentScale = ContentScale.Fit,
                colorFilter = invertFilter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Название по центру пустой обложки. Оно дублирует подпись под плиткой намеренно:
            // подпись в две строки обрезается многоточием, а здесь название видно целиком —
            // это и есть способ прочитать его у книги без обложки (просьба Александра 28.09.2026).
            // Отступы сверху и снизу разводят название с плашкой формата и полосой статуса.
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = dimens.gutterSmall,
                    end = dimens.gutterSmall,
                    top = dimens.gutterSmall * 2,
                    bottom = dimens.gutterSmall * 3,
                ),
            )
        }

        if (formatBadge != null) {
            CoverBadge(text = formatBadge, modifier = Modifier.align(Alignment.TopEnd))
        }

        if (statusBadge != null) {
            CoverStatusBadge(text = statusBadge, colors = colors, dimens = dimens)
        }
    }
}

/** Компактная плашка на обложке: формат книги. Размер по подписи, а не во всю ширину. */
@Composable
private fun CoverBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(colors.background)
            .border(dimens.border, colors.divider)
            .padding(horizontal = dimens.gutterSmall / 3, vertical = dimens.gutterSmall / 6),
    )
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

/**
 * Матрица инверсии: цвета обложки становятся негативом.
 *
 * Функция чистая и живёт отдельно от Composable намеренно: правило «настройка включена —
 * значит матрица именно такая» проверяется обычным JUnit без устройства («TDD» §7),
 * а [ColorFilter] собирается из неё уже при отрисовке.
 *
 * Диагональ `-1` инвертирует канал, а свободный член `255` возвращает диапазон 0..255 вместо
 * получающегося после умножения -255..0. Альфа (`1` в последней строке) не трогается: иначе
 * полупрозрачные PNG-обложки стали бы полностью прозрачными.
 */
internal fun invertedColorMatrix(): FloatArray = floatArrayOf(
    -1f, 0f, 0f, 0f, INVERT_CHANNEL_MAX,
    0f, -1f, 0f, 0f, INVERT_CHANNEL_MAX,
    0f, 0f, -1f, 0f, INVERT_CHANNEL_MAX,
    0f, 0f, 0f, 1f, 0f,
)

/**
 * Верхняя граница канала: после умножения на -1 цвет уходит в -255..0, и эту же границу надо
 * прибавить, чтобы вернуть его в привычный диапазон 0..255.
 */
private const val INVERT_CHANNEL_MAX = 255f


/**
 * Модель картинки для Coil: ссылка из фида или файл в кэше.
 *
 * Ключ кэша включает время изменения файла: обложка может быть перерисована под ТЕМ ЖЕ путём
 * (карточка книги → «Обновить из файла»), и тогда кэш отдал бы старую картинку — на устройстве
 * 28.09.2026 обложка PDF так и оставалась чёрной, хотя в файле уже лежала страница.
 *
 * Вынесено из `BookCover` отдельной функцией: та перевалила за предел длины detekt.
 */
@Composable
private fun rememberCoverModel(context: Context, coverPath: String?, coverUrl: String?): Any? {
    // Ключ кэша Coil по умолчанию — путь к файлу. Обложка может быть перерисована под ТЕМ ЖЕ
    // путём (карточка книги → «Обновить из файла»), и тогда кэш отдал бы старую картинку:
    // на устройстве 28.09.2026 обложка PDF так и оставалась чёрной на экране, хотя в файле уже
    // лежала страница. Поэтому в ключ входит время изменения файла — оно меняется при записи.
    val revision = coverPath?.let { path -> java.io.File(path).lastModified() } ?: 0L
    val imageModel: Any? = if (coverUrl != null) {
        coverUrl
    } else if (coverPath != null) {
        remember(coverPath, revision) {
            ImageRequest.Builder(context)
                .data(java.io.File(coverPath))
                .memoryCacheKey("$coverPath#$revision")
                .diskCacheKey("$coverPath#$revision")
                .build()
        }
    } else {
        null
    }
    return imageModel
}


/**
 * Плашка статуса чтения внизу обложки.
 *
 * Непрозрачная подложка обязательна: на e-ink полупрозрачность и тени запрещены, а по светлой
 * обложке светлый текст было бы не прочитать («E-ink UX» §5). Вынесено из `BookCover`
 * ради предела длины detekt.
 */
@Composable
private fun BoxScope.CoverStatusBadge(
    text: String,
    colors: EinkColors,
    dimens: EinkDimens,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.foreground,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // Плашка непрозрачная: на e-ink полупрозрачность и тени запрещены, а по светлой
            // обложке светлый текст было бы не прочитать («E-ink UX» §5).
            .background(colors.background)
            .border(dimens.border, colors.divider)
            .padding(vertical = dimens.gutterSmall / 4),
    )
}
