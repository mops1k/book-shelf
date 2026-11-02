package io.github.bookshelf.core.update.checker

import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.datastore.update.UpdateStore
import io.github.bookshelf.core.update.model.AppFlavor
import io.github.bookshelf.core.update.model.AppVersion
import io.github.bookshelf.core.update.source.AssetPicker
import io.github.bookshelf.core.update.source.ReleaseSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Когда и как проверять обновления (ADR-018).
 *
 * Правила собраны в одном месте: выключенная настройка и flavor Play не делают запрос вовсе;
 * автоматическая проверка не чаще раза в сутки, ручная (`force`) — всегда; после любой попытки
 * время запоминается, чтобы неудачная проверка не повторялась на каждый запуск.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val source: ReleaseSource,
    private val store: UpdateStore,
    private val clock: AppClock,
) {

    /**
     * @param currentVersion версия установленного приложения (`BuildConfig.VERSION_NAME`).
     * @param force `true` — ручная проверка из настроек: троттлинг игнорируется.
     */
    suspend fun check(
        currentVersion: AppVersion,
        flavor: AppFlavor,
        enabled: Boolean,
        force: Boolean,
    ): UpdateCheck {
        if (!enabled || flavor == AppFlavor.PLAY) return UpdateCheck.Disabled
        if (!force && isThrottled()) return UpdateCheck.Throttled

        return try {
            val release = source.latest() ?: return UpdateCheck.None
            // Время запоминаем только после успешного ответа: см. комментарий в isThrottled.
            store.markChecked(clock.nowMillis())
            if (release.version > currentVersion) {
                UpdateCheck.Available(release = release, asset = AssetPicker.pick(release.assets, flavor))
            } else {
                UpdateCheck.None
            }
        } catch (error: IOException) {
            store.markChecked(clock.nowMillis())
            UpdateCheck.Failed(reason = error.message.orEmpty())
        }
    }

    /** Проверяли меньше суток назад? Тогда автоматическую проверку пропускаем. */
    private suspend fun isThrottled(): Boolean {
        val last = store.lastCheckEpochMs() ?: return false
        return clock.nowMillis() - last < THROTTLE_MS
    }

    private companion object {
        /** Сутки между автоматическими проверками. */
        const val THROTTLE_MS: Long = 24L * 60 * 60 * 1000
    }
}
