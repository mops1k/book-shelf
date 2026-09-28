# Правила R8/ProGuard для :app.
#
# Файл обязателен: buildType `release` в convention-плагине ссылается на
# `proguard-rules.pro`, и без него release-сборка не проходит конфигурацию.

# --- Вендорский e-ink SDK (core:eink) ---------------------------------------
# Onyx SDK подключается как compileOnly и вызывается рефлексией, поэтому R8 о нём
# ничего не знает: классы нужно сохранить по имени, иначе они исчезнут как
# «недостижимый» код. Точный список уточняется в Sprint 10, когда появится
# OnyxEinkController.
-keep class com.onyx.android.sdk.** { *; }
-dontwarn com.onyx.android.sdk.**

# Скрытые API платформы, к которым идёт обращение через Class.forName
# (EinkDeviceDetector читает Build-поля и SystemProperties).
-keep class android.os.SystemProperties { *; }
-keepclassmembers class android.view.View {
    public void setByEinkUpdateMode(int);
}

# --- Сериализация (core:readerlink) ------------------------------------------
# ReaderProfile аннотирован @Serializable и хранится в настройках как JSON.
# kotlinx-serialization приносит consumer-правила с 1.4, но generated-классы
# serializer'ов переживают shrinker только при явном сохранении.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.bookshelf.core.readerlink.profile.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.bookshelf.core.readerlink.profile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Enum'ы, которые хранятся в БД как TEXT (core:database) ------------------
# Converters пишет enum-константы через `value.name`. R8 переименовывает поля и
# константы, поэтому после обновления приложения сохранённые строки перестали бы
# находиться: enumValueOf вернул бы null, а конвертер — проглотил значение молча,
# и книги потеряли бы статус/источник. Это не косметика, а порча пользовательской
# библиотеки, поэтому имена констант сохраняем.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Форматируемые имена файлов в сообщениях об ошибках ----------------------
# Сообщения об ошибках разбора должны оставаться читаемыми в отчёте о скане.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
