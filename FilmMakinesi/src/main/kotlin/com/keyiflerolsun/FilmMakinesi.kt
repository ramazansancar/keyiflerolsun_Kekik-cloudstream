// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer


class FilmMakinesi : MainAPI() {
    override var mainUrl              = "https://filmmakinesi.de"
    override var name                 = "FilmMakinesi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    // ! CloudFlare bypass
    override var sequentialMainPage            = true
    override var sequentialMainPageDelay       = 50L
    override var sequentialMainPageScrollDelay = 50L

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler/sayfa/"                                to "Son Filmler",
        "${mainUrl}/film-izle/olmeden-izlenmesi-gerekenler/sayfa/" to "Ölmeden İzle",
        "${mainUrl}/tur/aksiyon/film/sayfa/"                       to "Aksiyon",
        "${mainUrl}/tur/bilim-kurgu/film/sayfa/"                   to "Bilim Kurgu",
        "${mainUrl}/tur/macera/film/sayfa/"                        to "Macera",
        "${mainUrl}/tur/komedi/film/sayfa/"                        to "Komedi",
        "${mainUrl}/tur/romantik/film/sayfa/"                      to "Romantik",
        "${mainUrl}/tur/belgesel/film/sayfa/"                      to "Belgesel",
        "${mainUrl}/tur/fantastik/film/sayfa/"                     to "Fantastik",
        "${mainUrl}/tur/polisiye/film/sayfa/"                      to "Polisiye Suç",
        "${mainUrl}/tur/korku/film/sayfa/"                         to "Korku",
        "${mainUrl}/tur/animasyon/film/sayfa/"                     to "Animasyon",
        "${mainUrl}/tur/gizem/film/sayfa/"                         to "Gizem",
        "${mainUrl}/kanal/netflix/sayfa/"                          to "Netflix",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        
        val home = document.select("div.film-list div.content a.item").mapNotNull { 
            it.toSearchResult() 
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("data-title")
        if (title.isBlank()) return null
        
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.thumbnail-outer > img")?.attr("src"))
        val year = this.selectFirst("div.item-footer div.info > span:first-child")?.text()?.toIntOrNull()
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document
        return document.select("div.film-list div.content a.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
        val description = document.selectFirst("div.info-description p span")?.text()?.trim()
        
        val tags = document.select("div.type a").map { it.text().trim() }
        val rating = document.selectFirst("div.film-bilgileri div.imdb-puani")?.text()?.replace("IMDB:", "")?.trim()?.toRatingInt()
        val year = document.selectFirst("span.date a")?.text()?.replace("Yapım Yılı:", "")?.trim()?.toIntOrNull()

        val durationText = document.selectFirst("div.time")?.text()?.replace("Süre:", "")?.trim()
        val duration = durationText?.split(" ")?.firstOrNull()?.toIntOrNull() ?: 0

        val recommendations = document.select("slick-slide slick-active").mapNotNull { 
            it.toRecommendResult() 
        }
        
        val actors = document.select(".cast-name").map {
            Actor(it.text().trim())
        }

        val trailer = fixUrlNull(document.selectFirst("a.trailer-button")?.attr("data-video_url"))

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val iframe = document.selectFirst("div.player-area iframe")?.attr("src") ?: ""
        
        loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        
        // Alternatif kaynakları da kontrol et
        document.select("div.player-tabs a").forEach { tab ->
            val tabUrl = fixUrlNull(tab.attr("href")) ?: return@forEach
            val tabDoc = app.get(tabUrl).document
            val tabIframe = tabDoc.selectFirst("div.player-area iframe")?.attr("src") ?: return@forEach
            
            loadExtractor(tabIframe, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}
