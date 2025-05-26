package com.nikyokki

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class XPrime : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "XPrime"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    private val apiKey = "84259f99204eeb7d45c7e3d8e36c6123"
    private val imgUrl = "https://image.tmdb.org/t/p/w500"
    private val backImgUrl = "https://image.tmdb.org/t/p/w780"
    private val backendUrl = "https://backend.xprime.tv"

    override val mainPage = mainPageOf(
        "$mainUrl/trending/movie/week?api_key=$apiKey&language=tr-TR&page=SAYFA" to "Popüler Filmler",
        "$mainUrl/movie/now_playing?api_key=$apiKey&language=tr-TR&page=SAYFA" to "Sinemalarda",
        "$mainUrl/trending/tv/week?api_key=$apiKey&language=tr-TR&page=SAYFA" to "Popüler Diziler",
        "$mainUrl/tv/on_the_air?api_key=$apiKey&language=tr-TR&page=SAYFA" to "Yayında",
        "28" to "Aksiyon",
        "12" to "Macera",
        "16" to "Animasyon",
        "35" to "Komedi",
        "80" to "Suç",
        "99" to "Belgesel",
        "18" to "Dram",
        "10751" to "Aile",
        "14" to "Fantastik",
        "36" to "Tarih",
        "27" to "Korku",
        "9648" to "Gizem",
        "10749" to "Romantik",
        "878" to "Bilim-Kurgu",
        "53" to "Gerilim",
        "10752" to "Savaş",
        "37" to "Vahşi Batı",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var url = when {
            request.name in listOf("Popüler Filmler", "Sinemalarda", "Popüler Diziler", "Yayında") -> {
                request.data.replace("SAYFA", page.toString())
            }
            request.name in listOf("Popüler Diziler", "Yayında") -> {
                "${mainUrl}/discover/tv?api_key=$apiKey&page=${page}&include_adult=false&with_watch_monetization_types=flatrate%7Cfree%7Cads&watch_region=TR&language=tr-TR&with_genres=${request.data}&sort_by=popularity.desc"
            }
            else -> {
                "${mainUrl}/discover/movie?api_key=$apiKey&page=${page}&include_adult=false&with_watch_monetization_types=flatrate%7Cfree%7Cads&watch_region=TR&language=tr-TR&with_genres=${request.data}&sort_by=popularity.desc"
            }
        }
        Log.d("XPR", "URL -> $url")
        val movies = app.get(url).parsedSafe<MovieResponse>()
        val home =
            movies?.results?.map { it.toMainPageResult() }

        return newHomePageResponse(request.name, home!!)
    }

    private fun XMovie.toMainPageResult(): SearchResponse {
        val title = if (this.mediaType == "tv" || this.name != null) {
            this.name ?: this.originalName ?: "Unknown"
        } else {
            this.title ?: this.originalTitle ?: "Unknown"
        }
        val type = if (this.mediaType == "tv" || this.name != null) TvType.TvSeries else TvType.Movie
        val href = if (type == TvType.TvSeries) "tv/${this.id}" else this.id.toString()
        val posterUrl = imgUrl + this.posterPath

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        Log.d("XPR", "query -> $query")
        val url = "${mainUrl}/search/multi?api_key=$apiKey&query=$query&page=1"
        Log.d("XPR", "Search url -> $url")
        val document = app.get(url)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val movies: MovieResponse = objectMapper.readValue(document.text)
        Log.d("XPR", "Search document -> $document")

        return movies.results.filter { it.mediaType in listOf("movie", "tv") }.map { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.split("/")
        val id = parts.last()
        val isTvSeries = parts.size > 1 && parts[parts.size - 2] == "tv"
        
        Log.d("XPR", "id -> $id, isTvSeries -> $isTvSeries")
        
        val apiUrl = if (isTvSeries) {
            "$mainUrl/tv/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations"
        } else {
            "$mainUrl/movie/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations"
        }
        
        Log.d("XPR", "apiUrl -> $apiUrl")
        val document = app.get(apiUrl)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val content: XMovie = objectMapper.readValue(document.text)
        Log.d("XPR", content.toString())

        val title = if (isTvSeries) content.name else content.title
        val orgTitle = if (isTvSeries) content.originalName else content.originalTitle
        val totTitle = if (title?.isNotEmpty() == true && orgTitle != title) "$orgTitle - $title" else orgTitle
        val poster = backImgUrl + content.backdropPath
        val description = content.overview
        val year = if (isTvSeries) {
            content.firstAirDate?.split("-")?.first()?.toIntOrNull()
        } else {
            content.releaseDate?.split("-")?.first()?.toIntOrNull()
        }
        val tags = content.genres?.map { it.name }
        val rating = content.vote.toString().toRatingInt()
        val duration = if (isTvSeries) content.episodeRunTime?.firstOrNull() else content.runtime
        
        val trailerUrl = if (isTvSeries) {
            "$mainUrl/tv/$id/videos?api_key=$apiKey"
        } else {
            "$mainUrl/movie/$id/videos?api_key=$apiKey"
        }
        val trailerDoc = app.get(trailerUrl)
        val trailers: Trailers = objectMapper.readValue(trailerDoc.text)
        var trailer = ""
        if (trailers.results.filter { it.site == "YouTube" }.isNotEmpty()) {
            trailer = trailers.results.filter { it.site == "YouTube" }[0].key
        }
        
        val actors = content.credits?.cast?.map { Actor(it.name, imgUrl + it.profilePath) }
        val recommendations = content.recommendations?.results?.map { it.toMainPageResult() }
        
        return if (isTvSeries) {
            val episodes = mutableListOf<Episode>()
            content.seasons?.forEach { season ->
                if (season.seasonNumber > 0) {
                    val seasonUrl = "$mainUrl/tv/$id/season/${season.seasonNumber}?api_key=$apiKey&language=tr-TR"
                    try {
                        val seasonDoc = app.get(seasonUrl)
                        val seasonData: SeasonDetails = objectMapper.readValue(seasonDoc.text)
                        seasonData.episodes?.forEach { episode ->
                            episodes.add(
                                newEpisode("tv/$id/${season.seasonNumber}/${episode.episodeNumber}") {
                                    this.name = episode.name
                                    this.season = season.seasonNumber
                                    this.episode = episode.episodeNumber
                                    this.posterUrl = if (episode.stillPath != null) imgUrl + episode.stillPath else null
                                    this.description = episode.overview
                                }
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("XPR", "Error loading season ${season.seasonNumber}: ${e.message}")
                    }
                }
            }
            
            newTvSeriesLoadResponse(totTitle.toString(), url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer("https://www.youtube.com/embed/${trailer}")
            }
        } else {
            newMovieLoadResponse(totTitle.toString(), url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer("https://www.youtube.com/embed/${trailer}")
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("XPR", "data » ${data}")
        val parts = data.split("/")
        val id = parts[1]
        val isTvSeries = parts[0] == "tv"
        val season = if (isTvSeries && parts.size > 2) parts[2].toIntOrNull() else null
        val episode = if (isTvSeries && parts.size > 3) parts[3].toIntOrNull() else null
        
        Log.d("XPR", "id: $id, isTvSeries: $isTvSeries, season: $season, episode: $episode")
        
        val apiUrl = if (isTvSeries) {
            "$mainUrl/tv/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations"
        } else {
            "$mainUrl/movie/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations"
        }
        
        Log.d("XPR", "apiUrl -> $apiUrl")
        val document = app.get(apiUrl)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val content: XMovie = objectMapper.readValue(document.text)
        
        val subtitleUrl = "https://sub.wyzie.ru/search?id=$id"
        try {
            val subtitleDocument = app.get(subtitleUrl)
            val subtitles: List<Subtitle> = objectMapper.readValue(subtitleDocument.text)
            subtitles.forEach { subtitle ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = subtitle.display,
                        url = subtitle.url
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("XPR", "Error loading subtitles: ${e.message}")
        }
        
        val serversUrl = "https://xprime.tv/servers"
        val servers = app.get(serversUrl).parsedSafe<Servers>()
        servers?.servers?.forEach { server ->
            try {
                loadServers(server, id, content, callback, subtitleCallback, season, episode)
            } catch (e: Exception) {
                e.printStackTrace()
                return@forEach
            }
        }
        return true
    }

    private suspend fun loadServers(
        server: Server,
        id: String,
        content: XMovie,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit,
        season: Int? = null,
        episode: Int? = null
    ) {
        val contentName = content.originalTitle ?: content.originalName
        val year = content.releaseDate?.split("-")?.first()?.toIntOrNull() 
            ?: content.firstAirDate?.split("-")?.first()?.toIntOrNull()
        val imdb = content.imdb
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        
        if (server.name == "primebox" && server.status == "ok") {
            val url = if (season != null && episode != null) {
                "$backendUrl/primebox?name=$contentName&year=$year&id=$id&season=$season&episode=$episode"
            } else {
                "$backendUrl/primebox?name=$contentName&year=$year&fallback_year=${year?.minus(1)}"
            }
            
            Log.d("XPR", "Primebox URL: $url")
            val document = app.get(url)
            val streamText = document.text
            val stream: Stream = objectMapper.readValue(streamText)
            
            stream.qualities.forEach { quality ->
                val source = objectMapper.readTree(streamText).get("streams").get(quality).textValue()
                val linkName = if (season != null && episode != null) {
                    "${server.name.capitalize()} - $quality (S${season}E${episode})"
                } else {
                    "${server.name.capitalize()} - $quality"
                }
                
                callback.invoke(
                    newExtractorLink(
                        source = linkName,
                        name = linkName,
                        url = source,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.quality = getQualityFromName(quality)
                        this.headers = mapOf("Origin" to "https://xprime.tv/")
                        this.referer = "https://xprime.tv/"
                    }
                )
            }
            
            if (stream.hasSubtitles) {
                stream.subtitles.forEach { subtitle ->
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = subtitle.label.toString(),
                            url = subtitle.file.toString()
                        )
                    )
                }
            }
        } else {
            if (server.status == "ok") {
                val url = if (season != null && episode != null) {
                    "$backendUrl/${server.name}?name=$contentName&year=$year&id=$id&imdb=$imdb&season=$season&episode=$episode"
                } else {
                    "$backendUrl/${server.name}?name=$contentName&year=$year&id=$id&imdb=$imdb"
                }
                
                Log.d("XPR", "Server URL: $url")
                val document = app.get(url)
                val source = objectMapper.readTree(document.text).get("url").textValue()
                
                val linkName = if (season != null && episode != null) {
                    "${server.name.capitalize()} (S${season}E${episode})"
                } else {
                    server.name.capitalize()
                }
                
                callback.invoke(
                    newExtractorLink(
                        source = linkName,
                        name = linkName,
                        url = source,
                        ExtractorLinkType.M3U8
                    ) {
                        this.headers = mapOf("Origin" to "https://xprime.tv/")
                        this.quality = Qualities.Unknown.value
                        this.referer = "https://xprime.tv/"
                    }
                )
            }
        }
    }
}