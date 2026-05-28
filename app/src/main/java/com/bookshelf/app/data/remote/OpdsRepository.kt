package com.bookshelf.app.data.remote

import android.content.Context
import com.bookshelf.app.data.local.SettingsRepository
import com.bookshelf.app.data.model.AcquisitionLink
import com.bookshelf.app.data.model.DownloadLink
import com.bookshelf.app.data.model.OpdsCatalog
import com.bookshelf.app.data.model.OpdsEntry
import com.bookshelf.app.data.model.OpdsFeed
import com.bookshelf.app.data.model.OpdsNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.xml.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class OpdsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    
    private val client = OkHttpClient()
    
    suspend fun fetchFeed(url: String): Result<OpdsFeed> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
            }
            
            val xmlContent = response.body?.string() ?: ""
            
            // Simple XML parsing for OPDS Atom feeds
            val feed = parseOpdsFeed(xmlContent)
            
            Result.success(feed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseOpdsFeed(xmlContent: String): OpdsFeed {
        val entries = mutableListOf<OpdsEntry>()
        val navigation = mutableListOf<OpdsNavigation>()
        var title = "Unknown"
        var nextUrl: String? = null
        
        // Extract title
        val titleRegex = Regex("<title[^>]*>([^<]+)</title>")
        val titleMatch = titleRegex.find(xmlContent)
        title = titleMatch?.groupValues?.get(1) ?: "Unknown"
        
        // Check for next link
        val nextLinkRegex = Regex("""<link[^>]*rel="next"[^>]*href="([^"]+)"""")
        val nextMatch = nextLinkRegex.find(xmlContent)
        if (nextMatch != null) {
            nextUrl = nextMatch.groupValues[1]
        } else {
            // Try alternate format
            val nextLinkRegex2 = Regex("""<link[^>]*href="([^"]+)"[^>]*rel="next"""")
            val nextMatch2 = nextLinkRegex2.find(xmlContent)
            nextUrl = nextMatch2?.groupValues?.get(1)
        }
        
        // Parse entries
        val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        for (match in entryRegex.findAll(xmlContent)) {
            val entryContent = match.groupValues[1]
            entries.add(parseOpdsEntry(entryContent))
        }
        
        // Parse navigation links
        val navRegex = Regex("""<navigation><title>[^<]*</title><link[^>]*></navigation>""", RegexOption.DOT_MATCHES_ALL)
        // Simplified navigation parsing
        val simpleNavRegex = Regex("""<link[^>]*rel="subsection"[^>]*title="([^"]+)"[^>]*href="([^"]+)"""")
        for (match in simpleNavRegex.findAll(xmlContent)) {
            navigation.add(
                OpdsNavigation(
                    title = match.groupValues[1],
                    href = match.groupValues[2],
                    count = null
                )
            )
        }
        
        return OpdsFeed(
            title = title,
            entries = entries,
            navigation = navigation,
            nextUrl = nextUrl
        )
    }
    
    private fun parseOpdsEntry(entryContent: String): OpdsEntry {
        var id = ""
        var title = "Unknown"
        var author: String? = null
        var summary: String? = null
        var coverUrl: String? = null
        val downloadLinks = mutableListOf<DownloadLink>()
        val acquisitionLinks = mutableListOf<AcquisitionLink>()
        
        // Extract ID
        val idRegex = Regex("<id[^>]*>([^<]+)</id>")
        id = idRegex.find(entryContent)?.groupValues?.get(1) ?: ""
        
        // Extract title
        val titleRegex = Regex("<title[^>]*>([^<]+)</title>")
        title = titleRegex.find(entryContent)?.groupValues?.get(1) ?: "Unknown"
        
        // Extract author
        val authorRegex = Regex("<author>.*?<name[^>]*>([^<]+)</name>.*?</author>", RegexOption.DOT_MATCHES_ALL)
        author = authorRegex.find(entryContent)?.groupValues?.get(1)
        
        // Extract summary
        val summaryRegex = Regex("<summary[^>]*>([^<]+)</summary>")
        summary = summaryRegex.find(entryContent)?.groupValues?.get(1)
        
        // Extract cover image
        val coverRegex = Regex("""<link[^>]*rel="http://opds-spec.org/image"[^>]*href="([^"]+)"""")
        coverUrl = coverRegex.find(entryContent)?.groupValues?.get(1)
        
        // Extract download/acquisition links
        val linkRegex = Regex("""<link[^>]*href="([^"]+)"[^>]*type="([^"]+)"[^>]*rel="([^"]*)"""")
        for (match in linkRegex.findAll(entryContent)) {
            val href = match.groupValues[1]
            val type = match.groupValues[2]
            val rel = match.groupValues[3]
            
            if (type.startsWith("application/") || type.startsWith("text/")) {
                if (rel == "http://opds-spec.org/acquisition") {
                    acquisitionLinks.add(
                        AcquisitionLink(href = href, type = type, relation = rel)
                    )
                } else {
                    downloadLinks.add(
                        DownloadLink(href = href, type = type, title = null)
                    )
                }
            }
        }
        
        // Also check for simplified link format
        val simpleLinkRegex = Regex("""<link[^>]*href="([^"]+)"[^>]*type="([^"]+)"""")
        for (match in simpleLinkRegex.findAll(entryContent)) {
            val href = match.groupValues[1]
            val type = match.groupValues[2]
            
            if ((type.startsWith("application/") || type.startsWith("text/")) &&
                !downloadLinks.any { it.href == href }) {
                downloadLinks.add(
                    DownloadLink(href = href, type = type, title = null)
                )
            }
        }
        
        return OpdsEntry(
            id = id,
            title = title,
            author = author,
            summary = summary,
            coverUrl = coverUrl,
            downloadLinks = downloadLinks,
            acquisitionLinks = acquisitionLinks
        )
    }
    
    suspend fun downloadBook(url: String, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = if (url.startsWith("http")) url else {
                // Resolve relative URL
                URL(URL(context.getString(com.bookshelf.app.R.string.opds_url)), url).toString()
            }
            
            val request = Request.Builder()
                .url(fullUrl)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code}"))
            }
            
            val file = File(context.filesDir, "downloads/$fileName")
            file.parentFile?.mkdirs()
            
            FileOutputStream(file).use { output ->
                response.body?.byteStream()?.copyTo(output) ?: return@withContext Result.failure(Exception("Empty response"))
            }
            
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getCatalogs(): Flow<List<OpdsCatalog>> = settingsRepository.opdsCatalogs
    
    suspend fun addCatalog(catalog: OpdsCatalog) {
        settingsRepository.addOpdsCatalog(catalog)
    }
    
    suspend fun removeCatalog(id: Long) {
        settingsRepository.removeOpdsCatalog(id)
    }
}
