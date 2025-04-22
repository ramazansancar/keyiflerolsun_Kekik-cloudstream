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
        val doc = app.get("$mainUrl/arama?q=$query").document
        return doc.select("a.block").mapNotNull {
            val href = it.attr("href")
            val img = it.selectFirst("img")?.attr("data-src") ?: return@mapNotNull null
            val title = it.selectFirst("div.title")?.text() ?: return@mapNotNull null
            MovieSearchResponse(
                name = title,
                url = href,
                apiName = this.name,
                type = TvType.TvSeries,
                posterUrl = img
            )
        }
    }

override suspend fun load(url: String): LoadResponse {
    val doc = app.get(url).document

    val title = doc.selectFirst("div.slide-title h1")?.text() ?: "Bilinmeyen Başlık"
    val poster = doc.selectFirst("div.slide-background")?.attr("data-mobile-src")
    val description = doc.selectFirst("div.slide-description p")?.text()

    val seasons = mutableListOf<SeasonData>()

    // Sezon bilgilerini al
    val seasonOptions = doc.select("select#video-filter-changer > option")

    for (season in seasonOptions) {
        val seasonNum = season.attr("value")
        val seasonName = season.text()

        val seasonUrl = "$url?season=$seasonNum"
        val seasonDoc = app.get(seasonUrl).document

        val episodeElements = seasonDoc.select("div.item-meta")

        val episodes = episodeElements.mapIndexedNotNull { index, epEl ->
            val episodeName = epEl.selectFirst("div.item-meta-title strong")?.text()
            val episodeDesc = epEl.selectFirst("div.item-meta-description")?.text()

            Episode(
                data = seasonUrl,
                name = episodeName?.trim() ?: "Bölüm ${index + 1}",
                episode = index + 1,
                season = seasonNum.toIntOrNull(),
                description = episodeDesc?.trim()
            )
        }

        if (episodes.isNotEmpty()) {
            seasons += SeasonData(seasonNum.toIntOrNull() ?: 1, episodes)
        }
    }

    val allEpisodes = seasons.flatMap { it.episodes }

    return TvSeriesLoadResponse(
        name = title,
        url = url,
        apiName = this.name,
        type = TvType.TvSeries,
        posterUrl = poster,
        year = null,
        plot = description,
        episodes = allEpisodes
    )
}

// Yardımcı sınıf
data class SeasonData(
    val number: Int,
    val episodes: List<Episode>
)


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
                   headers = mapOf("Referer" to url)
                }
            )
	    return true
    }
}

fun String.extractEpisodeNumber(): Int? {
    return Regex("(\\d+)\\.\\s*Bölüm").find(this)?.groupValues?.get(1)?.toIntOrNull()
}

fun String.removeSeasonPrefix(): String {
    return this.replace(Regex("^\\d+\\.\\s*Sezon\\s*"), "").trim()
}
