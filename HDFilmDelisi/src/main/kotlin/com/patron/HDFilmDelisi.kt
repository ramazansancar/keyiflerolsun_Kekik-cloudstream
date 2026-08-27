//Eklenti kraptor reposundan çekilip düzenlenmiştir.
package com.patron

import android.content.SharedPreferences
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.app
import com.lagradost.api.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.ShowStatus

data class LinkData(
    @JsonProperty("url")    val url: String?,
    @JsonProperty("type")   val type: String?,
    @JsonProperty("season") val season: Int?,
    @JsonProperty("episode")val episode: Int?,
    @JsonProperty("tmdbId") val tmdbId: Int?,
    @JsonProperty("imdbId") val imdbId: String?
)

data class ExternalIds(
    @JsonProperty("imdb_id") val imdb_id: String?,
    @JsonProperty("id")      val id: Int?
)

class HDFilmDelisi(private val sharedPref: SharedPreferences? = null) : MainAPI() {

    override var mainUrl    = getDomain(sharedPref)
    override var name       = "HDFilmDelisi"
    override val hasMainPage    = true
    override val hasQuickSearch = false
    override var lang       = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        mainUrl                    to "Son Eklenen Filmler",
        "$mainUrl/tur/aile"        to "Aile",
        "$mainUrl/tur/aksiyon"     to "Aksiyon",
        "$mainUrl/tur/animasyon"   to "Animasyon",
        "$mainUrl/tur/belgeseller" to "Belgeseller",
        "$mainUrl/tur/bilim-kurgu" to "Bilim Kurgu",
        "$mainUrl/tur/dram"        to "Dram",
        "$mainUrl/tur/fantastik"   to "Fantastik",
        "$mainUrl/tur/gerilim"     to "Gerilim",
        "$mainUrl/tur/gizem"       to "Gizem",
        "$mainUrl/tur/komedi"      to "Komedi",
        "$mainUrl/tur/korku"       to "Korku",
        "$mainUrl/tur/macera"      to "Macera",
        "$mainUrl/tur/romantik"    to "Romantik",
        "$mainUrl/tur/savas"       to "Savaş",
        "$mainUrl/tur/suc"         to "Suç",
        "$mainUrl/tur/tarih"       to "Tarih",
        "$mainUrl/tur/yerli"       to "Yerli"
    )

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val headers   = mapOf("User-Agent" to userAgent)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (!HDFilmDelisiHelper.isAllowedVersion) {
            return newHomePageResponse(request.name, emptyList(), false)
        }
        val apiUrl = if (request.data == mainUrl) {
            "$mainUrl/api/films?page=$page&sort=newest&limit=20"
        } else {
            val slug = request.data.substringAfterLast("/")
            "$mainUrl/api/films/category/$slug?page=$page&sort=newest&limit=20"
        }
        return try {
            val response = app.get(apiUrl, headers = headers)
            val text     = response.text
            val items: List<SearchResponse> = try {
                parseJson<CategoryResponse>(text).films?.mapNotNull { toSearchItem(it) } ?: emptyList()
            } catch (e: Exception) {
                try {
                    parseJson<HDFilmSearchResponse>(text).results?.mapNotNull { toSearchItem(it) } ?: emptyList()
                } catch (e2: Exception) {
                    emptyList()
                }
            }
            newHomePageResponse(request.name, items, items.size >= 20)
        } catch (e: Exception) {
            throw ErrorLoadingException("Siteye ulaşılamıyor: ${e.message}")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (!HDFilmDelisiHelper.isAllowedVersion) return emptyList()
        return try {
            val url  = "$mainUrl/api/search?q=$query"
            val text = app.get(url, headers = headers).text
            parseJson<HDFilmSearchResponse>(text).results?.mapNotNull { toSearchItem(it) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    private fun toSearchItem(item: HDFilmItem): SearchResponse? {
        val title = item.baslik ?: return null
        val slug  = item.slug  ?: return null
        return newMovieSearchResponse(title, "$mainUrl/film/$slug", TvType.Movie) {
            posterUrl = fixUrlNull(item.afis)
            year      = item.yayinYili
            score     = Score.from10(item.imdbPuani)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        if (!HDFilmDelisiHelper.isAllowedVersion) return null
        val document = app.get(url, headers = headers).document

        val jsonLdRaw = document.selectFirst("script[type='application/ld+json']")?.html() ?: ""
        val jsonLd    = runCatching { parseJson<Map<String, Any>>(jsonLdRaw) }.getOrNull()

        val name     = document.selectFirst("h1.film-title, h1")?.text()?.trim() ?: ""
        val poster   = document.selectFirst("meta[property='og:image']")?.attr("content")
            ?: document.selectFirst("img.film-poster, img.poster")?.attr("src")
        val plot     = document.selectFirst("div.film-description, div.synopsis, p.description")?.text()
        val yearStr  = document.selectFirst("span.film-year, span.year, a[href*='/yil/']")?.text()
        val year     = yearStr?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val rating   = document.selectFirst("span.imdb-rating, span.rating")
            ?.text()?.replace(",", ".")?.trim()?.toDoubleOrNull()
        val duration = document.selectFirst("span.film-duration, span.duration")
            ?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val tags     = document.select("a[href*='/tur/']").map { it.text().trim() }.filter { it.isNotBlank() }

        val isTv     = document.selectFirst("div.season-list, div.sezonlar, div.episodes") != null
        val tvType   = if (isTv) TvType.TvSeries else TvType.Movie

        val tmdbId: Int? = try {
            val idEl = document.selectFirst("meta[name='tmdb-id'], span[data-tmdb]")
            idEl?.attr("content")?.toIntOrNull() ?: idEl?.attr("data-tmdb")?.toIntOrNull()
        } catch (e: Exception) { null }

        val imdbId: String? = try {
            document.selectFirst("a[href*='imdb.com/title/']")
                ?.attr("href")
                ?.substringAfter("title/")
                ?.substringBefore("/")
        } catch (e: Exception) { null }

        val tmdb = TmdbHelper.getTmdbDataById(tmdbId, imdbId, tvType, plot)
            ?: TmdbHelper.getTmdbDataByName(name, tvType, year, plot, ikinciIsim = null)

        val finalPoster   = tmdb?.finalPoster ?: poster
        val finalPlot     = tmdb?.finalPlot ?: plot
        val finalYear     = tmdb?.finalYear ?: year
        val finalDuration = tmdb?.finalDuration ?: duration
        val finalTmdbId   = tmdb?.tmdbId ?: tmdbId
        val finalImdbId   = tmdb?.imdbId ?: imdbId

        val syncData = mapOf(
            "url"    to url,
            "type"   to if (isTv) "series" else "movie",
            "tmdbId" to (finalTmdbId?.toString() ?: ""),
            "imdbId" to (finalImdbId ?: "")
        )

        val mapper = jacksonObjectMapper()
        if (!isTv) {
            return newMovieLoadResponse(name, url, TvType.Movie, mapper.writeValueAsString(syncData)) {
                this.posterUrl = finalPoster
                this.plot      = finalPlot
                this.year      = finalYear
                this.score     = Score.from10(rating)
                this.tags      = tags
                addActors(tmdb?.actors?.map { it.actor })
                tmdb?.trailerId?.let { addTrailer("https://www.youtube.com/watch?v=$it") }
            }
        }

        val episodes = mutableListOf<Episode>()
        val seasonPanes = document.select("div.season-tab, div.season-item, div[data-season]")

        if (seasonPanes.isEmpty()) {
            document.select("a.episode-link, a[href*='/bolum/']").forEachIndexed { idx, el ->
                episodes.add(
                    newEpisode(el.attr("href")) {
                        this.name         = el.text().trim()
                        this.episode      = idx + 1
                        this.posterUrl    = finalPoster
                    }
                )
            }
        } else {
            seasonPanes.forEachIndexed { sIdx, pane ->
                val seasonNum = pane.attr("data-season").toIntOrNull() ?: (sIdx + 1)
                val tmdbSeason = TmdbHelper.getTmdbSeason(finalTmdbId, seasonNum)
                pane.select("a.episode-link, a[href*='/bolum/']").forEachIndexed { eIdx, el ->
                    val tmdbEp = tmdbSeason?.episodes?.getOrNull(eIdx)
                    episodes.add(
                        newEpisode(el.attr("href")) {
                            this.name        = tmdbEp?.name ?: el.text().trim()
                            this.season      = seasonNum
                            this.episode     = eIdx + 1
                            this.description = tmdbEp?.overview
                            this.posterUrl   = tmdbEp?.still_path?.let { "https://image.tmdb.org/t/p/w300$it" }
                        }
                    )
                }
            }
        }

        return newTvSeriesLoadResponse(name, url, TvType.TvSeries, episodes) {
            this.posterUrl = finalPoster
            this.plot      = finalPlot
            this.year      = finalYear
            this.score     = Score.from10(rating)
            this.tags      = tags
            addActors(tmdb?.actors?.map { it.actor })
            tmdb?.trailerId?.let { addTrailer("https://www.youtube.com/watch?v=$it") }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = runCatching { parseJson<LinkData>(data) }.getOrNull()
        val targetUrl = linkData?.url ?: data

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val responseText = try {
            app.get(targetUrl, headers = mapOf("User-Agent" to userAgent)).text
        } catch (e: Exception) {
            return false
        }

        val vidmodyRegex = Regex("""(https?:[\\/]+player\.vidmody\.com[\\/][a-zA-Z0-9=\\/]+)""")
        val vidmodyMatch = vidmodyRegex.find(responseText)?.groupValues?.get(1)
        val vidmodyUrl = vidmodyMatch?.replace("\\/", "/")?.replace("\\", "")

        var foundVideo = false
        if (vidmodyUrl != null) {
            foundVideo = true
            VidMody().getUrl(vidmodyUrl, targetUrl, subtitleCallback, callback)
        }

        val imdbId  = linkData?.imdbId
        val season  = linkData?.season
        val episode = linkData?.episode

        if (!imdbId.isNullOrBlank()) {
            invokeSubtitleAPI(imdbId, season, episode, subtitleCallback)
            invokeTurkceAltyaziAPI(imdbId, season, episode, subtitleCallback)
            invokeWyZIESUBAPI(imdbId, season, episode, subtitleCallback)
        }

        return foundVideo
    }

    companion object {
        private const val CACHE_DURATION   = 300_000L
        private const val LAST_UPDATE_KEY  = "last_domain_update"
        private const val DEFAULT_DOMAIN   = "https://hdfilmdelisi.one"

        fun getDomain(prefs: SharedPreferences?): String {
            if (prefs == null) return DEFAULT_DOMAIN
            return runBlocking {
                withTimeoutOrNull(3000L) {
                    try {
                        val lastUpdate = prefs.getLong(LAST_UPDATE_KEY, 0)
                        val cached     = prefs.getString("HDFilmDelisi", null)
                        if (cached != null && (System.currentTimeMillis() - lastUpdate) < CACHE_DURATION) {
                            return@withTimeoutOrNull cached
                        }
                        val response = app.get("$DEFAULT_DOMAIN/domain.json")
                        if (response.isSuccessful) {
                            val domain = response.text.trim().trim('"')
                            prefs.edit()
                                .putString("HDFilmDelisi", domain)
                                .putLong(LAST_UPDATE_KEY, System.currentTimeMillis())
                                .apply()
                            domain
                        } else {
                            DEFAULT_DOMAIN
                        }
                    } catch (e: Exception) {
                        DEFAULT_DOMAIN
                    }
                } ?: DEFAULT_DOMAIN
            }
        }
    }
}
