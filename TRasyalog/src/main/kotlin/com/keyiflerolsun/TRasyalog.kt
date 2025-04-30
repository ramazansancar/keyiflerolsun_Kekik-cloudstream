package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.*
import org.jsoup.Jsoup


class TRasyalog : MainAPI() {
    override var mainUrl        = "https://asyalog.com"
    override var name           = "TRasyalog"
    override val hasMainPage    = true
    override var lang           = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/library/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 500L  // ? 0.5 saniye
    override var sequentialMainPageScrollDelay = 500L  // ? 0.5 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/category/kore-dizileri-izle-guncel/" to "Kore Dizileri",
        "${mainUrl}/category/cin-dizileri/"              to "Çin Dizileri",
        "${mainUrl}/category/tayland-dizileri/"          to "TaylandDizileri",
        "${mainUrl}/category/japon-dizileri/"            to "Japon Diziler",
        "${mainUrl}/category/endonezya-dizileri/"        to "Endonezya Diziler",
        "${mainUrl}/category/devam-eden-diziler/"        to "Devam eden Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page").document
        val home     = document.select("div.post-container").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt")?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.thumbnail img")?.let { img ->
        img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src")
    }
)

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/search?name=${query}").document

        return document.select("div.post-container").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
    
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("img.wp-image-66892")?.attr("data-src")
            ?: document.selectFirst("img.wp-image-66892")?.attr("src"))
        val description = document.selectFirst("h2 > p")?.text()?.trim()
        val tags = document.select("div.post-meta a[href*='/category/']").map { it.text() }
    
        val episodes = mutableListOf<Episode>()
    
        // Bölüm URL'lerini almak için data-url kullanıyoruz
        val partUrls = document.select("span[data-url]").mapNotNull {
            val relativeUrl = it.attr("data-url").trim()
            if (relativeUrl.isNotBlank()) fixUrl(relativeUrl) else null
        }
    
        // Eğer data-url varsa, her bir grup için bölüm URL'lerini alıyoruz
        if (partUrls.isNotEmpty()) {
            for (partUrl in partUrls) {
                val partDoc = app.get(partUrl).document
                val tabDivs = partDoc.select("div.tab_content[id^=tab-]")
    
                for (div in tabDivs) {
                    val idAttr = div.attr("id")
                    val epNum = Regex("tab-\\d+-(\\d+)-bolum").find(idAttr)?.groupValues?.get(1)?.toIntOrNull() ?: continue
    
                    // iframe'den video linki almak
                    val iframe = div.selectFirst("iframe[src], iframe[data-src]") ?: continue
                    val rawUrl = iframe.attr("src").ifBlank { iframe.attr("data-src") }
                    val videoUrl = if (rawUrl.startsWith("http")) rawUrl else "https:$rawUrl"
    
                    val episode = newEpisode(videoUrl) {
                        this.name = "Bölüm $epNum"
                        this.episode = epNum
                    }
                    episodes.add(episode)
                }
            }
        } else {
            // Eğer data-url yoksa, sayfa içindeki tek tek bölümleri almak
            val links = document.select("a[href*='-bolum']").mapNotNull { a ->
                val href = a.attr("href")
                val epNum = Regex("(\\d+)-bolum").find(href)?.groupValues?.get(1)?.toIntOrNull()
                val fullUrl = fixUrlNull(href)
                if (epNum != null && fullUrl != null) {
                    newEpisode(fullUrl) {
                        this.name = "Bölüm $epNum"
                        this.episode = epNum
                    }
                } else null
            }
            episodes.addAll(links)
        }
    
        // Sonuç döndürülüyor
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.sortedBy { it.episode }) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    Log.d("TRASYA", "data » $data")
    // data zaten doğrudan iframe URL'si ise loadExtractor'a gönderiyoruz
    loadExtractor(data, "$mainUrl/", subtitleCallback, callback)

    return true
}
}
