package com.bookshelf.app.data.local

import android.content.Context
import com.bookshelf.app.data.model.Book
import com.bookshelf.app.data.model.BookFormat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipFile

class BookMetadataParser(private val context: Context) {
    
    fun parseMetadata(file: File): Book {
        val format = getBookFormat(file)
        return when (format) {
            BookFormat.FB2 -> parseFb2Metadata(file)
            BookFormat.EPUB -> parseEpubMetadata(file)
            BookFormat.PDF -> parsePdfMetadata(file)
            BookFormat.UNKNOWN -> createUnknownBook(file)
        }
    }
    
    private fun getBookFormat(file: File): BookFormat {
        return when {
            file.extension.equals("fb2", ignoreCase = true) || 
            file.extension.equals("fb2.zip", ignoreCase = true) -> BookFormat.FB2
            file.extension.equals("epub", ignoreCase = true) -> BookFormat.EPUB
            file.extension.equals("pdf", ignoreCase = true) -> BookFormat.PDF
            else -> BookFormat.UNKNOWN
        }
    }
    
    private fun parseFb2Metadata(file: File): Book {
        var title = file.nameWithoutExtension
        var author: String? = null
        var seriesName: String? = null
        var seriesNumber: Double? = null
        var year: Int? = null
        var genre: String? = null
        var language: String? = "ru"
        var annotation: String? = null
        var coverImageBytes: ByteArray? = null
        
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            
            val inputStream = if (file.extension.equals("zip", ignoreCase = true)) {
                ZipFile(file).use { zip ->
                    val entry = zip.entries().asSequence().firstOrNull { 
                        it.name.endsWith(".fb2") 
                    }
                    entry?.let { zip.getInputStream(it) }
                }
            } else {
                file.inputStream()
            }
            
            inputStream?.use { stream ->
                parser.setInput(stream.reader())
                
                var eventType = parser.eventType
                var inTitleInfo = false
                var inAuthor = false
                var inFirstName = false
                var inLastName = false
                var inMiddleName = false
                var inAnnotation = false
                var inCoverpage = false
                var authorParts = mutableListOf<String>()
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "title-info" -> inTitleInfo = true
                                "book-title" -> {
                                    if (inTitleInfo) {
                                        // Will read text next
                                    }
                                }
                                "author" -> {
                                    inAuthor = true
                                    authorParts.clear()
                                }
                                "first-name" -> inFirstName = true
                                "last-name" -> inLastName = true
                                "middle-name" -> inMiddleName = true
                                "annotation" -> inAnnotation = true
                                "coverpage" -> inCoverpage = true
                                "sequence" -> {
                                    if (inTitleInfo) {
                                        seriesName = parser.getAttributeValue(null, "name")
                                        seriesNumber = parser.getAttributeValue(null, "number")?.toDoubleOrNull()
                                    }
                                }
                                "genre" -> {
                                    if (inTitleInfo) {
                                        genre = parser.nextText()
                                    }
                                }
                                "lang" -> {
                                    if (inTitleInfo) {
                                        language = parser.nextText()
                                    }
                                }
                                "date" -> {
                                    if (inTitleInfo) {
                                        year = parser.nextText().take(4).toIntOrNull()
                                    }
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text.trim()
                            if (text.isEmpty()) return@run
                            
                            when {
                                parser.name == "book-title" && inTitleInfo -> title = text
                                parser.name == "first-name" && inAuthor -> authorParts.add(0, text)
                                parser.name == "last-name" && inAuthor -> authorParts.add(text)
                                parser.name == "middle-name" && inAuthor -> {
                                    if (authorParts.size > 0) {
                                        authorParts.add(1, text)
                                    }
                                }
                                parser.name == "annotation" && inAnnotation -> {
                                    annotation = (annotation ?: "") + text
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "title-info" -> inTitleInfo = false
                                "author" -> {
                                    inAuthor = false
                                    if (authorParts.isNotEmpty()) {
                                        author = authorParts.joinToString(" ")
                                    }
                                }
                                "annotation" -> inAnnotation = false
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return Book(
            title = title,
            author = author,
            seriesName = seriesName,
            seriesNumber = seriesNumber,
            year = year,
            genre = genre,
            language = language,
            annotation = annotation,
            coverImageBytes = coverImageBytes,
            filePath = file.absolutePath,
            fileSize = file.length(),
            format = BookFormat.FB2
        )
    }
    
    private fun parseEpubMetadata(file: File): Book {
        var title = file.nameWithoutExtension
        var author: String? = null
        var seriesName: String? = null
        var seriesNumber: Double? = null
        var year: Int? = null
        var language: String? = null
        var coverImageBytes: ByteArray? = null
        
        try {
            ZipFile(file).use { zip ->
                // Find content.opf or similar
                val contentOpfEntry = zip.entries().asSequence().firstOrNull { 
                    it.name.endsWith(".opf") 
                }
                
                contentOpfEntry?.let { entry ->
                    zip.getInputStream(entry).use { stream ->
                        val content = stream.bufferedReader().readText()
                        
                        // Parse title
                        val titleRegex = Regex("<dc:title[^>]*>([^<]+)</dc:title>")
                        title = titleRegex.find(content)?.groupValues?.get(1) ?: title
                        
                        // Parse creator/author
                        val creatorRegex = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>")
                        author = creatorRegex.find(content)?.groupValues?.get(1)
                        
                        // Parse date
                        val dateRegex = Regex("<dc:date[^>]*>([^<]+)</dc:date>")
                        year = dateRegex.find(content)?.groupValues?.get(1)?.take(4)?.toIntOrNull()
                        
                        // Parse language
                        val langRegex = Regex("<dc:language[^>]*>([^<]+)</dc:language>")
                        language = langRegex.find(content)?.groupValues?.get(1)
                        
                        // Parse series from meta tags
                        val seriesRegex = Regex("""<meta[^>]*name="calibre:series"[^>]*content="([^"]+)"""")
                        seriesName = seriesRegex.find(content)?.groupValues?.get(1)
                        
                        val seriesIndexRegex = Regex("""<meta[^>]*name="calibre:series_index"[^>]*content="([^"]+)"""")
                        seriesNumber = seriesIndexRegex.find(content)?.groupValues?.get(1)?.toDoubleOrNull()
                        
                        // Try to find cover image
                        val coverIdRegex = Regex("""<meta[^>]*name="cover"[^>]*content="([^"]+)"""")
                        val coverId = coverIdRegex.find(content)?.groupValues?.get(1)
                        
                        if (coverId != null) {
                            val coverHrefRegex = Regex("""<item[^>]*id="$coverId"[^>]*href="([^"]+)"""")
                            val coverHref = coverHrefRegex.find(content)?.groupValues?.get(1)
                            
                            if (coverHref != null) {
                                // Resolve relative path
                                val basePath = entry.name.substringBeforeLast("/")
                                val coverPath = if (basePath.isEmpty()) coverHref else "$basePath/$coverHref"
                                
                                val coverEntry = zip.getEntry(coverPath)
                                if (coverEntry != null) {
                                    coverImageBytes = zip.getInputStream(coverEntry).readBytes()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return Book(
            title = title,
            author = author,
            seriesName = seriesName,
            seriesNumber = seriesNumber,
            year = year,
            language = language,
            coverImageBytes = coverImageBytes,
            filePath = file.absolutePath,
            fileSize = file.length(),
            format = BookFormat.EPUB
        )
    }
    
    private fun parsePdfMetadata(file: File): Book {
        // Basic PDF parsing - would need a library like PdfBox for full support
        return Book(
            title = file.nameWithoutExtension,
            filePath = file.absolutePath,
            fileSize = file.length(),
            format = BookFormat.PDF
        )
    }
    
    private fun createUnknownBook(file: File): Book {
        return Book(
            title = file.nameWithoutExtension,
            filePath = file.absolutePath,
            fileSize = file.length(),
            format = BookFormat.UNKNOWN
        )
    }
}
