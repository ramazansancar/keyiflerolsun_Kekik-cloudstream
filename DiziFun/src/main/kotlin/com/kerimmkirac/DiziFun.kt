
package com.kerimmkirac

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.R.string.*
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLDecoder

class DiziFun : MainAPI() {
    override var mainUrl = "https://dizifun6.com"
    override var name = "DiziFun"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler" to "Diziler",
        "${mainUrl}/filmler" to "Filmler",
        "${mainUrl}/netflix" to "NetFlix Dizileri",
        "${mainUrl}/exxen" to "Exxen Dizileri",
        "${mainUrl}/disney" to "Disney+ Dizileri",
        "${mainUrl}/tabii-dizileri" to "Tabii Dizileri",
        "${mainUrl}/blutv" to "BluTV Dizileri",
        "${mainUrl}/todtv" to "TodTV Dizileri",
        "${mainUrl}/gain" to "Gain Dizileri",
        "${mainUrl}/hulu" to "Hulu Dizileri",
        "${mainUrl}/primevideo" to "PrimeVideo Dizileri",
        "${mainUrl}/hbomax" to "HboMax Dizileri",
        "${mainUrl}/paramount" to "Paramount+ Dizileri",
        
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}?p=${page}").document
        val home = document.select("div.uk-width-1-3").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h5.uk-panel-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.platformmobile img")?.attr("src"))

        // Burada tür kontrolü yapıyoruz
        val type = if (href.contains("/film/")) TvType.Movie else TvType.TvSeries

        return newTvSeriesSearchResponse(title, href, type) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama?query=${query}").document
        return document.select("div.uk-width-1-3").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h5")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.uk-overlay img")?.attr("src"))

        // Tür kontrolü eklendi
        val type = if (href.contains("/film/")) TvType.Movie else TvType.TvSeries

        return newTvSeriesSearchResponse(title, href, type) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.text-bold")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("img.responsive-img")?.attr("src"))
        val description = document.selectFirst("p.text-muted")?.text()?.trim()
        val year = document.select("ul.subnav li")
            .firstOrNull { it.text().contains("Dizi Yılı") || it.text().contains("Film Yılı") }
            ?.ownText()
            ?.filter { it.isDigit() }
            ?.toIntOrNull()
        val tags = document.select("div.series-info")
            .map { it.text() }
            .flatMap { text ->
                text.removePrefix("Türü:")
                    .split(",")
                    .map { it.trim() }
            }
        val actors = document.select("div.actor-card").map { card ->
            val name = card.selectFirst("span.actor-name")?.text()?.trim() ?: return@map null
            val image = fixUrlNull(card.selectFirst("img")?.attr("src"))
            val actor = Actor(name, image)
            ActorData(
                actor = actor,
            )
        }.filterNotNull()
        val trailer = Regex("""embed/([^?"]+)""").find(document.html())?.groupValues?.get(1)
            ?.let { "https://www.youtube.com/embed/$it" }

        val type = if (url.contains("/film/")) TvType.Movie else TvType.TvSeries

        val score = document.selectFirst("div.imdb-score")?.text()

        if (type == TvType.Movie) {
            val movieData = url
            return newMovieLoadResponse(title, url, type, movieData) {
                this.posterUrl = poster
                this.year = year
                this.tags = tags
                this.plot = description
                this.actors = actors
                this.score  = Score.from10(score)
                addTrailer(trailer)
            }
        } else {
            val episodes = document.select("div.season-detail").flatMap { seasonDiv ->
                val seasonId = seasonDiv.attr("id") // örnek: "season-1"
                val season = seasonId.removePrefix("season-").toIntOrNull() ?: 1
                seasonDiv.select("div.bolumtitle a").mapNotNull { aTag ->
                    val rawHref = aTag.attr("href")
                    val href = if (rawHref.startsWith("?")) "$url$rawHref"
                    else aTag.absUrl("href").ifBlank { fixUrl(rawHref) }

                    if (href.isBlank()) return@mapNotNull null
                    val episodeDiv = aTag.selectFirst("div.episode-button") ?: return@mapNotNull null
                    val name = episodeDiv.text().trim()
                    val episodeNumber = name.filter { it.isDigit() }.toIntOrNull() ?: 1
                    newEpisode(href) {
                        this.name = name
                        this.season = season
                        this.episode = episodeNumber
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
                this.year = year
                this.tags = tags
                this.plot = description
                this.actors = actors
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("kraptor_Dizifun","data = $data")
        val document = app.get(data).text

        val regex = Regex(
            pattern = "decodeURIComponent\\(hexToString\\w*\\(\"([^\"]*)\"\\)\\)",
            options = setOf(RegexOption.IGNORE_CASE)
        )

        val regMatch = regex.findAll(document)

        regMatch.forEach { match ->
            val linkhex = match.groupValues[1]
            Log.d("kraptor_Dizifun","linkhex = $linkhex")
            val linkler = hexToString(linkhex)
            val iframe   = httpsify(linkler)
            Log.d("kraptor_Dizifun","iframe = $iframe")
            loadExtractor(iframe, referer = "${mainUrl}/" , subtitleCallback, callback)
        }
        return true
    }
}

fun hexToString(hex: String): String {
    val result = StringBuilder()
    for (i in 0 until hex.length step 2) {
        val endIndex = minOf(i + 2, hex.length)
        result.append(hex.substring(i, endIndex).toInt(16).toChar())
    }
    return URLDecoder.decode(result.toString(), "UTF-8")
}