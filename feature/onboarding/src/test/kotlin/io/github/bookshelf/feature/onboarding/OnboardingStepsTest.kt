package io.github.bookshelf.feature.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тесты чистой логики шагов онбординга: без Compose, Hilt и устройства.
 */
class OnboardingStepsTest {

    @Test
    fun `next moves to the following step`() {
        assertThat(OnboardingStep.WELCOME.next()).isEqualTo(OnboardingStep.SOURCES)
        assertThat(OnboardingStep.SOURCES.next()).isEqualTo(OnboardingStep.READERS)
    }

    @Test
    fun `next stays on the last step`() {
        assertThat(OnboardingStep.READERS.next()).isEqualTo(OnboardingStep.READERS)
    }

    @Test
    fun `previous moves to the preceding step`() {
        assertThat(OnboardingStep.READERS.previous()).isEqualTo(OnboardingStep.SOURCES)
        assertThat(OnboardingStep.SOURCES.previous()).isEqualTo(OnboardingStep.WELCOME)
    }

    @Test
    fun `previous stays on the first step`() {
        assertThat(OnboardingStep.WELCOME.previous()).isEqualTo(OnboardingStep.WELCOME)
    }

    @Test
    fun `library can be assembled only with at least one source`() {
        assertThat(canFinishOnboarding(emptyList())).isFalse()

        val sources = listOf(
            SourceListItem(id = "source-1", displayName = "Books", uri = "content://books"),
        )
        assertThat(canFinishOnboarding(sources)).isTrue()
    }
}
