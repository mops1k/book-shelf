package io.github.bookshelf

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Запуск приложения на живом устройстве («TDD» §4).
 *
 * Юнит-тесты проверяют экраны по состояниям, но не собирают граф зависимостей Hilt, не открывают
 * настоящую Room-базу и не проходят путь «application → activity → первый кадр». Именно этот путь
 * ломался при обновлениях библиотек и миграциях, поэтому проверяется здесь.
 *
 * Первый экран зависит от данных человека (есть источники — полка, нет — онбординг), поэтому
 * проверка не привязана к конкретной подписи: приложение обязано дойти до первого кадра и
 * остаться живым.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun the_app_starts_and_renders_its_first_screen() {
        composeRule.waitForIdle()

        assertThat(composeRule.activityRule.scenario.state).isAtLeast(Lifecycle.State.RESUMED)
        composeRule.onRoot().assertExists()
    }
}
