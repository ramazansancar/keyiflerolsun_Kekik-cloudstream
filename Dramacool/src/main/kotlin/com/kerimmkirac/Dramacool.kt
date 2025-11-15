package com.kerimmkirac

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import java.net.URLEncoder
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import android.util.Base64

class Dramacool : MainAPI() {
    override var mainUrl = "https://dramacool.com.tr"
    override var name = "Dramacool"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/recently-added-drama" to "Yeni Dizi Bölümleri",
        "$mainUrl/recently-added-kshow" to "Yeni Program Bölümleri",
        "$mainUrl/country/korean" to "Kdrama",
        "$mainUrl/country/japanese-a" to "Jdrama",
        "$mainUrl/country/chinese" to "Cdrama"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page").document
        val home = document.select("ul.switch-block li").mapNotNull { it.toMainPageResult(request.data) } 
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(categoryUrl: String): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val baseTitle = aTag.selectFirst("h3.title")?.text()?.trim() ?: return null
        val originalHref = aTag.attr("href").takeIf { it.isNotEmpty() } ?: return null
        
        val imgElement = aTag.selectFirst("img")
        val rawPosterUrl = imgElement?.attr("data-original")?.takeIf { it.isNotEmpty() }
            ?: imgElement?.attr("src")?.takeIf { it.isNotEmpty() }
        
        val posterUrl = if (!rawPosterUrl.isNullOrEmpty()) {
            fixImageFormat(rawPosterUrl)
        } else {
            null
        }
        
        val epSpan = this.selectFirst("span.ep")?.text()?.trim()
        val href = convertToSeriesUrl(originalHref)
        
        val finalTitle = if (categoryUrl.contains("recently-added-drama") || categoryUrl.contains("recently-added-kshow")) {
            if (epSpan != null && epSpan.startsWith("EP")) {
                "$baseTitle $epSpan"
            } else {
                baseTitle
            }
        } else {
            baseTitle
        }
        
        return newMovieSearchResponse(finalTitle, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    private fun fixImageFormat(url: String): String {
        if (url.isEmpty()) return ""
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        return try {
            "https://res.cloudinary.com/di0j4jsa8/image/fetch/f_auto/$encodedUrl"
        } catch (e: Exception) {
            url
        }
    }

    private fun convertToSeriesUrl(originalUrl: String): String {
        return when {
            originalUrl.contains("episode-") -> {
                val urlParts = originalUrl.split("/")
                val episodePart = urlParts.find { it.contains("episode-") } ?: return originalUrl
                val seriesName = episodePart.replace(Regex("""-episode-\d+.*$"""), "")
                "$mainUrl/series/${seriesName}/"
            }
            
            else -> originalUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl?type=movies&s=$query").document
        return document.select("ul.switch-block li").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val title = aTag.selectFirst("h3.title")?.text()?.trim() ?: return null
        val href = aTag.attr("href").takeIf { it.isNotEmpty() } ?: return null
        
        val imgElement = aTag.selectFirst("img")
        val rawPosterUrl = imgElement?.attr("data-original")?.takeIf { it.isNotEmpty() }
            ?: imgElement?.attr("src")?.takeIf { it.isNotEmpty() }
        
        val posterUrl = if (!rawPosterUrl.isNullOrEmpty()) {
            fixImageFormat(rawPosterUrl)
        } else {
            null
        }

        return newMovieSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.info h1")?.text()?.trim() ?: return null
        
        val rawPoster = document.selectFirst("div.img img")?.attr("src")
        val poster = if (!rawPoster.isNullOrEmpty()) {
            fixImageFormat(rawPoster)
        } else {
            null
        }
        
        val description = document.select("div.info > p").getOrNull(2)?.text()?.trim()


        val year = document.selectFirst("p:has(span:contains(Released:)) a")?.text()?.toIntOrNull()
        val tags = document.select("p:has(span:contains(Genre:)) a").map { it.text() }
        val trailer = document.selectFirst("div.trailer iframe")?.attr("src")
            ?.let { src ->
                Regex("""youtube\.com/embed/([^/?]+)""").find(src)?.groupValues?.get(1)
            }?.let { id ->
                "https://www.youtube.com/embed/$id"
            }

        val episodes = document.select("ul.list-episode-item-2.all-episode li").mapNotNull { li ->
            val aTag = li.selectFirst("a") ?: return@mapNotNull null
            val epUrl = aTag.attr("href").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            
            val epNumber = Regex("""episode-(\d+)(?:-\w+)?/?$""").find(epUrl) 
                ?.groupValues?.get(1)
                ?.toIntOrNull() ?: 0
            
            val epTitle = "Bölüm $epNumber" 

            newEpisode(epUrl) {
                this.name = epTitle
                this.episode = epNumber
                this.posterUrl = poster
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    
    val document = app.get(data).document
    val iframes = document.select("iframe[src*=asianload], iframe[src*=asianembed], div.watch-iframe iframe")

    if (iframes.isEmpty()) {
        Log.w("Dramacool", "no iframe found: $data")
        return false
    }

    for (iframe in iframes) {
        val src = iframe.attr("src").trim()
        val iframeUrl = if (src.startsWith("//")) "https:$src" else src
        Log.d("Dramacool", "found iframe : $iframeUrl")

        when {
            iframeUrl.contains("asianload") -> {
                
                extractAsianLoadWithJsHandling(iframeUrl, callback)
            }
            
            iframeUrl.contains("asianembed") -> {
                Log.d("Dramacool", "AsianEmbed found")
                try {
                    val extractor = asianembed()
                    extractor.getUrl(iframeUrl, data, subtitleCallback, callback)
                } catch (e: Exception) {
                    Log.e("Dramacool", "AsianEmbed  ${e.message}", e)
                }
            }
            
            else -> {
                Log.d("Dramacool", "not supported iframe: $iframeUrl")
            }
        }
    }
    return true
}

    private suspend fun extractAsianLoadWithJsHandling(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            
            val document = app.get(url).document
            
            val script = document.selectFirst("div#player + script")?.html()

            if (script == null) {
                
                return
            }

            if (!script.startsWith("eval(function(p,a,c,k,e,d){")) {
                 
                 return
            }

            
            val unpacked = JsUnpacker(script).unpack()

            if (unpacked != null) {
                

                val allBase64Links = mutableListOf<String>()

                val base64Regex = Regex("""window\.atob\("([^"]+)"\)""")
                base64Regex.findAll(unpacked).forEach { match ->
                    val base64EncodedUrl = match.groupValues[1]
                    allBase64Links.add(base64EncodedUrl)
                }

                var foundVideoLink = false

                for (base64EncodedUrl in allBase64Links) {
                    try {
                        val decodedLink = String(Base64.decode(base64EncodedUrl, Base64.DEFAULT))
                        
                        if (decodedLink.contains(".mp4")) {
                           
                            callback(
                                newExtractorLink(
                                    name = "AsianLoad (mp4)",
                                    source = "AsianLoad",
                                    url = decodedLink,
                                    
                                    type = ExtractorLinkType.VIDEO
                                ){
                                    this.referer = url
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            foundVideoLink = true
                        } else if (decodedLink.contains(".m3u8")) {
                            
                            callback(
                                newExtractorLink(
                                    name = "AsianLoad (m3u8)",
                                    source = "AsianLoad",
                                    url = decodedLink,
                                    
                                    type = ExtractorLinkType.M3U8
                                ){
                                    this.referer = url
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            foundVideoLink = true
                        } else if (decodedLink.contains(".jpg") || decodedLink.contains(".png")) {
                             
                        } else {
                            
                        }

                    } catch (e: Exception) {
                        
                    }
                }

                if (!foundVideoLink) {
                   
                }

            } else {
                
            }
        } catch (e: Exception) {
            
        }
    }
}