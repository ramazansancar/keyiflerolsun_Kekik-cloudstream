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
        "${mainUrl}/category/yeni-eklenen-bolumler/" to "Yeni Eklenen Bölümler",
        "${mainUrl}/category/final-yapan-diziler/" to "Final Yapan Diziler",
        "${mainUrl}/category/kore-dizileri-izle-guncel/" to "Kore Dizileri",
        "${mainUrl}/category/cin-dizileri/" to "Çin Dizileri",
        "${mainUrl}/category/tayland-dizileri/" to "TaylandDizileri",
        "${mainUrl}/category/japon-dizileri/" to "Japon Diziler",
        "${mainUrl}/category/endonezya-dizileri/" to "Endonezya Diziler",
        "${mainUrl}/category/seri-diziler/" to "Seri Diziler",
        "${mainUrl}/category/devam-eden-diziler/" to "Devam eden Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page").document
        val home     = document.select("div.post-container").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.thumbnail img")?.attr("src"))

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
    val poster = fixUrlNull(document.selectFirst("div.aligncenter img")?.attr("src"))
    val description = document.selectFirst("div.entry-content > p")?.text()?.trim()
    val tags = document.select("div.post-meta a[href*='/category/']").map { it.text() }

    val episodeses = mutableListOf<Episode>()

    for (bolum in document.select("span[data-url*='-bolum']")) {
        val epPath = bolum.attr("data-url")?.trim() ?: continue
        val epHref = fixUrlNull(mainUrl + epPath) ?: continue
        val epName = bolum.text()?.trim() ?: continue
        // "1-4. Bölüm" için ilk numarayı (1) al
        val epEpisode = Regex("(\\d+)(?:-\\d+)?\\.\\s*Bölüm").find(epName)?.groupValues?.get(1)?.toIntOrNull()

        val newEpisode = newEpisode(epHref) {
            this.name = epName
            this.episode = epEpisode
        }
        episodeses.add(newEpisode)
    }

    return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
        this.posterUrl = poster
        this.plot = description
        this.tags = tags
    }
}

     override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("TRASYA", "data » $data")
        val document = app.get(data).document

            val iframe = document.selectFirst("iframe")?.attr("src")
            Log.d("TRASYA", "iframe » $iframe")

         if (iframe != null) {
                 loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
             } else {
                Log.d("TRASYA", "Iframe bulunamadı")
                return false
        }

        return true
    }
}
