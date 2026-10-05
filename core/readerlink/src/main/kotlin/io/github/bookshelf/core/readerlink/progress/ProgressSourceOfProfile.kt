package io.github.bookshelf.core.readerlink.progress

import io.github.bookshelf.core.readerlink.koreader.KoReaderPreset
import io.github.bookshelf.core.readerlink.profile.FbReaderPreset

/**
 * Какой источник прогресса соответствует профилю читалки.
 *
 * Нужно для правила нагрузки (требование Александра, 05.10.2026): пока идёт чтение, разрешено
 * спрашивать **только ту** читалку, которая открыта сейчас. Книгу называет сессия чтения, а читалку
 * — профиль, которым её открывали; связь между профилем и источником живёт здесь, потому что оба
 * идентификатора принадлежат модулю читалок.
 *
 * `null` означает «у нас нет источника для этой читалки»: тогда во время чтения не спрашивают
 * никого. Опросить «всех остальных» нельзя — это ровно та нагрузка, от которой отказались.
 */
fun progressSourceOf(readerProfileId: String): ProgressSourceId? = when (readerProfileId) {
    FbReaderPreset.ID -> ProgressSourceId.FBREADER_AIDL
    KoReaderPreset.ID -> ProgressSourceId.KOREADER_SIDECAR
    else -> null
}
