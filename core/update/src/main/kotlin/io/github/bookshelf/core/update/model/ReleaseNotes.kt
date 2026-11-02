package io.github.bookshelf.core.update.model

/**
 * Текст «что нового» для диалога обновления (ADR-018).
 *
 * Тело релиза на GitHub — markdown: заголовки, картинки, таблицы установки. В диалоге нужен
 * короткий текст, иначе блок новостей растягивал карточку на весь экран (найдено на устройстве
 * 02.11.2026). Картинки и разметка заголовков отбрасываются, лишние пустые строки схлопываются,
 * длинный текст обрезается по границе символа.
 */
object ReleaseNotes {

    /** Сколько символов показывать по умолчанию: примерно экран крупного шрифта на e-ink. */
    const val DEFAULT_MAX_CHARS: Int = 700

    /** Пригодный для диалога текст: без картинок и заголовков, с обрезкой до [maxChars]. */
    fun preview(raw: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val lines = raw.lines()
            .map { it.trim() }
            .filterNot { it.startsWith("![") }
            .filterNot { it.startsWith("<img") }
            // Жирный/код в markdown: в диалоге звёздочки и обратные кавычки — мусор.
            .map { it.replace("**", "").replace("`", "") }
            .map { it.trimStart('#', ' ') }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""
        val text = lines.joinToString("\n")
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"
    }
}
