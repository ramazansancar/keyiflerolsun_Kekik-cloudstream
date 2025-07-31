

package com.kerimmkirac


import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class BirAsyaDizi : MainAPI() {
    override var mainUrl              = "https://www.birasyadizi.com"
    override var name                 = "BirAsyaDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler/aile-dizi/"                 to "Aile dizi",
        "${mainUrl}/diziler/aksiyon-dizi/"              to "Aksiyon Dizi",
        "${mainUrl}/diziler/anime/"                     to "Anime",
        "${mainUrl}/diziler/arkadaslik-dizi/"           to "Arkadaşlık Dizi",
        
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}page/$page/").document
        val home     = document.select("div.frag-k.yedi.yan").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.attr("title") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.frag-k").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.attr("title") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.tab-icerik img")?.attr("title") ?: return null
        val poster = fixUrlNull(document.selectFirst("div.tab-icerik img")?.attr("data-src"))
        val description = document.selectFirst("div.aciklama div.scroll-liste")?.text()?.trim()
        val year = document.selectFirst("div.extra span.C a")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("ol.gizli li a").map { it.text() }
        val rating = document.selectFirst("span.tum-gor")?.text()?.trim()
        val recommendations = document.select("div.sag-vliste li").mapNotNull { it.toRecommendationResult() }
//        Log.d("kerimmkirac_$name", "ul.scroll-liste = ${document.select("li.szn")}")
        val episodes = document.select("li.szn").map { bolum ->
//            Log.d("kerimmkirac_$name", "bolum = ${bolum}")
            val epName = bolum.selectFirst("div.baslik a")?.text()?.trim()
//            Log.d("kerimmkirac_$name", "epName = ${epName}")
            val epEpisode = bolum.selectFirst("div.resim a")?.attr("href")
                ?.substringBeforeLast("-bolum")
                ?.substringAfterLast("-")
                ?.toIntOrNull()
//            Log.d("kerimmkirac_$name", "epEpisode = ${epEpisode}")
//            val epSeason    = bolum.selectFirst("div.resim a")?.attr("href")
            val epHref = fixUrlNull(bolum.selectFirst("div.resim a")?.attr("href"))
//            Log.d("kerimmkirac_$name", "epHref = ${epHref}")
            newEpisode(epHref, {
                this.episode = epEpisode
                this.name = epName
            })
        }

        if (episodes.isEmpty()) {
            return newMovieLoadResponse(title, url, TvType.AsianDrama, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
            }
        } else {
            return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("span.baslik")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("kerimmkirac_$name", "data » ${data}")
        val document = app.get(data).document

//        Log.d("kerimmkirac_$name", "document » ${document}")

        val iframe   = document.select("iframe")

        val iframeVid = fixUrlNull(iframe.attr("vdo-src")).toString()

        Log.d("kerimmkirac_$name", "iframeVid » ${iframeVid}")

         loadExtractor(iframeVid, "${mainUrl}/", subtitleCallback, callback)

        return true
    }
}