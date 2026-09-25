package io.github.bookshelf.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Чистый Kotlin/JVM-модуль.
 *
 * Когда использовать: для логики, которая не касается платформы (разбор имени файла, правила
 * умных полок, определение номера серии, работа с профилями читалок как JSON). Такие модули
 * тестируются обычным JUnit за миллисекунды, без Robolectric.
 *
 * Когда не использовать: если модуль зависит от `android.*` (например, от `XmlPullParser`
 * или `Bitmap`) — тесты на JVM-реализации парсера не доказывают поведение на устройстве,
 * поэтому парсеры форматов живут в Android-библиотеках с Robolectric-тестами.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        configureKotlinJvm()
        configureDetekt()
        configureJvmTests()
    }
}
