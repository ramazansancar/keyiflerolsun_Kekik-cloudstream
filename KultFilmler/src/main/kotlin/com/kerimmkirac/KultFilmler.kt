package com.kerimmkirac

import android.util.Base64
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class KultFilmler : MainAPI() {
    override var mainUrl              = "https://kultfilmler.net"
    override var name                 = "KultFilmler"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/page/"                                      to "Son Filmler",
        "${mainUrl}/category/aile-filmleri-izle/page/"		    to "Aile",
        "${mainUrl}/category/aksiyon-filmleri-izle/page/"	    to "Aksiyon",
        "${mainUrl}/category/animasyon-filmleri-izle/page/"	    to "Animasyon",
        "${mainUrl}/category/belgesel-izle/page/"			    to "Belgesel",
        "${mainUrl}/category/bilim-kurgu-filmleri-izle/page/"   to "Bilim Kurgu",
        "${mainUrl}/category/biyografi-filmleri-izle/page/"	    to "Biyografi",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home     = document.select("div.movie-box").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.name a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div.name a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.img img")?.attr("src"))
        val puan      = this.selectFirst("div.rating")?.text()?.trim()

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.score     = Score.from10(puan)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.score     = Score.from10(puan)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=${query}").document

        return document.select("div.movie-box").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("div.film h1")?.text()?.trim() ?: document.selectFirst("h1.film")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description     = document.selectFirst("div.description")?.text()?.trim()
        var tags            = document.select("ul.post-categories a").map { it.text() }
        val rating          = document.selectFirst("div.imdb-count")?.text()?.trim()?.split(" ")?.first()
        val year            = Regex("""(\d+)""").find(document.selectFirst("li.release")?.text()?.trim() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val duration        = Regex("""(\d+)""").find(document.selectFirst("li.time")?.text()?.trim() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val recommendations = document.select("div.movie-box").mapNotNull { it.toSearchResult() }
        val actors          = document.select("[href*='oyuncular']").map {
            Actor(it.text())
        }

        if (url.contains("/dizi/")) {
            tags  = document.select("div.category a").map { it.text() }

            val episodes = document.select("div.episode-box").mapNotNull {
                val epHref    = fixUrlNull(it.selectFirst("div.name a")?.attr("href")) ?: return@mapNotNull null
                val ssnDetail = it.selectFirst("span.episodetitle")?.ownText()?.trim() ?: return@mapNotNull null
                val epDetail  = it.selectFirst("span.episodetitle b")?.ownText()?.trim() ?: return@mapNotNull null
                val epName    = "$ssnDetail - $epDetail"
                val epSeason  = ssnDetail.substringBefore(". ").toIntOrNull()
                val epEpisode = epDetail.substringBefore(". ").toIntOrNull()

                newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.score = Score.from10(rating)
                this.duration        = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.score = Score.from10(rating)
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    private fun getIframe(sourceCode: String): String {
        // val atobKey = Regex("""atob\("(.*)"\)""").find(sourceCode)?.groupValues?.get(1) ?: return ""

        // return Jsoup.parse(String(Base64.decode(atobKey))).selectFirst("iframe")?.attr("src") ?: ""

        val atob = Regex("""PHA\+[0-9a-zA-Z+/=]*""").find(sourceCode)?.value ?: return ""

        val padding    = 4 - atob.length % 4
        val atobPadded = if (padding < 4) atob.padEnd(atob.length + padding, '=') else atob

        val iframe = Jsoup.parse(String(Base64.decode(atobPadded, Base64.DEFAULT), Charsets.UTF_8))

        return fixUrlNull(iframe.selectFirst("iframe")?.attr("src")) ?: ""
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("kraptor_${this.name}", "data » $data")
        val document = app.get(data).document
        val iframes  = mutableSetOf<String>()

        val mainFrame = getIframe(document.html())
        Log.d("kraptor_${this.name}", "mainFrame » $mainFrame")
        iframes.add(mainFrame)

        document.select("div.parts-middle").forEach {
            val alternatif = it.selectFirst("a")?.attr("href")
            if (alternatif != null) {
                val alternatifDocument = app.get(alternatif).document
                val alternatifFrame    = getIframe(alternatifDocument.html())
                Log.d("kraptor_${this.name}", "alternatifFrame » $alternatifFrame")
                iframes.add(alternatifFrame)
            }
        }

        for (iframe in iframes) {
            loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
        return true
    }
}
