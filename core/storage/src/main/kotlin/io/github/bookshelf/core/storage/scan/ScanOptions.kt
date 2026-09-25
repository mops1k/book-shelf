package io.github.bookshelf.core.storage.scan

/**
 * Параметры обхода дерева источника (docs/architecture.md §4.2).
 *
 * @param maxDepth предел вложенности: защита от патологически глубоких деревьев;
 * @param excludeHidden пропускать ли записи, начинающиеся с точки;
 * @param excludeGlobs glob-маски исключений из настроек источника;
 * @param extensions белый список расширений в нижнем регистре (включая составное `fb2.zip`).
 */
data class ScanOptions(
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    val excludeHidden: Boolean = true,
    val excludeGlobs: List<String> = BookFileFilter.DEFAULT_EXCLUDE_GLOBS,
    val extensions: Set<String> = BookFileFilter.SUPPORTED_EXTENSIONS,
) {

    companion object {

        /** Значение по умолчанию; в дальнейшем читается из настроек источника. */
        const val DEFAULT_MAX_DEPTH = 8

        /** Готовый набор параметров для источника без пользовательских правок. */
        val DEFAULT = ScanOptions()
    }
}
