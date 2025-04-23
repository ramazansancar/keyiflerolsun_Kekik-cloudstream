// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.*
import org.jsoup.Jsoup

class TRanimaci : MainAPI() {
    override var mainUrl              = "https://tranimaci.com"
    override var name                 = "TRanimaci"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/library/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 250L  // ? 0.25 saniye
    override var sequentialMainPageScrollDelay = 250L  // ? 0.25 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/category/action"                                   to "Aksiyon",
        "${mainUrl}/category/cars"                                     to "Arabalar",
        "${mainUrl}/category/supernatural"                             to "Doğaüstü",
        "${mainUrl}/category/drama"                                    to "Dram",
        "${mainUrl}/category/ecchi"                                    to "Ecchi",
        "${mainUrl}/category/fantasy"                                  to "Fantastik",
        "${mainUrl}/category/mystery"                                  to "Gizem",
        "${mainUrl}/category/comedy"                                   to "Komedi",
        "${mainUrl}/category/horror"                                   to "Korku",
        "${mainUrl}/category/adventure"                                to "Macera",
        "${mainUrl}/category/mecha"                                    to "Mecha",
        "${mainUrl}/category/music"                                    to "Müzik",
        "${mainUrl}/category/romance"                                  to "Romantik",
        "${mainUrl}/category/sports"                                   to "Spor",

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("article.bs div.bsx").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.ts-post-image img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/search?name=${query}").document

        return document.select("article.bs div.bsx").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.thumb img")?.attr("src"))
        val description = document.selectFirst("div.anime-description")?.text()?.trim()
        val tags        = document.select("div#genxed a[href*='/category']").map { it.text() }

        val episodeses = mutableListOf<Episode>()

        for (bolum in document.select("div.eplister ul li a")) {
            val epHref = fixUrlNull(bolum.attr("href")) ?: continue
            val epName = bolum.selectFirst(".epl-title")?.text()?.trim() ?: continue
            val epEpisode = epName.replace("Bölüm", "").trim().toIntOrNull()
	
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
        Log.d("ANI", "data » $data")
        val document = app.get(data).document

        val script = document.select("script").firstOrNull { it.html().contains("video_source") }
        Log.d("ANI", "script » ${script?.html()}")

        if (script != null) {
            // Extract the JSON string from the script
            val scriptContent = script.html()
            val jsonMatch = Regex("""video_source\s*=\s*`(\[.*?\])`""").find(scriptContent)?.groups?.get(1)?.value
            Log.d("ANI", "jsonMatch » $jsonMatch")

            if (jsonMatch != null) {
                try {
                    // Parse the JSON array
                    val jsonArray = JSONArray(jsonMatch)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val url = jsonObject.getString("url")
                        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = url,
                ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
                this. quality = Qualities.Unknown.value
            }
        )
    }
        return true
    }
	}
}
