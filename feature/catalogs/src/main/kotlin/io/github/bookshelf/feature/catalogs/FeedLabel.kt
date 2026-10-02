package io.github.bookshelf.feature.catalogs

import io.github.bookshelf.core.designsystem.text.AppText

/**
 * Подпись уровня фида OPDS: заголовок фида и элемент хлебных крошек.
 *
 * Разделение обязательно («TDD» §8): имя каталога, автора и серии приходит с сервера — это
 * данные, они не переводятся; а «Поиск: …», «Поиск по загруженному фиду: …», «Каталог», «Автор» и
 * «Серия» рождаются в приложении и должны быть ресурсами. Раньше всё это было строкой, и в
 * английском интерфейсе хлебные крошки показывали русские «Поиск: …» и «Каталог».
 */
sealed interface FeedLabel {

    /** Имя, пришедшее с сервера: данные, не переводятся. */
    data class Named(val text: String) : FeedLabel

    /** Слова, которые выбирает приложение: ресурс и его аргументы. */
    data class Message(val text: AppText) : FeedLabel
}

/** Данные подписи, если она пришла с сервера; иначе `null`. */
val FeedLabel.namedText: String? get() = (this as? FeedLabel.Named)?.text

/** Слова подписи, если их выбирает приложение; экран показывает их через `resolve()`. */
val FeedLabel.messageText: AppText? get() = (this as? FeedLabel.Message)?.text
