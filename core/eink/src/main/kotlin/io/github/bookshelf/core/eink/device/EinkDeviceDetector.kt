package io.github.bookshelf.core.eink.device

import android.os.Build
import io.github.bookshelf.core.eink.vendor.EinkVendor

/**
 * Результат определения устройства.
 *
 * @param isEink распознано ли устройство как e-ink;
 * @param vendor вендор либо [EinkVendor.UNKNOWN];
 * @param screen тип экрана: обычный, бумага чёрно-белая или цветная;
 * @param model модель из `Build.MODEL` (для диагностики в «О приложении»);
 * @param androidSdk `Build.VERSION.SDK_INT` — от него зависит, доступны ли скрытые API вендора.
 */
data class EinkDeviceInfo(
    val isEink: Boolean,
    val vendor: EinkVendor,
    val screen: EinkScreen,
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
 * Определить тип экрана по тем же строковым признакам.
 *
 * Порядок решений важен и отражает то, что мы действительно знаем:
 * 1. нашли признак **цвета** — это цветная бумага (единственный признак, который в прошивках есть);
 * 2. вендор опознан, признака цвета нет — чёрно-белая бумага (самый частый случай, см. [EinkScreen]);
 * 3. вендор не опознан, но есть вендорские признаки бумаги — честное «похоже на бумагу»;
 * 4. ничего — обычный экран.
 *
 * Функция чистая: только строки, поэтому проверяется тестом без Robolectric.
 */
fun detectScreen(
    vendor: EinkVendor,
    manufacturer: String?,
    brand: String?,
    model: String?,
    systemProperties: Map<String, String> = emptyMap(),
): EinkScreen {
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

    return when {
        COLOUR_MARKERS.any { marker -> marker in haystack } -> EinkScreen.EINK_COLOR
        vendor != EinkVendor.UNKNOWN -> EinkScreen.EINK_MONOCHROME
        PAPER_MARKERS.any { marker -> marker in haystack } -> EinkScreen.EINK_UNKNOWN
        else -> EinkScreen.NORMAL
    }
}

/**
 * Признаки цветной бумаги.
 *
 * Только проверяемые, а не «слово color в любом месте»: на этом же устройстве есть свойство
 * `persist.vendor.sys.pq.mdp.color.dbg` про обработку цвета в дисплее, и поиск подстроки «color»
 * без границы слова объявил бы цветным любой аппарат с такой прошивкой.
 *
 * * `productcode=color` — Bigme B6 Color (`ro.build.ProductCode=COLOR`, проверено на устройстве);
 * * ` color` — цвет прямо назван в имени модели или устройства («B6 Color»);
 * * `colour` — то же в британском написании;
 * * `kaleido` — название самой технологии цветной бумаги, встречается у Onyx и других.
 */
private val COLOUR_MARKERS: List<String> = listOf("productcode=color", " color", "colour", "kaleido")

/**
 * Признаки бумаги без имени вендора.
 *
 * Нужны, чтобы не назвать обычным экраном незнакомую читалку: у Bigme (и родственных прошивок)
 * вендорский стек помечен `vendor.xrz.*`, а `eink`/`epd` прямо говорят о технологии.
 */
private val PAPER_MARKERS: List<String> = listOf("vendor.xrz", "eink", "epd")

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
        val screen = detectScreen(
            vendor = vendor,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            systemProperties = systemProperties,
        )
        return EinkDeviceInfo(
            isEink = screen != EinkScreen.NORMAL,
            vendor = vendor,
            screen = screen,
            model = Build.MODEL,
            androidSdk = Build.VERSION.SDK_INT,
        )
    }
}

/**
 * Ключи, которые реально помогают определить вендора и экран.
 *
 * Список собран по фактам с живого устройства (Bigme B6 Color) и по известным прошивкам:
 * `ro.product.manufacturer`/`brand` дают вендора, `ro.build.fingerprint` — вендора там, где
 * производитель заполнен как попало, `ro.build.ProductCode` и имя Bluetooth-устройства — цвет,
 * `persist.sys.hctmodel` — вендорский стек Bigme. Партиционные варианты (`ro.product.system.*`
 * и другие) добавлены потому, что на части прошивок заполнены только они, а `ro.product.model`
 * пуст.
 */
private val SYSTEM_PROPERTY_KEYS: List<String> = listOf(
    "ro.product.model",
    "ro.product.brand",
    "ro.product.manufacturer",
    "ro.product.system.model",
    "ro.product.system.brand",
    "ro.product.system.manufacturer",
    "ro.product.vendor.model",
    "ro.product.vendor.brand",
    "ro.product.vendor.manufacturer",
    "ro.product.odm.model",
    "ro.product.odm.brand",
    "ro.product.odm.manufacturer",
    "ro.product.bootimage.model",
    "ro.product.bootimage.brand",
    "ro.product.bootimage.manufacturer",
    "ro.build.product",
    "ro.build.fingerprint",
    "ro.build.display.id",
    "ro.build.description",
    "ro.build.ProductCode",
    "persist.sys.hctmodel",
    "bluetooth.device.default_name",
    "vendor.xrz.disable_eink_vsync",
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
