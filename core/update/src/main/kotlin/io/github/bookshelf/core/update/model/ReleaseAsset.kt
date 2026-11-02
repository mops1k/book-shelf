package io.github.bookshelf.core.update.model

/** Один APK, приложенный к релизу (ADR-018). */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)
