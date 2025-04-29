// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element


class FilmMakinesi : MainAPI() {
    override var mainUrl = "https://filmmakinesi.de"
    override var name = "FilmMakinesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ! CloudFlare bypass
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 50L
    override var sequentialMainPageScrollDelay = 50L

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler/sayfa/" to "Son Filmler",
        "${mainUrl}/film-izle/olmeden-izlenmesi-gerekenler/sayfa/" to "Ölmeden İzle",
        "${mainUrl}/tur/aksiyon/film/sayfa/" to "Aksiyon",
        "${mainUrl}/tur/bilim-kurgu/film/sayfa/" to "Bilim Kurgu",
        "${mainUrl}/tur/macera/film/sayfa/" to "Macera",
        "${mainUrl}/tur/komedi/film/sayfa/" to "Komedi",
        "${mainUrl}/tur/romantik/film/sayfa/" to "Romantik",
        "${mainUrl}/tur/belgesel/film/sayfa/" to "Belgesel",
        "${mainUrl}/tur/fantastik/film/sayfa/" to "Fantastik",
        "${mainUrl}/tur/polisiye/film/sayfa/" to "Polisiye Suç",
        "${mainUrl}/tur/korku/film/sayfa/" to "Korku",
        "${mainUrl}/tur/animasyon/film/sayfa/" to "Animasyon",
        "${mainUrl}/tur/gizem/film/sayfa/" to "Gizem",

        "${mainUrl}/yabanci-dizi-izle/sayfa/" to "Son Diziler",
        "${mainUrl}/tur/aksiyon/dizi/sayfa/" to "Aksiyon Dizi",
        "${mainUrl}/tur/bilim-kurgu/dizi/sayfa/" to "Bilim Kurgu Dizi",
        "${mainUrl}/tur/macera/dizi/sayfa/" to "Macera Dizi",
        "${mainUrl}/tur/komedi/dizi/sayfa/" to "Komedi Dizi",
        "${mainUrl}/tur/romantik/dizi/sayfa/" to "Romantik Dizi",
        "${mainUrl}/tur/belgesel/dizi/sayfa/" to "Belgesel Dizi",
        "${mainUrl}/tur/fantastik/dizi/sayfa/" to "Fantastik Dizi",
        "${mainUrl}/tur/polisiye/dizi/sayfa/" to "Polisiye Dizi",
        "${mainUrl}/tur/korku/dizi/sayfa/" to "Korku Dizi",
        "${mainUrl}/tur/animasyon/dizi/sayfa/" to "Animasyon Dizi",
        "${mainUrl}/tur/gizem/dizi/sayfa/" to "Gizem Dizi",
        //"${mainUrl}/kanal/netflix/sayfa/"                          to "Netflix",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cleanedUrl = request.data.removeSuffix("/")
        val url = if (page > 1) {
            "$cleanedUrl/$page"
        } else {
            cleanedUrl.replace(Regex("/sayfa/?$"), "")
        }

        val document = app.get(
            url, headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to mainUrl
            )
        ).document

        val home = document.select("div.film-list div.item-relative")
            .mapNotNull { it.toSearchResult() }

        Log.d("FLMM", "Toplam film: ${home.size}")
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("a.item") ?: return null
        val title = aTag.attr("data-title").takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(aTag.attr("href")) ?: return null
        val posterUrl = fixUrlNull(aTag.selectFirst("img")?.attr("src"))

        Log.d("FLMM", "Film: $title, Href: $href, Poster: $posterUrl")

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.select("a").last()?.text() ?: return null
        val href = fixUrlNull(this.select("a").last()?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama/?s=${query}").document

        return document.select("div.film-list div.item-relative").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.select("div.info-description p").last()?.text()?.trim()
        val tags = document.selectFirst("dt:contains(Tür:) + dd")?.text()?.split(", ")
        val rating =
            document.selectFirst("dt:contains(IMDB Puanı:) + dd")?.text()?.trim()?.toRatingInt()
        val year =
            document.selectFirst("dt:contains(Yapım Yılı:) + dd")?.text()?.trim()?.toIntOrNull()

        val durationElement =
            document.select("dt:contains(Film Süresi:) + dd time").attr("datetime")
        // ? ISO 8601 süre formatını ayrıştırma (örneğin "PT129M")
        val duration = if (durationElement.startsWith("PT") && durationElement.endsWith("M")) {
            durationElement.drop(2).dropLast(1).toIntOrNull() ?: 0
        } else {
            0
        }

        val recommendations =
            document.select("div.film-list div.item-relative").mapNotNull { it.toRecommendResult() }
        val actors =
            document.selectFirst("dt:contains(Oyuncular:) + dd")?.text()?.split(", ")?.map {
                Actor(it.trim())
            }

        val trailer =
            fixUrlNull(document.selectXpath("//iframe[@title='Fragman']").attr("data-src"))

        if (url.contains("/dizi/")) {
            val eps = mutableListOf<Episode>()
            document.select("div#sezonTabContent div.col-12").forEach { it ->
                val epHref = it.selectFirst("a")?.attr("href")
                val epName = it.selectFirst("div.ep-details span")?.text()
                val epTitle = it.selectFirst("div.ep-title")?.text()?.split("/")
                val epSeason = epTitle!![0].replace(". Sezon", "").trim().toIntOrNull()
                val epEpisode = epTitle[1].replace(". Bölüm", "").trim().toIntOrNull()
                eps.add(
                    newEpisode(epHref) {
                        this.name = epName
                        this.season = epSeason
                        this.episode = epEpisode
                    })
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                addTrailer(trailer)
            }
        }
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


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("FLMM", "data » $data")
        val document = app.get(data).document
        val iframe = document.selectFirst("iframe")?.attr("data-src") ?: ""
        Log.d("FLMM", iframe)
        loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        document.select("div.video-parts a").forEach {
            val iframeUrl = it.attr("data-video_url")
            val urlName = it.text()
            loadExtractor(iframeUrl, "${mainUrl}/", subtitleCallback, callback)
        }
        return true
    }
}
