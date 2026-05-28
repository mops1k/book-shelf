package com.bookshelf.app.data.remote

import com.bookshelf.app.data.model.AcquisitionLink
import com.bookshelf.app.data.model.DownloadLink
import com.bookshelf.app.data.model.OpdsEntry
import com.bookshelf.app.data.model.OpdsFeed
import com.bookshelf.app.data.model.OpdsNavigation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.xml.Xml

@Xml
@Serializable
data class AtomFeed(
    @SerialName("title") val title: String?,
    @SerialName("entry") val entries: List<AtomEntry>?,
    @SerialName("link") val links: List<AtomLink>?,
    @SerialName("navigation") val navigation: List<OpdsNavigation>?
) {
    fun toOpdsFeed(): OpdsFeed {
        return OpdsFeed(
            title = title ?: "Unknown",
            entries = entries?.map { it.toOpdsEntry() } ?: emptyList(),
            navigation = navigation ?: emptyList(),
            nextUrl = links?.find { it.rel == "next" }?.href
        )
    }
}

@Xml
@Serializable
data class AtomEntry(
    @SerialName("id") val id: String?,
    @SerialName("title") val title: String?,
    @SerialName("author") val author: AtomAuthor?,
    @SerialName("summary") val summary: String?,
    @SerialName("content") val content: String?,
    @SerialName("link") val links: List<AtomLink>?,
    @SerialName("cover") val cover: AtomCover?
) {
    fun toOpdsEntry(): OpdsEntry {
        val downloadLinks = links?.filter { 
            it.type?.startsWith("application/") == true || 
            it.type?.startsWith("text/") == true 
        }?.map { 
            DownloadLink(
                href = it.href ?: "",
                type = it.type ?: "",
                title = it.title
            )
        } ?: emptyList()
        
        val acquisitionLinks = links?.filter { 
            it.rel == "http://opds-spec.org/acquisition" 
        }?.map {
            AcquisitionLink(
                href = it.href ?: "",
                type = it.type ?: "",
                relation = it.rel ?: ""
            )
        } ?: emptyList()
        
        return OpdsEntry(
            id = id ?: "",
            title = title ?: "Unknown",
            author = author?.name,
            summary = summary ?: content,
            coverUrl = cover?.href ?: links?.find { it.rel == "http://opds-spec.org/image" }?.href,
            downloadLinks = downloadLinks,
            acquisitionLinks = acquisitionLinks
        )
    }
}

@Xml
@Serializable
data class AtomAuthor(
    @SerialName("name") val name: String?
)

@Xml
@Serializable
data class AtomLink(
    @SerialName("href") val href: String?,
    @SerialName("type") val type: String?,
    @SerialName("rel") val rel: String?,
    @SerialName("title") val title: String?
)

@Xml
@Serializable
data class AtomCover(
    @SerialName("href") val href: String?
)
