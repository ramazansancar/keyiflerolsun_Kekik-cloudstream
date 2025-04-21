// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class Tlctr : MainAPI() {
    override var name                 = "TLCtr"
    override var mainUrl              = "https://www.tlctv.com.tr/kesfet"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

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
        val items = doc.select("section.grid.dyn-content div.poster")
            .mapNotNull {
                val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = it.selectFirst("img")?.attr("src") ?: return@mapNotNull null
                val title = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
                MovieSearchResponse(
                    name = title,
                    url = href,
                    apiName = this.name,
                    type = TvType.TvSeries,
                    posterUrl = img
                )
            }
        return HomePageResponse(listOf(HomePageList("Bölümler", items)))
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

        val metaUrl = "$mainUrl/player/info?referenceId=$referenceId"
		Log.d("TLC", "metaUrl » $metaUrl")
        val json = app.get(metaUrl).parsedSafe<TLCMeta>() ?: return false
		Log.d("TLC", "json » $json")

        json.sources?.forEach { source ->
            val file = source.file ?: return@forEach
            val label = source.label ?: "Default"
            callback(
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
        val sources: List<TLCSource>?
    )

    data class TLCSource(
        val file: String?,
        val label: String?
    )
}