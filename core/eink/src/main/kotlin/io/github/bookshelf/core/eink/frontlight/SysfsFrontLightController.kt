package io.github.bookshelf.core.eink.frontlight

import java.io.File
import kotlin.math.roundToInt

/**
 * Подсветка через sysfs — то, что приложение может сделать само, без вендорского SDK.
 *
 * Уровень задаётся долей от `max_brightness` рядом с узлом: у разных прошивок максимум разный
 * (100, 255, 2047), и писать «проценты» напрямую нельзя.
 *
 * ЧТО ИЗМЕРЕНО НА BIGME B6 COLOR (01.10.2026, Android 14, ядро 4.19.191, проверено
 * инструментальным тестом от лица приложения): каталога `/sys/class/backlight` НЕТ вовсе, а
 * `/sys/class/leds` существует, но приложению не читается (SELinux) — то есть на этом устройстве
 * sysfs-подсветка недоступна, и виджет подсветки честно не показывается. На устройствах, где узел
 * открыт на запись, реализация работает без всяких SDK.
 *
 * Тёплая составляющая через sysfs не делается: отдельного узла у типовых прошивок нет, поэтому
 * [setWarmth] остаётся значением по умолчанию (no-op) из интерфейса.
 *
 * @param candidates узлы `brightness`, которые стоит попробовать, по порядку.
 */
class SysfsFrontLightController(
    private val candidates: List<File> = defaultCandidates(),
) : FrontLightController {

    override fun supportsFrontLight(): Boolean = usableNode() != null

    override fun setLevel(percent: Int) {
        val brightness = usableNode() ?: return
        val clamped = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        val raw = (clamped.toDouble() * maximumOf(brightness) / MAX_PERCENT).roundToInt()
        runCatching { brightness.writeText(raw.toString()) }
    }

    override fun level(): Int? {
        val brightness = usableNode() ?: return null
        val raw = runCatching { brightness.readText().trim().toInt() }.getOrNull() ?: return null
        val maximum = maximumOf(brightness)
        if (maximum <= 0) return null
        return (raw.toDouble() * MAX_PERCENT / maximum).roundToInt().coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    /** Первый узел, который существует и открыт и на чтение, и на запись. */
    private fun usableNode(): File? =
        candidates.firstOrNull { node -> node.isFile && node.canRead() && node.canWrite() }

    /**
     * Максимум узла: рядом лежит `max_brightness`.
     *
     * Если файла нет или он битый, значение считается уже процентным (100): так ведут себя
     * прошивки, которые пишут в узел проценты.
     */
    private fun maximumOf(brightness: File): Int {
        val stored = runCatching {
            File(brightness.parentFile, MAX_BRIGHTNESS_FILE_NAME).readText().trim().toInt()
        }.getOrNull()
        return if (stored != null && stored > 0) stored else MAX_PERCENT
    }

    private companion object {
        const val MIN_PERCENT = 0
        const val MAX_PERCENT = 100
        const val BRIGHTNESS_FILE_NAME = "brightness"
        const val MAX_BRIGHTNESS_FILE_NAME = "max_brightness"

        /**
         * Известные места подсветки: стандартный backlight и подсветка через LED.
         *
         * Каталог может быть закрыт для чтения (на Bigme B6 закрыт) — тогда список просто пуст,
         * и приложение честно считает, что подсветки нет.
         */
        fun defaultCandidates(): List<File> = KNOWN_DIRECTORIES.flatMap { path ->
            val dir = File(path)
            val listed = dir.listFiles().orEmpty()
                .filter { it.name == BRIGHTNESS_FILE_NAME }
            listed + File(dir, BRIGHTNESS_FILE_NAME)
        }

        val KNOWN_DIRECTORIES = listOf(
            "/sys/class/backlight",
            "/sys/class/leds/lcd-backlight",
            "/sys/class/leds/frontlight",
            "/sys/class/leds/white",
        )
    }
}
