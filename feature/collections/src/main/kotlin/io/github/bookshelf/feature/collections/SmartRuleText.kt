package io.github.bookshelf.feature.collections

import io.github.bookshelf.core.data.model.SmartRulePart
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.genreText
import io.github.bookshelf.core.designsystem.text.readingStatusText

/**
 * Части правила умной полки → переводимые подписи.
 *
 * Живёт в модуле интерфейса, а не в `:core:data` («TDD» §8): у слоя данных нет ресурсов, и
 * готовая строка оттуда показывалась бы по-русски и в английском интерфейсе. Значения частей —
 * это данные: имя автора или серии показывается как есть, код жанра и имя статуса переводятся,
 * а неизвестный код тоже показывается как есть (это значение метаданных, а не текст).
 */
internal fun List<SmartRulePart>.toSummaryText(maxParts: Int): List<AppText> {
    val shown = take(maxParts).map { part -> part.toText() }
    val rest = size - maxParts
    return if (rest > 0) shown + AppText(R.string.collections_rule_more, rest) else shown
}

/** Подпись одной части правила. */
internal fun SmartRulePart.toText(): AppText = when (this) {
    is SmartRulePart.Genre -> AppText(R.string.collections_rule_genre, genreText(code) ?: code)
    is SmartRulePart.Author -> AppText(R.string.collections_rule_author, name)
    is SmartRulePart.Series -> AppText(R.string.collections_rule_series, name)
    is SmartRulePart.Format -> AppText(R.string.collections_rule_format, extension.uppercase())
    is SmartRulePart.Status -> AppText(R.string.collections_rule_status, readingStatusText(name) ?: name)
    SmartRulePart.Favorites -> AppText(R.string.collections_rule_favorites)
}
