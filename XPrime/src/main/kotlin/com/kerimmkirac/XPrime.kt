package com.nikyokki

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*

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
        "37" to "Vahşi Batı"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = when {
            request.name.contains("Popüler Filmler") || request.name == "Sinemalarda" || request.name.contains("Diziler") -> {
                request.data.replace("SAYFA", page.toString())
            }
            else -> {
                "$mainUrl/discover/movie?api_key=$apiKey&page=$page&include_adult=false&with_watch_monetization_types=flatrate%7Cfree%7Cads&watch_region=TR&language=tr-TR&with_genres=${request.data}&sort_by=popularity.desc"
            }
        }
        Log.d("XPR", "URL -> $url")
        val response = app.get(url).parsedSafe<MovieResponse>()
        val home = response?.results?.map { it.toMainPageResult() } ?: emptyList()
        return newHomePageResponse(request.name, home)
    }

    private fun XMovie.toMainPageResult(): SearchResponse {
        val title = (this.title ?: this.name).toString()
        val tvType = if (this.mediaType == "tv" || this.firstAirDate != null) TvType.TvSeries else TvType.Movie
        val mediaTypePrefix = if (tvType == TvType.TvSeries) "tv" else "movie"
        val href = "$mediaTypePrefix:${this.id}"
        val posterUrl = if (this.posterPath != null) imgUrl + this.posterPath else ""

        return if (tvType == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        Log.d("XPR", "query -> $query")
        val url = "$mainUrl/search/multi?api_key=$apiKey&query=$query&page=1"
        Log.d("XPR", "Search url -> $url")
        val document = app.get(url)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val movies: MovieResponse = objectMapper.readValue(document.text)
        return movies.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .map { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.split(":")
        val mediaTypeStr = parts[0]
        val idStr = parts[1]

        // ID'yi ve mediaType'ı belirle
        val isMovie = mediaTypeStr == "movie"
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        if (isMovie) {
            // Film API çağrısı
            val movieUrl = "$mainUrl/movie/$idStr?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations,external_ids"
            val resp = app.get(movieUrl)
            val movie: XMovie = objectMapper.readValue(resp.text)

            val title = movie.title ?: ""
            val orgTitle = movie.originalTitle ?: ""
            val totTitle = if (title.isNotEmpty() && orgTitle != title) "$orgTitle - $title" else orgTitle
            val poster = if (movie.backdropPath != null) backImgUrl + movie.backdropPath else ""
            val description = movie.overview ?: ""
            val year = movie.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val tags = movie.genres?.map { it.name }
            val rating = movie.vote?.toString()?.toRatingInt()
            val duration = movie.runtime
            val trailerUrl = "$mainUrl/movie/$idStr/videos?api_key=$apiKey"
            val trailerDoc = app.get(trailerUrl)
            val trailers: Trailers = objectMapper.readValue(trailerDoc.text)
            var trailerKey = ""
            val ytTrailers = trailers.results.filter { it.site == "YouTube" }
            if (ytTrailers.isNotEmpty()) trailerKey = ytTrailers[0].key

            val actors = movie.credits?.cast?.map { Actor(it.name, imgUrl + it.profilePath) }
            val recommendations = movie.recommendations?.results?.map { it.toMainPageResult() }

            return newMovieLoadResponse(totTitle, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer("https://www.youtube.com/embed/$trailerKey")
            }

        } else {
            // Dizi API çağrısı
            val tvUrl = "$mainUrl/tv/$idStr?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations,external_ids"
            val resp = app.get(tvUrl)
            val tvSeries: XMovie = objectMapper.readValue(resp.text)

            val title = tvSeries.name ?: ""
            val orgTitle = tvSeries.originalName ?: ""
            val totTitle = if (title.isNotEmpty() && orgTitle != title) "$orgTitle - $title" else orgTitle
            val poster = if (tvSeries.backdropPath != null) backImgUrl + tvSeries.backdropPath else ""
            val description = tvSeries.overview ?: ""
            val year = tvSeries.firstAirDate?.split("-")?.firstOrNull()?.toIntOrNull()
            val tags = tvSeries.genres?.map { it.name }
            val rating = tvSeries.vote?.toString()?.toRatingInt()

            // Sezonları ve bölümleri al
            val episodes = mutableListOf<Episode>()
            tvSeries.seasons?.filter { it.seasonNumber > 0 }?.forEach { season ->
                val seasonUrl = "$mainUrl/tv/$idStr/season/${season.seasonNumber}?api_key=$apiKey&language=tr-TR"
                try {
                    val seasonResp = app.get(seasonUrl)
                    val seasonDetails: SeasonDetails = objectMapper.readValue(seasonResp.text)
                    seasonDetails.episodes.forEach { ep ->
                        episodes.add(
                            Episode(
                                data = "$idStr:${season.seasonNumber}:${ep.episodeNumber}",
                                name = ep.name ?: "Episode ${ep.episodeNumber}",
                                season = season.seasonNumber,
                                episode = ep.episodeNumber,
                                posterUrl = if (ep.stillPath != null) imgUrl + ep.stillPath else null,
                                description = ep.overview
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("XPR", "Sezon yüklenirken hata: ${e.message}")
                }
            }

            // Trailer almak
            val trailerUrl = "$mainUrl/tv/$idStr/videos?api_key=$apiKey"
            val trailerDoc = app.get(trailerUrl)
            val trailers: Trailers = objectMapper.readValue(trailerDoc.text)
            var trailerKey = ""
            val ytTrailers = trailers.results.filter { it.site == "YouTube" }
            if (ytTrailers.isNotEmpty()) trailerKey = ytTrailers[0].key

            return newTvSeriesLoadResponse(totTitle, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.rating = rating
                this.recommendations = null
                addActors(tvSeries.credits?.cast?.map { Actor(it.name, imgUrl + it.profilePath) })
                addTrailer("https://www.youtube.com/embed/$trailerKey")
            }
        }
    }
    

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("XPR", "data -> $data")
        val objMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val parts = data.split(":")
        val idStr = parts[0]
        val season = if (parts.size > 1) parts[1].toIntOrNull() else null
        val episode = if (parts.size > 2) parts[2].toIntOrNull() else null

        // ID'yi ve mediaType'ı doğru tespit et
        val isMovie = (season == null || episode == null)

        // API URL belirle
        val contentUrl = if (isMovie) {
            "$mainUrl/movie/$idStr?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations,external_ids"
        } else {
            "$mainUrl/tv/$idStr?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations,external_ids"
        }

        Log.d("XPR", "contentUrl -> $contentUrl")
        val resp = app.get(contentUrl)
        val content: XMovie = objMapper.readValue(resp.text)

        // Alt yazı
        val subtitleUrl = if (isMovie) {
            "https://sub.wyzie.ru/search?id=$idStr"
        } else {
            "https://sub.wyzie.ru/search?id=$idStr&season=$season&episode=$episode"
        }

        try {
            val subResp = app.get(subtitleUrl)
            val subs: List<Subtitle> = objMapper.readValue(subResp.text)
            subs.forEach {
                subtitleCallback.invoke(SubtitleFile(it.display, it.url))
            }
        } catch (e: Exception) {
            Log.e("XPR", "Alt yazı alınırken hata: ${e.message}")
        }

        // Sunucuları getir ve yükle
        val serversResp = app.get("https://backend.xprime.tv/servers")
        val servers = serversResp.parsedSafe<Servers>()
        servers?.servers?.forEach { server ->
            try {
                loadServers(server, idStr, content, callback, subtitleCallback, season, episode)
            } catch (e: Exception) {
                Log.e("XPR", "Sunucu yüklenirken hata: ${e.message}")
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
        val name = content.originalTitle ?: content.name ?: content.originalName
        val year = content.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            ?: content.firstAirDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val imdb = content.imdb
        val objMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val isMovie = (season == null || episode == null)

        if (server.name == "primebox" && server.status == "ok") {
            val url = if (isMovie) {
                "$backendUrl/primebox?name=$name&year=$year&fallback_year=${year?.minus(1)}"
            } else {
                "$backendUrl/primebox?name=$name&year=$year&id=$id&season=$season&episode=$episode"
            }
            val resp = app.get(url)
            val stream = objMapper.readValue(resp.text) as Stream
            stream.qualities.forEach {
                val source = objMapper.readTree(resp.text).get("streams").get(it).asText()
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
                        SubtitleFile(it.label.toString(), it.file.toString())
                    )
                }
            }
        } else {
            if (server.status == "ok") {
                val url = if (isMovie) {
                    "$backendUrl/${server.name}?name=$name&year=$year&id=$id&imdb=$imdb"
                } else {
                    "$backendUrl/${server.name}?name=$name&year=$year&id=$id&season=$season&episode=$episode"
                }
                val resp = app.get(url)
                val source = objMapper.readTree(resp.text).get("url").asText()
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