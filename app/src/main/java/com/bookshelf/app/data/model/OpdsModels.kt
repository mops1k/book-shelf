package com.bookshelf.app.data.model

data class OpdsCatalog(
    val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true
)

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val navigation: List<OpdsNavigation>,
    val nextUrl: String? = null
)

data class OpdsEntry(
    val id: String,
    val title: String,
    val author: String?,
    val summary: String?,
    val coverUrl: String?,
    val downloadLinks: List<DownloadLink>,
    val acquisitionLinks: List<AcquisitionLink>
)

data class DownloadLink(
    val href: String,
    val type: String,
    val title: String?
)

data class AcquisitionLink(
    val href: String,
    val type: String,
    val relation: String
)

data class OpdsNavigation(
    val title: String,
    val href: String,
    val count: Int?
)
