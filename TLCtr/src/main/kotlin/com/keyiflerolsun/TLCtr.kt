// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
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
        val doc = app.get(mainUrl).document

        // Poster ve başlıkları içeren grid yapısının elemanlarını çek
        val home = doc.select("section.grid.dyn-content div.poster").mapNotNull{ it.toSearchResult() }

    return newHomePageResponse(request.name, home)
}

    private fun Element.toSearchResult(): SearchResponse? {
                val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = it.selectFirst("img")?.attr("src") ?: return@mapNotNull null
                val title = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
				
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

    val episodes = listOf(
        Episode(
            data = url,
            name = title
        )
    )

    return TvSeriesLoadResponse(
        name = title,
        url = url,
        apiName = this.name,
        type = TvType.TvSeries,
        posterUrl = poster,
        year = null,
        plot = description,
        episodes = episodes
    )
}

override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val res = app.get(data).text

        // referenceId’yi JS içinden yakala
        val regex = Regex("referenceId\\s*:\\s*['\"](EHD_\\d+)['\"]")
        val referenceId = regex.find(res)?.groupValues?.get(1) ?: return false
		Log.d("TLC", "referenceId » $referenceId")

        val metaUrl = "https://www.tlctv.com.tr/player/info?referenceId=$referenceId"
		Log.d("TLC", "metaUrl » $metaUrl")
        val json = app.get(metaUrl).parsedSafe<TLCMeta>() ?: return false
		Log.d("TLC", "json » $json")

        val file = json.flavors?.find { it.format == "hls" }?.url
          if (file != null) {
            callback.invoke(
                newExtractorLink(
                    name = "tlctr",
                    source = "TLC",
                    url = file,
                    type   = ExtractorLinkType.M3U8
                ) {
                   quality = Qualities.Unknown.value
                   headers = mapOf("Referer" to url)
                }
            )
        }
	    return true
    }

    data class TLCMeta(
        @JsonProperty("flavors")
        val flavors: List<TLCFlavor>? = null
    )

    data class TLCFlavor(
        @JsonProperty("format")
        val format: String? = null,
        @JsonProperty("url")
        val url: String? = null
    )
}