package io.github.bookshelf.core.eink.device

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.eink.vendor.EinkVendor
import org.junit.Test

/**
 * Тест таблицы определения вендора («E-ink UX» §8).
 *
 * Проверяются ровно те строки, которые встречаются в `Build.MANUFACTURER`/`Build.BRAND`/`Build.MODEL`
 * реальных устройств, плюс неизвестное устройство и нечувствительность к регистру. Функция чистая,
 * поэтому тест не требует Robolectric.
 */
class EinkDeviceDetectorTest {

    @Test
    fun onyx_is_detected_by_manufacturer_and_model() {
        assertThat(detectVendor("Onyx", "onyx", "Note Air")).isEqualTo(EinkVendor.ONYX)
        assertThat(detectVendor("Onyx International", "ONYX", "BOOX Nova Air")).isEqualTo(EinkVendor.ONYX)
    }

    @Test
    fun rockchip_boyue_is_detected_by_all_three_brands() {
        assertThat(detectVendor("Boyue", "boyue", "Likebook Mars")).isEqualTo(EinkVendor.ROCKCHIP_BOYUE)
        assertThat(detectVendor(null, null, "Likebook Mimas")).isEqualTo(EinkVendor.ROCKCHIP_BOYUE)
        assertThat(detectVendor("Meebook", null, "M6")).isEqualTo(EinkVendor.ROCKCHIP_BOYUE)
    }

    @Test
    fun other_vendors_are_detected_by_name() {
        assertThat(detectVendor("Hisense", null, "A9")).isEqualTo(EinkVendor.HISENSE)
        assertThat(detectVendor(null, "tolino", "vision 6")).isEqualTo(EinkVendor.TOLINO)
        assertThat(detectVendor("Bigme", null, "S6")).isEqualTo(EinkVendor.BIGME)
        assertThat(detectVendor("Dasung", null, "Paperlike")).isEqualTo(EinkVendor.DASUNG)
        assertThat(detectVendor("PocketBook", null, "740")).isEqualTo(EinkVendor.POCKETBOOK)
        assertThat(detectVendor("iReader", null, "Smart X")).isEqualTo(EinkVendor.IREADER)
        assertThat(detectVendor(null, null, "InkPalm 5")).isEqualTo(EinkVendor.INKPALM)
    }

    @Test
    fun vendor_is_detected_from_system_properties() {
        val properties = mapOf("ro.product.model" to "BOOX Page", "ro.product.brand" to "onyx")

        assertThat(detectVendor(null, null, null, properties)).isEqualTo(EinkVendor.ONYX)
    }

    @Test
    fun unknown_device_falls_back_to_unknown() {
        assertThat(detectVendor("Google", "google", "Pixel 7")).isEqualTo(EinkVendor.UNKNOWN)
        assertThat(detectVendor(null, null, null, emptyMap())).isEqualTo(EinkVendor.UNKNOWN)
        assertThat(detectVendor("", "", "")).isEqualTo(EinkVendor.UNKNOWN)
    }
}
