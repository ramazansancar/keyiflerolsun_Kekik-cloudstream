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
        val poster = fixUrlNull(
            document.selectFirst("img.wp-image-66892")?.attr("data-src")
                ?: document.selectFirst("img.wp-image-66892")?.attr("src")
        )
        val description = document.selectFirst("h2 > p")?.text()?.trim()
        val tags = document.select("div.post-meta a[href*='/category/']").map { it.text() }
    
        val episodes = mutableListOf<Episode>()
    
        // 1. Ana sayfada data-url içeren span'leri al
        val partUrls = document.select("span[data-url]").mapNotNull {
            val relativeUrl = it.attr("data-url")?.trim()
            if (!relativeUrl.isNullOrBlank()) fixUrl(relativeUrl) else null
        }.distinct()
    
        // 2. Her data-url sayfasını işle
        for (partUrl in partUrls) {
            val partDoc = app.get(partUrl).document
    
            // Her tab içindeki ilk iframe'i al ve bölüm numarasını çıkar
            val tabDivs = partDoc.select("div.tab_container > div[id^=tab-]")
            for (tab in tabDivs) {
                val tabId = tab.attr("id")
                val epNumber = Regex("-(\\d+)-bolum$").find(tabId)?.groupValues?.get(1)?.toIntOrNull()
    
                // Sadece ilk iframe'i al
                val iframe = tab.selectFirst("iframe")
                val iframeUrl = iframe?.attr("data-src") ?: iframe?.attr("src")
                val finalUrl = iframeUrl?.let { if (it.startsWith("http")) it else "https:$it" } ?: continue
    
                episodes.add(
                    newEpisode(finalUrl) {
                        name = "Bölüm ${epNumber ?: episodes.size + 1}"
                        episode = epNumber
                    }
                )
            }
        }
    
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.sortedBy { it.episode ?: 0 }) {
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
