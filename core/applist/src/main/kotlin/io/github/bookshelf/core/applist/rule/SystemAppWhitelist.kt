package io.github.bookshelf.core.applist.rule

/**
 * Белый список полезных системных приложений (решение Александра 02.11.2026).
 *
 * Вендорские прошивки объявляют launcher-активности не только у приложений, но и у служебных
 * записей: тестовые пакеты MediaTek, «Отчет о регистрации», отдельные активности настроек
 * клавиатуры. В Google-лаунчере их не видно, потому что там такие записи просто не объявлены
 * лаунчерными, а у вендора — объявлены. Поэтому при включённой галочке «Показывать системные
 * приложения» показываем не всё подряд, а только полезное из списка ниже: настройки, файлы,
 * камера, часы, контакты, маркет, клавиатура, музыка, менеджер приложений.
 *
 * Список сознательно короткий и явный, а не «всё, что похоже на системное»: скрыть нужное хуже,
 * чем не показать лишнее — лишнее пользователь всё равно не ищет.
 */
object SystemAppWhitelist {

    /** Точные пакеты, которые считаем полезными на любом устройстве. */
    private val exactPackages: Set<String> = setOf(
        "com.android.settings",
        "com.android.deskclock",
        "com.android.contacts",
        "com.android.vending",
        "com.google.android.inputmethod.latin",
        "com.android.camera2",
        "com.android.gallery3d",
        "com.android.calendar",
        "com.android.calculator2",
        "com.android.documentsui",
        "com.google.android.apps.photos",
        // Вендорские приложения Bigme: файлы, музыка, менеджер приложений.
        "com.xrz.file.manager",
        "com.xrz.music",
        "com.xrz.appmanager",
    )

    /** Семейства пакетов камеры и калькулятора: у разных прошивок разные имена. */
    private val prefixes: List<String> = listOf(
        "com.android.camera",
        "com.mediatek.camera",
        "com.android.calculator",
        "com.android.gallery",
    )

    /** Полезное системное приложение, которое показываем при включённой галочке. */
    fun isUseful(packageName: String): Boolean =
        packageName in exactPackages || prefixes.any { prefix -> packageName.startsWith(prefix) }
}
