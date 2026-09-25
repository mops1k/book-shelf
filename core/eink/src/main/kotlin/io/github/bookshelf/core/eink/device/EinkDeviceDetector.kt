package io.github.bookshelf.core.eink.device

import android.os.Build
import io.github.bookshelf.core.eink.vendor.EinkVendor

/**
 * Результат определения устройства.
 *
 * @param isEink распознано ли устройство как e-ink;
 * @param vendor вендор либо [EinkVendor.UNKNOWN];
 * @param model модель из `Build.MODEL` (для диагностики в «О приложении»);
 * @param androidSdk `Build.VERSION.SDK_INT` — от него зависит, доступны ли скрытые API вендора.
 */
data class EinkDeviceInfo(
    val isEink: Boolean,
    val vendor: EinkVendor,
    val model: String?,
    val androidSdk: Int,
)

/**
 * Определение вендора e-ink устройства.
 *
 * Интерфейс (а не `object`) — потому что в тестах и на «странных» прошивках подменяется:
 * реальная реализация читает системные свойства, тестовая отдаёт фиксированный [EinkDeviceInfo].
 */
interface EinkDeviceDetector {
    fun detect(): EinkDeviceInfo
}

/**
 * Чистое определение вендора по строковым признакам (docs/eink-ux.md §8).
 *
 * Признаки объединяются в одну строку в нижнем регистре и проверяются по таблице; порядок таблицы
 * задаёт приоритет. Значения [systemProperties] (`ro.product.model`, `ro.product.brand`, …) важны
 * потому, что часть прошивок не заполняет `Build.MANUFACTURER` осмысленно, но кладёт вендора в
 * системное свойство.
 *
 * Функция чистая: никаких обращений к Android — только строки, поэтому она полностью покрыта тестом.
 */
fun detectVendor(
    manufacturer: String?,
    brand: String?,
    model: String?,
    systemProperties: Map<String, String> = emptyMap(),
): EinkVendor {
    val haystack = buildString {
        append(manufacturer.orEmpty())
        append(' ')
        append(brand.orEmpty())
        append(' ')
        append(model.orEmpty())
        systemProperties.forEach { (key, value) ->
            append(' ')
            append(key)
            append('=')
            append(value)
        }
    }.lowercase()

    if (haystack.isBlank()) return EinkVendor.UNKNOWN

    return VENDOR_MARKERS
        .firstOrNull { (_, markers) -> markers.any { marker -> marker in haystack } }
        ?.first
        ?: EinkVendor.UNKNOWN
}

/**
 * Таблица «маркер → вендор» (docs/eink-ux.md §8.1–8.3).
 *
 * Порядок важен: первое совпадение выигрывает. `A2` и `rockchip` здесь сознательно отсутствуют —
 * это не бренд, а чип, и по нему нельзя надёжно выбрать контроллер.
 */
private val VENDOR_MARKERS: List<Pair<EinkVendor, List<String>>> = listOf(
    EinkVendor.ONYX to listOf("onyx", "boox"),
    EinkVendor.ROCKCHIP_BOYUE to listOf("meebook", "likebook", "boyue"),
    EinkVendor.HISENSE to listOf("hisense"),
    EinkVendor.TOLINO to listOf("tolino"),
    EinkVendor.BIGME to listOf("bigme"),
    EinkVendor.DASUNG to listOf("dasung"),
    EinkVendor.POCKETBOOK to listOf("pocketbook"),
    EinkVendor.IREADER to listOf("ireader"),
    EinkVendor.INKPALM to listOf("inkpalm"),
)

/**
 * Android-реализация детектора: `Build.MANUFACTURER`/`Build.BRAND`/`Build.MODEL` плюс системные
 * свойства.
 *
 * @param systemProperties системные свойства; по умолчанию читаются рефлексией через
 *   `android.os.SystemProperties`. Параметр существует, чтобы детектор можно было проверить
 *   без Robolectric-магии.
 */
class AndroidEinkDeviceDetector(
    private val systemProperties: Map<String, String> = readSystemProperties(),
) : EinkDeviceDetector {

    override fun detect(): EinkDeviceInfo {
        val vendor = detectVendor(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            systemProperties = systemProperties,
        )
        return EinkDeviceInfo(
            isEink = vendor != EinkVendor.UNKNOWN,
            vendor = vendor,
            model = Build.MODEL,
            androidSdk = Build.VERSION.SDK_INT,
        )
    }
}

/** Ключи, которые реально помогают определить вендора на прошивках e-ink. */
private val SYSTEM_PROPERTY_KEYS: List<String> = listOf(
    "ro.product.model",
    "ro.product.brand",
    "ro.product.manufacturer",
    "ro.build.product",
)

/**
 * Чтение системных свойств рефлексией.
 *
 * `android.os.SystemProperties` — скрытый API: он есть на всех версиях Android, но не входит в
 * публичный SDK, поэтому любая ошибка (в том числе на будущих версиях) глушится и превращается
 * в пустую карту. Приложение обязано работать и без этих свойств.
 *
 * STUB(Sprint 10): сверить поведение на целевых прошивках и, если свойств недостаточно,
 *  добавить разбор `ro.build.display.id` / `ro.product.device`.
 */
private fun readSystemProperties(): Map<String, String> {
    val systemPropertiesClass = runCatching { Class.forName("android.os.SystemProperties") }
        .getOrNull()
        ?: return emptyMap()
    val getMethod = runCatching { systemPropertiesClass.getMethod("get", String::class.java) }
        .getOrNull()
        ?: return emptyMap()

    return SYSTEM_PROPERTY_KEYS
        .mapNotNull { key ->
            val value = runCatching { getMethod.invoke(null, key) as? String }.getOrNull()
            key.takeIf { !value.isNullOrBlank() }?.let { it to value.orEmpty() }
        }
        .toMap()
}
