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
        "$mainUrl/trending/movie/week?api_key=$apiKey&language=en-US&page=" to "Filmler",
        "$mainUrl/trending/tv/week?api_key=$apiKey&language=en-US&page=" to "Diziler"

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page.toString()
        Log.d("XPR", "URL -> $url")
        
        return try {
            val home = if (request.name == "Filmler") {
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
        val title = this.title ?: this.originalTitle ?: "Bilinmeyen Film"
        val href = "${this.id}|movie" 
        val posterUrl = if (this.posterPath != null) imgUrl + this.posterPath else ""

        return newMovieSearchResponse(title, href, TvType.Movie) { 
            this.posterUrl = posterUrl 
        }
    }

    private fun XTvShow.toTvSearchResponse(): SearchResponse {
        val title = this.name ?: this.originalName ?: "Bilinmeyen Dizi"
        val href = "${this.id}|tv" 
        val posterUrl = if (this.posterPath != null) imgUrl + this.posterPath else ""

        return newAnimeSearchResponse(title, href, TvType.TvSeries) { 
            this.posterUrl = posterUrl 
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return try {
            Log.d("XPR", "query -> $query")
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "${mainUrl}/search/multi?api_key=$apiKey&query=$encodedQuery&page=1&language=en-US"
            Log.d("XPR", "Search url -> $url")
            val document = app.get(url)
            Log.d("XPR", "Search response: ${document.text}")
            
            val searchResponse = objectMapper.readValue<SearchMultiResponse>(document.text)

            searchResponse.results.mapNotNull { result ->
                when (result.mediaType) {
                    "movie" -> {
                        val movie = result as? XMovie
                        movie?.toMovieSearchResponse()
                    }
                    "tv" -> {
                        val tv = result as? XTvShow
                        tv?.toTvSearchResponse()
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
            val parts = url.split("|")
            if (parts.size != 2) {
                Log.e("XPR", "Invalid URL format: $url")
                return null
            }
            
            val id = parts[0]
            val type = parts[1]
            
            Log.d("XPR", "Loading: type -> $type, id -> $id")
            
            if (type == "movie") {
                loadMovie(id)
            } else if (type == "tv") {
                loadTvShow(id)
            } else {
                Log.e("XPR", "Unknown type: $type")
                null
            }
        } catch (e: Exception) {
            Log.e("XPR", "Error in load: ${e.message}", e)
            null
        }
    }

    private suspend fun loadMovie(id: String): LoadResponse? {
        return try {
            val movieUrl = "$mainUrl/movie/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations,videos"
            Log.d("XPR", "movieUrl -> $movieUrl")
            val document = app.get(movieUrl)
            Log.d("XPR", "Movie details response: ${document.text}")
            
            val movie = objectMapper.readValue<XMovie>(document.text)

            val title = movie.title ?: movie.originalTitle ?: "Bilinmeyen Film"
            val poster = if (movie.posterPath != null) imgUrl + movie.posterPath else ""
            val background = if (movie.backdropPath != null) backImgUrl + movie.backdropPath else ""
            val description = movie.overview ?: "Açıklama bulunamadı"
            val year = movie.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val tags = movie.genres?.map { it.name } ?: emptyList()
            val rating = movie.vote?.let { (it * 10).toInt() } 
            val duration = movie.runtime
            
            
            var trailer = ""
            try {
                val trailerUrl = "$mainUrl/movie/$id/videos?api_key=$apiKey"
                val trailerDoc = app.get(trailerUrl)
                val trailers = objectMapper.readValue<Trailers>(trailerDoc.text)
                val youtubeTrailer = trailers.results.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
                if (youtubeTrailer != null) {
                    trailer = "https://www.youtube.com/watch?v=${youtubeTrailer.key}"
                }
            } catch (e: Exception) {
                Log.e("XPR", "Error loading trailer: ${e.message}")
            }
            
            val actors = movie.credits?.cast?.take(10)?.map { 
                Actor(it.name, if (it.profilePath != null) imgUrl + it.profilePath else "") 
            } ?: emptyList()
            
            val recommendations = movie.recommendations?.results?.take(20)?.map { it.toMovieSearchResponse() } ?: emptyList()
            
            newMovieLoadResponse(title, "$id|movie", TvType.Movie, "$id|movie") {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.plot = description
                this.year = year
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.recommendations = recommendations
                if (actors.isNotEmpty()) addActors(actors)
                if (trailer.isNotEmpty()) addTrailer(trailer)
            }
        } catch (e: Exception) {
            Log.e("XPR", "Error loading movie: ${e.message}", e)
            null
        }
    }

    private suspend fun loadTvShow(id: String): LoadResponse? {
        return try {
            val tvUrl = "$mainUrl/tv/$id?api_key=$apiKey&language=en-US&append_to_response=credits,recommendations,videos"
            Log.d("XPR", "tvUrl -> $tvUrl")
            val document = app.get(tvUrl)
            Log.d("XPR", "TV details response: ${document.text}")
            
            val tvShow = objectMapper.readValue<XTvShow>(document.text)

            val title = tvShow.name ?: tvShow.originalName ?: "Bilinmeyen Dizi"
            val poster = if (tvShow.posterPath != null) imgUrl + tvShow.posterPath else ""
            val background = if (tvShow.backdropPath != null) backImgUrl + tvShow.backdropPath else ""
            val description = tvShow.overview ?: "Açıklama bulunamadı"
            val year = tvShow.firstAirDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val tags = tvShow.genres?.map { it.name } ?: emptyList()
            val rating = tvShow.vote?.let { (it * 10).toInt() } 
            
            
            var trailer = ""
            try {
                val trailerUrl = "$mainUrl/tv/$id/videos?api_key=$apiKey"
                val trailerDoc = app.get(trailerUrl)
                val trailers = objectMapper.readValue<Trailers>(trailerDoc.text)
                val youtubeTrailer = trailers.results.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
                if (youtubeTrailer != null) {
                    trailer = "https://www.youtube.com/watch?v=${youtubeTrailer.key}"
                }
            } catch (e: Exception) {
                Log.e("XPR", "Error loading trailer: ${e.message}")
            }
            
            val actors = tvShow.credits?.cast?.take(10)?.map { 
                Actor(it.name, if (it.profilePath != null) imgUrl + it.profilePath else "") 
            } ?: emptyList()
            
            val recommendations = tvShow.recommendations?.results?.take(20)?.map { it.toTvSearchResponse() } ?: emptyList()
            
            
            val episodes = mutableListOf<Episode>()
            tvShow.seasons?.forEach { season ->
                if (season.seasonNumber > 0) { 
                    try {
                        val seasonUrl = "$mainUrl/tv/$id/season/${season.seasonNumber}?api_key=$apiKey&language=en-US"
                        Log.d("XPR", "Loading season: $seasonUrl")
                        val seasonDoc = app.get(seasonUrl)
                        val seasonData = objectMapper.readValue<Season>(seasonDoc.text)
                        
                        seasonData.episodes?.forEach { episode ->
                            val episodeTitle = if (episode.name.isNullOrBlank()) {
                                "${season.seasonNumber}. Sezon ${episode.episodeNumber}. Bölüm"
                            } else {
                                episode.name
                            }
                            
                            episodes.add(
                                newEpisode("$id|tv|${season.seasonNumber}|${episode.episodeNumber}") {
                                    this.name = episodeTitle
                                    this.season = season.seasonNumber
                                    this.episode = episode.episodeNumber
                                    this.posterUrl = if (episode.stillPath != null) imgUrl + episode.stillPath else poster
                                    this.description = episode.overview ?: "Bölüm açıklaması bulunamadı"
                                    this.rating = episode.voteAverage?.let { (it * 10).toInt() }
                                }
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("XPR", "Error loading season ${season.seasonNumber}: ${e.message}")
                    }
                }
            }
            
            Log.d("XPR", "Loaded ${episodes.size} episodes for TV show: $title")
            
            newTvSeriesLoadResponse(title, "$id|tv", TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.plot = description
                this.year = year
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
                if (actors.isNotEmpty()) addActors(actors)
                if (trailer.isNotEmpty()) addTrailer(trailer)
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
            Log.d("XPR", "Loading links for data: $data")
            val parts = data.split("|")
            
            if (parts.size < 2) {
                Log.e("XPR", "Invalid data format: $data")
                return false
            }
            
            val id = parts[0]
            val type = parts[1]
            
            if (type == "movie") {
                loadMovieLinks(id, subtitleCallback, callback)
            } else if (type == "tv" && parts.size >= 4) {
                val season = parts[2].toIntOrNull() ?: return false
                val episode = parts[3].toIntOrNull() ?: return false
                loadTvLinks(id, season, episode, subtitleCallback, callback)
            } else {
                Log.e("XPR", "Invalid data format for type $type: $data")
                false
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
            Log.d("XPR", "Loading movie links for ID: $id")
            
            
            val movieUrl = "$mainUrl/movie/$id?api_key=$apiKey&language=en-US"
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
                Log.d("XPR", "Loaded ${subtitles.size} subtitles")
            } catch (e: Exception) {
                Log.e("XPR", "Error loading subtitles: ${e.message}")
            }
            
            
            try {
                val serversUrl = "$backendUrl/servers"
                val serversResponse = app.get(serversUrl)
                val servers = objectMapper.readValue<Servers>(serversResponse.text)
                Log.d("XPR", "Found ${servers.servers?.size ?: 0} servers")
                
                servers.servers?.forEach { server ->
                    if (server.status == "ok") {
                        try {
                            loadMovieServer(server, id, movie, callback, subtitleCallback)
                        } catch (e: Exception) {
                            Log.e("XPR", "Error loading server ${server.name}: ${e.message}")
                        }
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
            Log.d("XPR", "Loading TV links for ID: $id, Season: $season, Episode: $episode")
            
            
            val tvUrl = "$mainUrl/tv/$id?api_key=$apiKey&language=en-US"
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
                Log.d("XPR", "Loaded ${subtitles.size} subtitles")
            } catch (e: Exception) {
                Log.e("XPR", "Error loading subtitles: ${e.message}")
            }
            
            
            try {
                val serversUrl = "$backendUrl/servers"
                val serversResponse = app.get(serversUrl)
                val servers = objectMapper.readValue<Servers>(serversResponse.text)
                Log.d("XPR", "Found ${servers.servers?.size ?: 0} servers")
                
                servers.servers?.forEach { server ->
                    if (server.status == "ok") {
                        try {
                            loadTvServer(server, id, tvShow, season, episode, callback, subtitleCallback)
                        } catch (e: Exception) {
                            Log.e("XPR", "Error loading server ${server.name}: ${e.message}")
                        }
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
            val movieName = movie.originalTitle ?: movie.title ?: return
            val year = movie.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val imdb = movie.imdb
            
            Log.d("XPR", "Loading server: ${server.name} for movie: $movieName")
            
            if (server.name == "primebox") {
                val url = "$backendUrl/primebox?name=${java.net.URLEncoder.encode(movieName, "UTF-8")}&year=$year&fallback_year=${year?.minus(1)}"
                Log.d("XPR", "Primebox URL: $url")
                val document = app.get(url)
                val streamText = document.text
                Log.d("XPR", "Primebox response: $streamText")
                val stream = objectMapper.readValue<Stream>(streamText)
                
                stream.qualities.forEach { quality ->
                    val streamTree = objectMapper.readTree(streamText)
                    val source = streamTree.get("streams")?.get(quality)?.textValue()
                    if (source != null) {
                        callback.invoke(
                            newExtractorLink(
                                source = "${server.name.capitalize()} - $quality",
                                name = "${server.name.capitalize()} - $quality",
                                url = source,
                                ExtractorLinkType.VIDEO
                            ) {
                                this.quality = getQualityFromName(quality)
                                this.headers = mapOf("Origin" to mainUrl)
                                this.referer = xUrl
                            }
                        )
                        Log.d("XPR", "Added link: ${server.name} - $quality")
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
            } else {
                val url = "$backendUrl/${server.name}?name=${java.net.URLEncoder.encode(movieName, "UTF-8")}&year=$year&id=$id&imdb=$imdb"
                Log.d("XPR", "Server URL: $url")
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
                    Log.d("XPR", "Added link: ${server.name}")
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
            val showName = tvShow.originalName ?: tvShow.name ?: return
            val year = tvShow.firstAirDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val imdb = tvShow.imdb
            
            Log.d("XPR", "Loading server: ${server.name} for show: $showName S${season}E${episode}")
            
            if (server.name == "primebox") {
                val url = "$backendUrl/primebox?name=${java.net.URLEncoder.encode(showName, "UTF-8")}&fallback_year=${year?.minus(1)}&season=$season&episode=$episode"
                Log.d("XPR", "Primebox URL: $url")
                val document = app.get(url)
                val streamText = document.text
                Log.d("XPR", "Primebox response: $streamText")
                val stream = objectMapper.readValue<Stream>(streamText)
                
                stream.qualities.forEach { quality ->
                    val streamTree = objectMapper.readTree(streamText)
                    val source = streamTree.get("streams")?.get(quality)?.textValue()
                    if (source != null) {
                        callback.invoke(
                            newExtractorLink(
                                source = "${server.name.capitalize()} - $quality",
                                name = "${server.name.capitalize()} - $quality",
                                url = source,
                                ExtractorLinkType.VIDEO
                            ) {
                                this.quality = getQualityFromName(quality)
                                this.headers = mapOf("Origin" to mainUrl)
                                this.referer = xUrl
                            }
                        )
                        Log.d("XPR", "Added link: ${server.name} - $quality")
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
            } else {
                val url = "$backendUrl/${server.name}?name=${java.net.URLEncoder.encode(showName, "UTF-8")}&year=$year&id=$id&imdb=$imdb&season=$season&episode=$episode"
                Log.d("XPR", "Server URL: $url")
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
                    Log.d("XPR", "Added link: ${server.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("XPR", "Error in loadTvServer: ${e.message}", e)
        }
    }
}