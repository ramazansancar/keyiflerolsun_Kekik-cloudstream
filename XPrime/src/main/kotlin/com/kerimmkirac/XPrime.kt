package com.kerimmkirac

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.APIHolder.capitalize
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
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
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
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    private val apiKey = "84259f99204eeb7d45c7e3d8e36c6123"
    private val imgUrl = "https://image.tmdb.org/t/p/w500"
    private val backImgUrl = "https://image.tmdb.org/t/p/w780"
    private val backendUrl = "https://backend.xprime.tv"
    private val xUrl = "https://xprime.tv/"
    
    override val mainPage = mainPageOf(
        "$mainUrl/trending/movie/week?api_key=$apiKey&language=en-US&page=SAYFA" to "Movies",
        "$mainUrl/trending/tv/week?api_key=$apiKey&language=en-US&page=SAYFA" to "TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.replace("SAYFA", page.toString())
        Log.d("XPR", "URL -> $url")
        
        val home = if (request.name == "Movies") {
            val response = app.get(url).parsedSafe<MovieResponse>()
            response?.results?.map { it.toMovieSearchResponse() }
        } else {
            val response = app.get(url).parsedSafe<TvResponse>()
            response?.results?.map { it.toTvSearchResponse() }
        }

        return newHomePageResponse(request.name, home ?: emptyList())
    }

    private fun XMovie.toMovieSearchResponse(): SearchResponse {
        val title = this.title ?: this.originalTitle ?: ""
        val href = "movie/${this.id}"
        val posterUrl = imgUrl + this.posterPath

        return newMovieSearchResponse(title, href, TvType.Movie) { 
            this.posterUrl = posterUrl 
        }
    }

    private fun XTvShow.toTvSearchResponse(): SearchResponse {
        val title = this.name ?: this.originalName ?: ""
        val href = "tv/${this.id}"
        val posterUrl = imgUrl + this.posterPath

        return newAnimeSearchResponse(title, href, TvType.TvSeries) { 
            this.posterUrl = posterUrl 
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        Log.d("XPR", "query -> $query")
        val url = "${mainUrl}/search/multi?api_key=$apiKey&query=$query&page=1&language=en-US"
        Log.d("XPR", "Search url -> $url")
        val document = app.get(url)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val searchResponse: SearchMultiResponse = objectMapper.readValue(document.text)
        Log.d("XPR", "Search document -> $document")

        return searchResponse.results.mapNotNull { result ->
            when (result.mediaType) {
                "movie" -> {
                    val movie = result as XMovie
                    movie.toMovieSearchResponse()
                }
                "tv" -> {
                    val tv = result as XTvShow
                    tv.toTvSearchResponse()
                }
                else -> null
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.split("/")
        val type = parts[0] 
        val id = parts[1]
        
        Log.d("XPR", "type -> $type, id -> $id")
        
        return if (type == "movie") {
            loadMovie(id)
        } else {
            loadTvShow(id)
        }
    }

    private suspend fun loadMovie(id: String): LoadResponse? {
        val movieUrl = "$mainUrl/movie/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations"
        Log.d("XPR", "movieUrl -> $movieUrl")
        val document = app.get(movieUrl)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val movie: XMovie = objectMapper.readValue(document.text)
        Log.d("XPR", movie.toString())

        val title = movie.title ?: movie.originalTitle ?: ""
        val poster = backImgUrl + movie.backdropPath
        val description = movie.overview
        val year = movie.releaseDate?.split("-")?.first()?.toIntOrNull()
        val tags = movie.genres?.map { it.name }
        val rating = movie.vote.toString().toRatingInt()
        val duration = movie.runtime
        
        
        val trailerUrl = "$mainUrl/movie/$id/videos?api_key=$apiKey"
        val trailerDoc = app.get(trailerUrl)
        val trailers: Trailers = objectMapper.readValue(trailerDoc.text)
        var trailer = ""
        if (trailers.results.filter { it.site == "YouTube" }.isNotEmpty()) {
            trailer = trailers.results.filter { it.site == "YouTube" }[0].key
        }
        
        val actors = movie.credits?.cast?.map { Actor(it.name, imgUrl + it.profilePath) }
        val recommendations = movie.recommendations?.results?.map { it.toMovieSearchResponse() }
        
        return newMovieLoadResponse(title, "movie/$id", TvType.Movie, "movie/$id") {
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

    private suspend fun loadTvShow(id: String): LoadResponse? {
        val tvUrl = "$mainUrl/tv/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations"
        Log.d("XPR", "tvUrl -> $tvUrl")
        val document = app.get(tvUrl)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val tvShow: XTvShow = objectMapper.readValue(document.text)
        Log.d("XPR", tvShow.toString())

        val title = tvShow.name ?: tvShow.originalName ?: ""
        val poster = backImgUrl + tvShow.backdropPath
        val description = tvShow.overview
        val year = tvShow.firstAirDate?.split("-")?.first()?.toIntOrNull()
        val tags = tvShow.genres?.map { it.name }
        val rating = tvShow.vote.toString().toRatingInt()
        
        
        val trailerUrl = "$mainUrl/tv/$id/videos?api_key=$apiKey"
        val trailerDoc = app.get(trailerUrl)
        val trailers: Trailers = objectMapper.readValue(trailerDoc.text)
        var trailer = ""
        if (trailers.results.filter { it.site == "YouTube" }.isNotEmpty()) {
            trailer = trailers.results.filter { it.site == "YouTube" }[0].key
        }
        
        val actors = tvShow.credits?.cast?.map { Actor(it.name, imgUrl + it.profilePath) }
        val recommendations = tvShow.recommendations?.results?.map { it.toTvSearchResponse() }
        
        
        val episodes = mutableListOf<Episode>()
        tvShow.seasons?.forEach { season ->
            if (season.seasonNumber != 0) { 
                val seasonUrl = "$mainUrl/tv/$id/season/${season.seasonNumber}?api_key=$apiKey&language=en-US"
                try {
                    val seasonDoc = app.get(seasonUrl)
                    val seasonData: Season = objectMapper.readValue(seasonDoc.text)
                    seasonData.episodes?.forEach { episode ->
                        episodes.add(
                            newEpisode("tv/$id/${season.seasonNumber}/${episode.episodeNumber}") {
                                this.name = episode.name
                                this.season = season.seasonNumber
                                this.episode = episode.episodeNumber
                                this.posterUrl = imgUrl + episode.stillPath
                                this.description = episode.overview
                                this.rating = episode.voteAverage?.toInt()
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.e("XPR", "Error loading season ${season.seasonNumber}: ${e.message}")
                }
            }
        }
        
        return newTvSeriesLoadResponse(title, "tv/$id", TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.rating = rating
            this.recommendations = recommendations
            addActors(actors)
            addTrailer("https://www.youtube.com/embed/${trailer}")
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
        val type = parts[0] 
        val id = parts[1]
        
        return if (type == "movie") {
            loadMovieLinks(id, subtitleCallback, callback)
        } else {
            val season = parts[2].toInt()
            val episode = parts[3].toInt()
            loadTvLinks(id, season, episode, subtitleCallback, callback)
        }
    }

    private suspend fun loadMovieLinks(
        id: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val movieUrl = "$mainUrl/movie/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations"
        Log.d("XPR", "movieUrl -> $movieUrl")
        val document = app.get(movieUrl)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val movie: XMovie = objectMapper.readValue(document.text)
        
       
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
        
       
        val serversUrl = "https://backend.xprime.tv/servers"
        val servers = app.get(serversUrl).parsedSafe<Servers>()
        servers?.servers?.forEach { server ->
            try {
                loadMovieServer(server, id, movie, callback, subtitleCallback)
            } catch (e: Exception) {
                Log.e("XPR", "Error loading server ${server.name}: ${e.message}")
            }
        }
        return true
    }

    private suspend fun loadTvLinks(
        id: String,
        season: Int,
        episode: Int,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val tvUrl = "$mainUrl/tv/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations"
        Log.d("XPR", "tvUrl -> $tvUrl")
        val document = app.get(tvUrl)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val tvShow: XTvShow = objectMapper.readValue(document.text)
        
       
        val subtitleUrl = "https://sub.wyzie.ru/search?id=$id&season=$season&episode=$episode"
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
        
        
        val serversUrl = "https://backend.xprime.tv/servers"
        val servers = app.get(serversUrl).parsedSafe<Servers>()
        servers?.servers?.forEach { server ->
            try {
                loadTvServer(server, id, tvShow, season, episode, callback, subtitleCallback)
            } catch (e: Exception) {
                Log.e("XPR", "Error loading server ${server.name}: ${e.message}")
            }
        }
        return true
    }

    private suspend fun loadMovieServer(
        server: Server,
        id: String,
        movie: XMovie,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val movieName = movie.originalTitle
        val year = movie.releaseDate?.split("-")?.first()?.toIntOrNull()
        val imdb = movie.imdb
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        
        if (server.name == "primebox" && server.status == "ok") {
            val url = "$backendUrl/primebox?name=$movieName&year=$year&fallback_year=${year?.minus(1)}"
            val document = app.get(url)
            val streamText = document.text
            val stream: Stream = objectMapper.readValue(streamText)
            stream.qualities.forEach { quality ->
                val source = objectMapper.readTree(streamText).get("streams").get(quality).textValue()
                callback.invoke(
                    newExtractorLink(
                        source = server.name.capitalize() + " - " + quality,
                        name = server.name.capitalize() + " - " + quality,
                        url = source,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.quality = getQualityFromName(quality)
                        this.headers = mapOf("Origin" to mainUrl)
                        this.referer = xUrl
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
                val url = "$backendUrl/${server.name}?name=$movieName&year=$year&id=$id&imdb=$imdb"
                val document = app.get(url)
                val source = objectMapper.readTree(document.text).get("url").textValue()
                callback.invoke(
                    newExtractorLink(
                        source = server.name.capitalize(),
                        name = server.name.capitalize(),
                        url = source,
                        ExtractorLinkType.M3U8
                    ) {
                        this.headers = mapOf("Origin" to xUrl)
                        this.quality = Qualities.Unknown.value
                        this.referer = xUrl
                    }
                )
            }
        }
    }

    private suspend fun loadTvServer(
        server: Server,
        id: String,
        tvShow: XTvShow,
        season: Int,
        episode: Int,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val showName = tvShow.originalName ?: tvShow.name
        val year = tvShow.firstAirDate?.split("-")?.first()?.toIntOrNull()
        val imdb = tvShow.imdb
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        
        if (server.name == "primebox" && server.status == "ok") {
            val url = "$backendUrl/primebox?name=$showName&fallback_year=${year?.minus(1)}&season=$season&episode=$episode"
            val document = app.get(url)
            val streamText = document.text
            val stream: Stream = objectMapper.readValue(streamText)
            stream.qualities.forEach { quality ->
                val source = objectMapper.readTree(streamText).get("streams").get(quality).textValue()
                callback.invoke(
                    newExtractorLink(
                        source = server.name.capitalize() + " - " + quality,
                        name = server.name.capitalize() + " - " + quality,
                        url = source,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.quality = getQualityFromName(quality)
                        this.headers = mapOf("Origin" to mainUrl)
                        this.referer = xUrl
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
                val url = "$backendUrl/${server.name}?name=$showName&year=$year&id=$id&imdb=$imdb&season=$season&episode=$episode"
                val document = app.get(url)
                val source = objectMapper.readTree(document.text).get("url").textValue()
                callback.invoke(
                    newExtractorLink(
                        source = server.name.capitalize(),
                        name = server.name.capitalize(),
                        url = source,
                        ExtractorLinkType.M3U8
                    ) {
                        this.headers = mapOf("Origin" to xUrl)
                        this.quality = Qualities.Unknown.value
                        this.referer = xUrl
                    }
                )
            }
        }
    }
}