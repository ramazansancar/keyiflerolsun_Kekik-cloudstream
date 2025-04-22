// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import com.fasterxml.jackson.annotation.JsonProperty

class Tlctr : MainAPI() {
    override var name                 = "TLCtr"
    override var mainUrl              = "https://www.tlctv.com.tr/kesfet"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/a-z"                              to "a-z",
        "${mainUrl}/sira-disi-hayatlar"               to "Sıra Dışı Hayatlar",
        "${mainUrl}/ev-dekorasyon"                    to "Ev Dekorasyon",
        "${mainUrl}/suc-arastirma"                    to "Suç Araştırma",
        "${mainUrl}/yasam"                            to "Yaşam",
        "${mainUrl}/evlilik"                          to "Evlilik",
        "${mainUrl}/yemek"                            to "Yemek",
        "${mainUrl}/belgesel"                         to "Belgesel",
        "${mainUrl}/korelendik"                       to "Korelendik",
    )
	
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document

        // Poster ve başlıkları içeren grid yapısının elemanlarını çek
        val home = doc.select("section.grid.dyn-content div.poster")
		.mapNotNull { it.toSearchResult() }

    return newHomePageResponse(request.name, home)
}

    private fun Element.toSearchResult(): SearchResponse? {
                val href = this.selectFirst("a")?.attr("href") ?: "return null"
                val img = this.selectFirst("img")?.attr("src") ?: "return null"
                val title = this.selectFirst("img")?.attr("alt") ?: "return null"
				
                return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = img
        }
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

override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val res = app.get(data).text

        // referenceId’yi JS içinden yakala
        val regex = Regex("referenceId\\s*:\\s*['\"](EHD_\\d+)['\"]")
        val referenceId = regex.find(res)?.groupValues?.get(1) ?: return false
		Log.d("TLC", "referenceId » $referenceId")

        val videoUrl = "https://dygvideo.dygdigital.com/api/redirect?PublisherId=20&ReferenceId=$referenceId&SecretKey=NtvApiSecret2014*&.m3u8"
        Log.d("TLC", "videoUrl » $videoUrl")
		
            callback.invoke(
                newExtractorLink(
                    name = "tlctr",
                    source = "TLC",
                    url = videoUrl,
                    type   = ExtractorLinkType.M3U8
                ) {
                   quality = Qualities.Unknown.value
                   headers = mapOf("Referer" to data)
                }
            )
	    return true
    }
}
