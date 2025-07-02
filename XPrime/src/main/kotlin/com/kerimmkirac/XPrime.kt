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
    
    
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
        configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
    }
    
    override val mainPage = mainPageOf(
        "$mainUrl/trending/movie/week?api_key=$apiKey&language=en-US&page=SAYFA" to "Movies",
        "$mainUrl/trending/tv/week?api_key=$apiKey&language=en-US&page=SAYFA" to "TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.replace("SAYFA", page.toString())
        Log.d("XPR", "URL -> $url")
        
        return try {
            val home = if (request.name == "Movies") {
                val response = app.get(url)
                Log.d("XPR", "Movie response: ${response.text}")
                val movieResponse = objectMapper.readValue<MovieResponse>(response.text)
                movieResponse.results.map { it.toMovieSearchResponse() }
            } else {
                val response = app.get(url)
                Log.d("XPR", "TV response: ${response.text}")
                val tvResponse = objectMapper.readValue<TvResponse>(response.text)
                tvResponse.results.map { it.toTvSearchResponse() }
            }
            newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            Log.e("XPR", "Error in getMainPage: ${e.message}", e)
            newHomePageResponse(request.name, emptyList())
        }
    }

    private fun XMovie.toMovieSearchResponse(): SearchResponse {
        val title = this.title ?: this.originalTitle ?: ""
        val href = "movie/${this.id}"
        val posterUrl = if (this.posterPath != null) imgUrl + this.posterPath else ""

        return newMovieSearchResponse(title, href, TvType.Movie) { 
            this.posterUrl = posterUrl 
        }
    }

    private fun XTvShow.toTvSearchResponse(): SearchResponse {
        val title = this.name ?: this.originalName ?: ""
        val href = "tv/${this.id}"
        val posterUrl = if (this.posterPath != null) imgUrl + this.posterPath else ""

        return newAnimeSearchResponse(title, href, TvType.TvSeries) { 
            this.posterUrl = posterUrl 
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return try {
            Log.d("XPR", "query -> $query")
            val url = "${mainUrl}/search/multi?api_key=$apiKey&query=$query&page=1&language=en-US"
            Log.d("XPR", "Search url -> $url")
            val document = app.get(url)
            Log.d("XPR", "Search response: ${document.text}")
            
            val searchResponse = objectMapper.readValue<SearchMultiResponse>(document.text)

            searchResponse.results.mapNotNull { result ->
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
        } catch (e: Exception) {
            Log.e("XPR", "Error in search: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val parts = url.split("/")
            val type = parts[0] 
            val id = parts[1]
            
            Log.d("XPR", "type -> $type, id -> $id")
            
            if (type == "movie") {
                loadMovie(id)
            } else {
                loadTvShow(id)
            }
        } catch (e: Exception) {
            Log.e("XPR", "Error in load: ${e.message}", e)
            null
        }
    }

    private suspend fun loadMovie(id: String): LoadResponse? {
        return try {
            val movieUrl = "$mainUrl/movie/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations"
            Log.d("XPR", "movieUrl -> $movieUrl")
            val document = app.get(movieUrl)
            Log.d("XPR", "Movie details response: ${document.text}")
            
            val movie = objectMapper.readValue<XMovie>(document.text)

            val title = movie.title ?: movie.originalTitle ?: ""
            val poster = if (movie.backdropPath != null) backImgUrl + movie.backdropPath else ""
            val description = movie.overview
            val year = movie.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val tags = movie.genres?.map { it.name }
            val rating = movie.vote?.toInt()
            val duration = movie.runtime
            
           
            var trailer = ""
            try {
                val trailerUrl = "$mainUrl/movie/$id/videos?api_key=$apiKey"
                val trailerDoc = app.get(trailerUrl)
                val trailers = objectMapper.readValue<Trailers>(trailerDoc.text)
                val youtubeTrailer = trailers.results.firstOrNull { it.site == "YouTube" }
                if (youtubeTrailer != null) {
                    trailer = youtubeTrailer.key
                }
            } catch (e: Exception) {
                Log.e("XPR", "Error loading trailer: ${e.message}")
            }
            
            val actors = movie.credits?.cast?.map { Actor(it.name, if (it.profilePath != null) imgUrl + it.profilePath else "") }
            val recommendations = movie.recommendations?.results?.map { it.toMovieSearchResponse() }
            
            newMovieLoadResponse(title, "movie/$id", TvType.Movie, "movie/$id") {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.recommendations = recommendations
                if (actors != null) addActors(actors)
                if (trailer.isNotEmpty()) addTrailer("https://www.youtube.com/embed/${trailer}")
            }
        } catch (e: Exception) {
            Log.e("XPR", "Error loading movie: ${e.message}", e)
            null
        }
    }

    private suspend fun loadTvShow(id: String): LoadResponse? {
        return try {
            val tvUrl = "$mainUrl/tv/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations"
            Log.d("XPR", "tvUrl -> $tvUrl")
            val document = app.get(tvUrl)
            Log.d("XPR", "TV details response: ${document.text}")
            
            val tvShow = objectMapper.readValue<XTvShow>(document.text)

            val title = tvShow.name ?: tvShow.originalName ?: ""
            val poster = if (tvShow.backdropPath != null) backImgUrl + tvShow.backdropPath else ""
            val description = tvShow.overview
            val year = tvShow.firstAirDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val tags = tvShow.genres?.map { it.name }
            val rating = tvShow.vote?.toInt()
            
            
            var trailer = ""
            try {
                val trailerUrl = "$mainUrl/tv/$id/videos?api_key=$apiKey"
                val trailerDoc = app.get(trailerUrl)
                val trailers = objectMapper.readValue<Trailers>(trailerDoc.text)
                val youtubeTrailer = trailers.results.firstOrNull { it.site == "YouTube" }
                if (youtubeTrailer != null) {
                    trailer = youtubeTrailer.key
                }
            } catch (e: Exception) {
                Log.e("XPR", "Error loading trailer: ${e.message}")
            }
            
            val actors = tvShow.credits?.cast?.map { Actor(it.name, if (it.profilePath != null) imgUrl + it.profilePath else "") }
            val recommendations = tvShow.recommendations?.results?.map { it.toTvSearchResponse() }
            
            
            val episodes = mutableListOf<Episode>()
            tvShow.seasons?.forEach { season ->
                if (season.seasonNumber != 0) { 
                    try {
                        val seasonUrl = "$mainUrl/tv/$id/season/${season.seasonNumber}?api_key=$apiKey&language=en-US"
                        val seasonDoc = app.get(seasonUrl)
                        val seasonData = objectMapper.readValue<Season>(seasonDoc.text)
                        seasonData.episodes?.forEach { episode ->
                            episodes.add(
                                newEpisode("tv/$id/${season.seasonNumber}/${episode.episodeNumber}") {
                                    this.name = episode.name
                                    this.season = season.seasonNumber
                                    this.episode = episode.episodeNumber
                                    this.posterUrl = if (episode.stillPath != null) imgUrl + episode.stillPath else ""
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
            
            newTvSeriesLoadResponse(title, "tv/$id", TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
                if (actors != null) addActors(actors)
                if (trailer.isNotEmpty()) addTrailer("https://www.youtube.com/embed/${trailer}")
            }
        } catch (e: Exception) {
            Log.e("XPR", "Error loading TV show: ${e.message}", e)
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            Log.d("XPR", "data » ${data}")
            val parts = data.split("/")
            val type = parts[0] 
            val id = parts[1]
            
            if (type == "movie") {
                loadMovieLinks(id, subtitleCallback, callback)
            } else {
                val season = parts[2].toInt()
                val episode = parts[3].toInt()
                loadTvLinks(id, season, episode, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            Log.e("XPR", "Error in loadLinks: ${e.message}", e)
            false
        }
    }

    private suspend fun loadMovieLinks(
        id: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val movieUrl = "$mainUrl/movie/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations"
            Log.d("XPR", "movieUrl -> $movieUrl")
            val document = app.get(movieUrl)
            val movie = objectMapper.readValue<XMovie>(document.text)
            
            
            try {
                val subtitleUrl = "https://sub.wyzie.ru/search?id=$id"
                val subtitleDocument = app.get(subtitleUrl)
                val subtitles = objectMapper.readValue<List<Subtitle>>(subtitleDocument.text)
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
            
            
            try {
                val serversUrl = "https://backend.xprime.tv/servers"
                val serversResponse = app.get(serversUrl)
                val servers = objectMapper.readValue<Servers>(serversResponse.text)
                servers.servers?.forEach { server ->
                    try {
                        loadMovieServer(server, id, movie, callback, subtitleCallback)
                    } catch (e: Exception) {
                        Log.e("XPR", "Error loading server ${server.name}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("XPR", "Error loading servers: ${e.message}")
            }
            
            true
        } catch (e: Exception) {
            Log.e("XPR", "Error in loadMovieLinks: ${e.message}", e)
            false
        }
    }

    private suspend fun loadTvLinks(
        id: String,
        season: Int,
        episode: Int,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val tvUrl = "$mainUrl/tv/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations"
            Log.d("XPR", "tvUrl -> $tvUrl")
            val document = app.get(tvUrl)
            val tvShow = objectMapper.readValue<XTvShow>(document.text)
            
            
            try {
                val subtitleUrl = "https://sub.wyzie.ru/search?id=$id&season=$season&episode=$episode"
                val subtitleDocument = app.get(subtitleUrl)
                val subtitles = objectMapper.readValue<List<Subtitle>>(subtitleDocument.text)
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
            
            
            try {
                val serversUrl = "https://backend.xprime.tv/servers"
                val serversResponse = app.get(serversUrl)
                val servers = objectMapper.readValue<Servers>(serversResponse.text)
                servers.servers?.forEach { server ->
                    try {
                        loadTvServer(server, id, tvShow, season, episode, callback, subtitleCallback)
                    } catch (e: Exception) {
                        Log.e("XPR", "Error loading server ${server.name}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("XPR", "Error loading servers: ${e.message}")
            }
            
            true
        } catch (e: Exception) {
            Log.e("XPR", "Error in loadTvLinks: ${e.message}", e)
            false
        }
    }

    private suspend fun loadMovieServer(
        server: Server,
        id: String,
        movie: XMovie,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        try {
            val movieName = movie.originalTitle
            val year = movie.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val imdb = movie.imdb
            
            if (server.name == "primebox" && server.status == "ok") {
                val url = "$backendUrl/primebox?name=$movieName&year=$year&fallback_year=${year?.minus(1)}"
                val document = app.get(url)
                val streamText = document.text
                val stream = objectMapper.readValue<Stream>(streamText)
                
                stream.qualities.forEach { quality ->
                    val streamTree = objectMapper.readTree(streamText)
                    val source = streamTree.get("streams")?.get(quality)?.textValue()
                    if (source != null) {
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
                }
                
                if (stream.hasSubtitles) {
                    stream.subtitles.forEach { subtitle ->
                        if (subtitle.file != null && subtitle.label != null) {
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    lang = subtitle.label,
                                    url = subtitle.file
                                )
                            )
                        }
                    }
                }
            } else if (server.status == "ok") {
                val url = "$backendUrl/${server.name}?name=$movieName&year=$year&id=$id&imdb=$imdb"
                val document = app.get(url)
                val sourceTree = objectMapper.readTree(document.text)
                val source = sourceTree.get("url")?.textValue()
                
                if (source != null) {
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
        } catch (e: Exception) {
            Log.e("XPR", "Error in loadMovieServer: ${e.message}", e)
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
        try {
            val showName = tvShow.originalName ?: tvShow.name
            val year = tvShow.firstAirDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val imdb = tvShow.imdb
            
            if (server.name == "primebox" && server.status == "ok") {
                val url = "$backendUrl/primebox?name=$showName&fallback_year=${year?.minus(1)}&season=$season&episode=$episode"
                val document = app.get(url)
                val streamText = document.text
                val stream = objectMapper.readValue<Stream>(streamText)
                
                stream.qualities.forEach { quality ->
                    val streamTree = objectMapper.readTree(streamText)
                    val source = streamTree.get("streams")?.get(quality)?.textValue()
                    if (source != null) {
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
                }
                
                if (stream.hasSubtitles) {
                    stream.subtitles.forEach { subtitle ->
                        if (subtitle.file != null && subtitle.label != null) {
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    lang = subtitle.label,
                                    url = subtitle.file
                                )
                            )
                        }
                    }
                }
            } else if (server.status == "ok") {
                val url = "$backendUrl/${server.name}?name=$showName&year=$year&id=$id&imdb=$imdb&season=$season&episode=$episode"
                val document = app.get(url)
                val sourceTree = objectMapper.readTree(document.text)
                val source = sourceTree.get("url")?.textValue()
                
                if (source != null) {
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
        } catch (e: Exception) {
            Log.e("XPR", "Error in loadTvServer: ${e.message}", e)
        }
    }
}