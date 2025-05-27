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
        "$mainUrl/tv/on_the_air?api_key=$apiKey&language=tr-TR&page=SAYFA" to "Yayında Olan Diziler",
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
            request.name.contains("Popüler Filmler") || request.name == "Sinemalarda" -> {
                request.data.replace("SAYFA", page.toString())
            }
            request.name.contains("Diziler") -> {
                request.data.replace("SAYFA", page.toString())
            }
            else -> {
                "${mainUrl}/discover/movie?api_key=$apiKey&page=${page}&include_adult=false&with_watch_monetization_types=flatrate%7Cfree%7Cads&watch_region=TR&language=tr-TR&with_genres=${request.data}&sort_by=popularity.desc"
            }
        }
        Log.d("XPR", "URL -> $url")
        val response = app.get(url).parsedSafe<MovieResponse>()
        val home = response?.results?.map { it.toMainPageResult() }

        return newHomePageResponse(request.name, home!!)
    }

    private fun XMovie.toMainPageResult(): SearchResponse {
        val title = (this.title ?: this.name).toString()
        val tvType = when (this.mediaType) {
            "tv" -> TvType.TvSeries
            "movie" -> TvType.Movie
            else -> if (this.firstAirDate != null) TvType.TvSeries else TvType.Movie
        }
        val mediaTypePrefix = if (tvType == TvType.TvSeries) "tv" else "movie"
        val href = "$mediaTypePrefix/${this.id}"
        val posterUrl = imgUrl + this.posterPath

        return if (tvType == TvType.TvSeries) {
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

        return movies.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }.map { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val urlParts = url.split("/")
        var mediaType = urlParts[0] // "movie" or "tv"
        val id = urlParts[1]

        Log.d("XPR", "mediaType -> $mediaType, id -> $id")

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val movieUrl = "$mainUrl/movie/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations,external_ids"
        val tvUrl = "$mainUrl/tv/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations,external_ids"

        var isMovie = mediaType == "movie"
        var responseText: String? = null

        try {
            responseText = app.get(movieUrl).text
            mediaType = "movie"
            isMovie = true
        } catch (e: Exception) {
            try {
                responseText = app.get(tvUrl).text
                mediaType = "tv"
                isMovie = false
            } catch (e: Exception) {
                Log.e("XPR", "ID hem movie hem tv değil: $id")
                return null
            }
        }

        if (isMovie) {
            val movie: XMovie = objectMapper.readValue(responseText!!)
            Log.d("XPR", "Movie: $movie")

            val title = movie.title
            val orgTitle = movie.originalTitle
            val totTitle = if (title?.isNotEmpty() == true && orgTitle != title) "$orgTitle - $title" else orgTitle
            val poster = backImgUrl + movie.backdropPath
            val description = movie.overview
            val year = movie.releaseDate?.split("-")?.first()?.toIntOrNull()
            val tags = movie.genres?.map { it.name }
            val rating = movie.vote.toString().toRatingInt()
            val duration = movie.runtime
            val trailerUrl = "$mainUrl/movie/$id/videos?api_key=$apiKey"
            val trailerDoc = app.get(trailerUrl)
            val trailers: Trailers = try {
                objectMapper.readValue(trailerDoc.text)
            } catch (e: Exception) {
                Log.e("XPR", "Trailer parse error: ${e.message}")
                Trailers(0, emptyList())
            }
            var trailer = ""
            if (trailers.results.filter { it.site == "YouTube" }.isNotEmpty()) {
                trailer = trailers.results.filter { it.site == "YouTube" }[0].key
            }
            val actors = movie.credits?.cast?.map { Actor(it.name, imgUrl + it.profilePath) }
            val recommendations = movie.recommendations?.results?.map { it.toMainPageResult() }

            return newMovieLoadResponse(totTitle.toString(), url, TvType.Movie, url) {
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
            val tvSeries: XMovie = objectMapper.readValue(responseText!!)
            Log.d("XPR", "TV Series: $tvSeries")

            val title = tvSeries.name
            val orgTitle = tvSeries.originalName
            val totTitle = if (title?.isNotEmpty() == true && orgTitle != title) "$orgTitle - $title" else orgTitle
            val poster = backImgUrl + tvSeries.backdropPath
            val description = tvSeries.overview
            val year = tvSeries.firstAirDate?.split("-")?.first()?.toIntOrNull()
            val tags = tvSeries.genres?.map { it.name }
            val rating = tvSeries.vote.toString().toRatingInt()
            val trailerUrl = "$mainUrl/tv/$id/videos?api_key=$apiKey"
            val trailerDoc = app.get(trailerUrl)
            val trailers: Trailers = try {
                objectMapper.readValue(trailerDoc.text)
            } catch (e: Exception) {
                Log.e("XPR", "Trailer parse error: ${e.message}")
                Trailers(0, emptyList())
            }
            var trailer = ""
            if (trailers.results.filter { it.site == "YouTube" }.isNotEmpty()) {
                trailer = trailers.results.filter { it.site == "YouTube" }[0].key
            }
            val actors = tvSeries.credits?.cast?.map { Actor(it.name, imgUrl + it.profilePath) }
            val recommendations = tvSeries.recommendations?.results?.map { it.toMainPageResult() }

            // Get seasons and episodes
            val episodes = mutableListOf<Episode>()
            tvSeries.seasons?.filter { it.seasonNumber > 0 }?.forEach { season ->
                val seasonUrl = "$mainUrl/tv/$id/season/${season.seasonNumber}?api_key=$apiKey&language=tr-TR"
                try {
                    val seasonDoc = app.get(seasonUrl)
                    val seasonDetails: SeasonDetails = objectMapper.readValue(seasonDoc.text)
                    seasonDetails.episodes.forEach { episode ->
                        episodes.add(
                            Episode(
                                data = "$id/${season.seasonNumber}/${episode.episodeNumber}",
                                name = episode.name ?: "Episode ${episode.episodeNumber}",
                                season = season.seasonNumber,
                                episode = episode.episodeNumber,
                                posterUrl = if (episode.stillPath != null) imgUrl + episode.stillPath else null,
                                description = episode.overview
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("XPR", "Error loading season ${season.seasonNumber}: ${e.message}")
                }
            }

            return newTvSeriesLoadResponse(totTitle.toString(), url, TvType.TvSeries, episodes) {
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
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("XPR", "data » ${data}")

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        // Check if data contains episode info (format: id/season/episode)
        val dataParts = data.split("/")
        val id = dataParts[0]
        val season = if (dataParts.size > 1) dataParts[1].toIntOrNull() else null
        val episode = if (dataParts.size > 2) dataParts[2].toIntOrNull() else null

        val isMovie = season == null || episode == null

        // Get content details
        val contentUrl = if (isMovie) {
            "$mainUrl/movie/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations,external_ids"
        } else {
            "$mainUrl/tv/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations,external_ids"
        }

        Log.d("XPR", "contentUrl -> $contentUrl")
        val document = app.get(contentUrl)
        val content: XMovie = objectMapper.readValue(document.text)

        // Get subtitles
        val subtitleUrl = if (isMovie) {
            "https://sub.wyzie.ru/search?id=$id"
        } else {
            "https://sub.wyzie.ru/search?id=$id&season=$season&episode=$episode"
        }

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

        // Get servers and load streams
        val serversUrl = "https://backend.xprime.tv/servers"
        val servers = app.get(serversUrl).parsedSafe<Servers>()
        servers?.servers?.forEach { server ->
            try {
                loadServers(server, id, content, callback, subtitleCallback, season, episode)
            } catch (e: Exception) {
                Log.e("XPR", "Error loading server ${server.name}: ${e.message}")
                e.printStackTrace()
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
        val contentName = content.originalTitle ?: content.name ?: content.originalName
        val year = content.releaseDate?.split("-")?.first()?.toIntOrNull() 
            ?: content.firstAirDate?.split("-")?.first()?.toIntOrNull()
        val imdb = content.imdb
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val isMovie = season == null || episode == null

        if (server.name == "primebox" && server.status == "ok") {
            val url = if (isMovie) {
                "$backendUrl/primebox?name=$contentName&year=$year&fallback_year=${year?.minus(1)}"
            } else {
                "$backendUrl/primebox?name=$contentName&year=$year&id=$id&season=$season&episode=$episode"
            }

            val document = app.get(url)
            val streamText = document.text
            val stream: Stream = objectMapper.readValue(streamText)
            stream.qualities.forEach {
                val source = objectMapper.readTree(streamText).get("streams").get(it).textValue()
                callback.invoke(
                    newExtractorLink(
                        source = server.name.capitalize() + " - " + it,
                        name = server.name.capitalize() + " - " + it,
                        url = source,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.quality = getQualityFromName(it)
                        this.headers = mapOf("Origin" to "https://xprime.tv/")
                        this.referer = "https://xprime.tv/"
                    }
                )
            }
            if (stream.hasSubtitles) {
                stream.subtitles.forEach {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = it.label.toString(),
                            url = it.file.toString()
                        )
                    )
                }
            }
        } else {
            if (server.status == "ok") {
                val url = if (isMovie) {
                    "$backendUrl/${server.name}?name=$contentName&year=$year&id=$id&imdb=$imdb"
                } else {
                    "$backendUrl/${server.name}?name=$contentName&year=$year&id=$id&season=$season&episode=$episode"
                }

                val document = app.get(url)
                val source = objectMapper.readTree(document.text).get("url").textValue()
                callback.invoke(
                    newExtractorLink(
                        source = server.name.capitalize(),
                        name = server.name.capitalize(),
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