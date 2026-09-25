package io.github.bookshelf.core.testing.fixture

import java.nio.charset.Charset

/**
 * Доступ к тестовым фикстурам из classpath.
 *
 * Фикстуры лежат в `src/test/resources/fixtures/<формат>/…` того модуля, который их использует,
 * поэтому загрузчик берёт classloader вызывающего класса. Это позволяет гонять один и тот же
 * набор файлов и из JVM-тестов, и из Robolectric без копирования.
 */
object FixtureLoader {

    fun bytes(path: String, loader: ClassLoader = defaultLoader()): ByteArray =
        loader.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("Фикстура не найдена: $path. Проверьте src/test/resources/$path")

    fun text(path: String, charset: Charset = Charsets.UTF_8, loader: ClassLoader = defaultLoader()): String =
        bytes(path, loader).toString(charset)

    /** Существует ли фикстура — чтобы тест мог быть пропущен, а не «упал непонятно почему». */
    fun exists(path: String, loader: ClassLoader = defaultLoader()): Boolean =
        loader.getResource(path) != null

    private fun defaultLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: FixtureLoader::class.java.classLoader
            ?: error("Не удалось получить ClassLoader")
}
