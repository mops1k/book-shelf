package com.bookshelf.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String?,
    val seriesName: String?,
    val seriesNumber: Double?,
    val year: Int?,
    val genre: String?,
    val language: String?,
    val annotation: String?,
    val coverImageBytes: ByteArray?,
    val filePath: String,
    val fileSize: Long,
    val dateAdded: Long = System.currentTimeMillis(),
    val format: BookFormat,
    val isFavorite: Boolean = false,
    val lastReadPosition: Long = 0L,
    val readProgress: Float = 0f
)

enum class BookFormat {
    FB2, EPUB, PDF, UNKNOWN
}
