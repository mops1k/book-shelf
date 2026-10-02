package io.github.bookshelf.feature.onboarding

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.designsystem.text.AppText
import org.junit.Test

/**
 * Отказ онбординга доходит до экрана баннером (дефект 01.10.2026).
 *
 * Поле `error` заполнялось, но экран его не читал: человек не видел, что добавление папки или выбор
 * читалки не удались. Контракт «состояние → баннер» теперь назван ([OnboardingUiState.errorBanner]) и
 * проверяется здесь; сама отрисовка — забота Composable, в которой решать нечего.
 */
class OnboardingErrorBannerTest {

    @Test
    fun `a refusal is offered to the screen as a banner`() {
        val state = OnboardingUiState(
            error = AppText(
                R.string.onboarding_error_add_source,
                AppText(io.github.bookshelf.core.designsystem.R.string.error_io),
            ),
        )

        assertThat(state.errorBanner).isEqualTo(state.error)
    }

    @Test
    fun `no refusal means no banner`() {
        assertThat(OnboardingUiState().errorBanner).isNull()
    }
}
