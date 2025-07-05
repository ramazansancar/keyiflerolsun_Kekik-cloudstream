package com.kerimmkirac

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
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
        "$mainUrl/recently-added-kshow" to "Yeni Kore Programları",
        "$mainUrl/most-popular-drama/" to "En Ünlü Diziler",
        "$mainUrl/country/korean" to "Kdrama",
        "$mainUrl/country/japanese-a" to "Jdrama"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page").document
        val home = document.select("ul.switch-block li").mapNotNull { it.toMainPageResult(request.data) }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(categoryUrl: String): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val baseTitle = aTag.selectFirst("h3.title")?.text()?.trim() ?: return null
        val originalHref = fixUrlNull(aTag.attr("href")) ?: return null
        val posterUrl = fixUrlNull(aTag.selectFirst("img")?.attr("src"))

        val epSpan = this.selectFirst("span.ep")?.text()?.trim()
        val href = convertToSeriesUrl(originalHref)

        val finalTitle = if (categoryUrl.contains("recently-added-drama") || categoryUrl.contains("recently-added-kshow")) {
            if (epSpan != null && epSpan.startsWith("EP")) {
                "$baseTitle $epSpan"
            } else baseTitle
        } else baseTitle

        return newMovieSearchResponse(finalTitle, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    private fun convertToSeriesUrl(originalUrl: String): String {
        return when {
            originalUrl.contains("episode-") -> {
                val urlParts = originalUrl.split("/")
                val episodePart = urlParts.find { it.contains("episode-") } ?: return originalUrl
                val seriesName = episodePart.replace(Regex("""-episode-\d+.*$"""), "")
                "$mainUrl/series/$seriesName/"
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
        val href = fixUrlNull(aTag.attr("href")) ?: return null
        val posterUrl = fixUrlNull(aTag.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.info h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.img img")?.attr("src"))
        val description = document.select("div.info > p").drop(1)
            .takeWhile { !it.selectFirst("span")?.text().orEmpty().contains("Original Network:") }
            .joinToString("\n") { it.text().trim() }
        val year = document.selectFirst("p:has(span:contains(Released:)) a")?.text()?.toIntOrNull()
        val tags = document.select("p:has(span:contains(Genre:)) a").map { it.text() }
        val trailer = document.selectFirst("div.trailer iframe")?.attr("src")
            ?.let { src -> Regex("""youtube\\.com/embed/([^/?]+)""").find(src)?.groupValues?.get(1) }
            ?.let { id -> "https://www.youtube.com/embed/$id" }

        val episodes = document.select("ul.list-episode-item-2.all-episode li").mapNotNull { li ->
            val aTag = li.selectFirst("a") ?: return@mapNotNull null
            val epUrl = fixUrlNull(aTag.attr("href")) ?: return@mapNotNull null
            val epNumber = Regex("""episode-(\d+)(?:-\w+)?/?$""").find(epUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val epTitle = "Bölüm $epNumber"

            newEpisode(epUrl) {
                this.name = epTitle
                this.episode = epNumber
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
        Log.d("Dramacool", "loadLinks çağrıldı, URL: $data")
        val document = app.get(data).document
        val iframes = document.select("iframe[src*=asianload], div.watch-iframe iframe")

        if (iframes.isEmpty()) {
            Log.w("Dramacool", "Bölüm sayfasında iframe bulunamadı: $data")
            return false
        }

        for (iframe in iframes) {
            val src = iframe.attr("src").trim()
            val iframeUrl = if (src.startsWith("//")) "https:$src" else src
            Log.d("Dramacool", "Iframe bulundu, URL: $iframeUrl")

            if (iframeUrl.contains("asianload")) {
                Log.d("Dramacool", "AsianLoad iframe'i tespit edildi. Linkler çekiliyor")
                extractAsianLoadWithJsHandling(iframeUrl, callback)
            } else {
                Log.d("Dramacool", "Desteklenmeyen iframe kaynağı: $iframeUrl")
            }
        }
        return true
    }

    private suspend fun extractAsianLoadWithJsHandling(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            Log.d("AsianLoad", "AsianLoad iframe URL'sine gidiliyor: $url")
            val document = app.get(url).document
            val script = document.selectFirst("div#player + script")?.html() ?: return

            if (!script.startsWith("eval(function(p,a,c,k,e,d){")) {
                Log.e("AsianLoad", "Eval içeren script bulunamadı: $url")
                return
            }

            val unpacked = JsUnpacker(script).unpack() ?: return

            val allBase64Links = Regex("""window\\.atob\("([^"]+)"\)""").findAll(unpacked).mapNotNull {
                it.groupValues.getOrNull(1)
            }.toList()

            for (encoded in allBase64Links) {
                val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
                when {
                    decoded.contains(".mp4") -> callback(
                        newExtractorLink {
                            name = "AsianLoad (mp4)"
                            source = "AsianLoad"
                            url = decoded
                            referer = url
                            quality = Qualities.Unknown.value
                            isM3u8 = false
                        }
                    )
                    decoded.contains(".m3u8") -> callback(
                        newExtractorLink {
                            name = "AsianLoad (m3u8)"
                            source = "AsianLoad"
                            url = decoded
                            referer = url
                            quality = Qualities.Unknown.value
                            isM3u8 = true
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AsianLoad", "Hata oluştu: ${e.message}", e)
        }
    }
}
