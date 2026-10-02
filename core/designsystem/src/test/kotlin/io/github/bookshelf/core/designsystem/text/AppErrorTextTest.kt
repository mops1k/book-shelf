package io.github.bookshelf.core.designsystem.text

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.designsystem.R
import org.junit.Test

/**
 * Текст ошибки для человека: по типу, а не по `AppError.message` («TDD» §7, решение 01.10.2026).
 *
 * Проверяется то, что видит человек в баннере: у каждого типа свой переводимый текст, техническое
 * пояснение нижнего слоя не показывается вовсе (иначе на английском интерфейсе всплывала бы русская
 * диагностика), а значимые данные — формат файла, имя неподдерживаемого случая, код ответа сервера —
 * попадают в текст как аргументы.
 */
class AppErrorTextTest {

    @Test
    fun `the technical message is never shown to the person`() {
        // В `message` нижний слой пишет диагностику: путь, имя поля, подробность отказа.
        val error = AppError.Io(message = "Не удалось прочитать /storage/1A2B-3C4D/Books/книга.fb2")

        assertThat(error.text()).isEqualTo(AppText(R.string.error_io))
    }

    @Test
    fun `every type has its own text`() {
        assertThat(AppError.NotFound(what = "source").text()).isEqualTo(AppText(R.string.error_not_found))
        assertThat(AppError.InvalidArgument(what = "name").text())
            .isEqualTo(AppText(R.string.error_invalid_argument))
        assertThat(AppError.PermissionDenied().text()).isEqualTo(AppText(R.string.error_permission_denied))
        assertThat(AppError.AccessLost.text()).isEqualTo(AppText(R.string.error_access_lost))
        assertThat(AppError.Io().text()).isEqualTo(AppText(R.string.error_io))
        assertThat(AppError.Unauthorized().text()).isEqualTo(AppText(R.string.error_unauthorized))
        assertThat(AppError.Unknown().text()).isEqualTo(AppText(R.string.error_unknown))
    }

    @Test
    fun `a parse error names the format`() {
        assertThat(AppError.Parse(format = "FB2").text())
            .isEqualTo(AppText(R.string.error_parse, "FB2"))
    }

    @Test
    fun `an unsupported case names what is unsupported`() {
        // «пока не поддерживается: CBR» говорит человеку больше, чем «ошибка»: он понимает, чего ждать.
        assertThat(AppError.Unsupported(what = "CBR").text())
            .isEqualTo(AppText(R.string.error_unsupported, "CBR"))
    }

    @Test
    fun `a network error shows the server code when there is one`() {
        assertThat(AppError.Network(httpCode = 503).text())
            .isEqualTo(AppText(R.string.error_network_code, 503))
        // Без кода отдельный текст: «ошибка сети (HTTP null)» читалась бы как поломка.
        assertThat(AppError.Network().text()).isEqualTo(AppText(R.string.error_network))
    }

    @Test
    fun `the types are told apart`() {
        val resources = listOf(
            AppError.NotFound("source"),
            AppError.InvalidArgument("name"),
            AppError.PermissionDenied(),
            AppError.AccessLost,
            AppError.Io(),
            AppError.Parse(format = "FB2"),
            AppError.Network(),
            AppError.Unauthorized(),
            AppError.Unsupported(what = "CBR"),
            AppError.Unknown(),
        ).map { error -> error.text().res }

        // Один и тот же текст на разные причины сделал бы баннер бесполезным.
        assertThat(resources).containsNoDuplicates()
    }
}
