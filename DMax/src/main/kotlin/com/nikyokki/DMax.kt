package com.nikyokki

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DMax : MainAPI() {
    override var mainUrl              = "https://www.dmax.com.tr"
    override var name                 = "DMax"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:138.0) Gecko/20100101 Firefox/138.0",
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.5",
        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "X-Requested-With" to "XMLHttpRequest",
        "Origin" to mainUrl,
        "DNT" to "1",
        "Sec-GPC" to "1",
        "Connection" to "keep-alive",
        "Referer" to mainUrl,
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "Priority" to "u=0"
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/kesfet"      to "Öne Çıkanlar",
        "${mainUrl}/kesfet/a-z"      to "A-Z",
        "${mainUrl}/kesfet/turbo"      to "Turbo",
        "${mainUrl}/kesfet/dogayla-ic-ice"      to "Doğayla İç içe",
        "${mainUrl}/kesfet/zorlu-isler"      to "Zorlu İşler",
        "${mainUrl}/kesfet/spor"      to "Spor",
        "${mainUrl}/kesfet/belgesel"      to "Belgesel",
        "${mainUrl}/kesfet/nasil-yapiliyor"      to "Nasıl Yapılıyor?",
        "${mainUrl}/kesfet/popcorn-kusagi"      to "Popcorn Kuşağı",    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) {
            val url = "$mainUrl/ajax/more"
            val pageDocument = app.post(url, headers = headers, data = mapOf("type" to "discover",
                "slug" to request.data.split("/").last(), "page" to page.toString())).document
            val home     = pageDocument.select("div.poster").mapNotNull { it.toMainPageResult() }
            return newHomePageResponse(request.name, home)
        }
        val document = app.get(request.data).document
        val home     = document.select("section.grid.dyn-content div.poster").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val doc = app.post("$mainUrl/ajax/search", headers = headers, data = mapOf("query" to query)).document
        return doc.select("section.posters div.poster").mapNotNull {
            it.toMainPageResult()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("div.slide-title h1")?.text() ?: "Bilinmeyen Başlık"
        val poster = doc.selectFirst("div.slide-background")?.attr("data-mobile-src")
        val description = doc.selectFirst("div.slide-description p")?.text()
        val programId = doc.selectFirst("li.current a")?.attr("data-program-id") ?: ""
        val episodeses = mutableListOf<Episode>()
        (doc.select("select#video-filter-changer option")).forEach { it ->
            val szn = it.attr("value").toIntOrNull()
            val page = app.post("$mainUrl/ajax/more", headers = headers, data =
            mapOf("type" to "episodes", "program_id" to programId.toString(), "page" to "0", "season" to szn.toString())).document
            val hre = page.selectFirst("div.item a")?.attr("href")
            val href = hre?.split("-")
            val epNum = href?.get(href.size-2)?.toIntOrNull() ?: 1
            for (i in epNum downTo 1) {
                val hree = hre?.replace(epNum.toString(), i.toString())
                episodeses.add(newEpisode(hree) {
                    this.season = szn
                    this.episode = i
                })
            }
        }
        if (episodeses.size == 0) {
            val docu = app.get("$url/kisa-videolar").document
            docu.select("div.items a").forEach { it ->
                val href = it.attr("href")
                val img = it.selectFirst("img")?.attr("src")
                val epName = it.selectFirst("strong")?.text() + " - Kısa Video"
                episodeses.add(newEpisode(href) {
                    this.posterUrl = img
                    this.name = epName
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
            this.posterUrl = poster
            this.plot = description
        }

    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("TLC", "data » $data")
        val document = app.get(data).document
        val videoCode = document.selectFirst("div.videp-player-container div")?.attr("data-video-code")
        Log.d("TLC", "videoCode » $videoCode")
        val vidUrl = "https://dygvideo.dygdigital.com/api/redirect?PublisherId=27&ReferenceId=$videoCode&SecretKey=NtvApiSecret2014*"
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = vidUrl,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this. quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
