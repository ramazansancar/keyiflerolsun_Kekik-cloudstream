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
    override val supportedTypes = setOf(TvType.Movie)
    private val apiKey = "84259f99204eeb7d45c7e3d8e36c6123"
    private val imgUrl = "https://image.tmdb.org/t/p/w500"
    private val backImgUrl = "https://image.tmdb.org/t/p/w780"
    private val backendUrl = "https://backend.xprime.tv"
    private val xUrl = "https://xprime.tv/"
    override val mainPage = mainPageOf(
        "$mainUrl/trending/movie/week?api_key=$apiKey&language=tr-TR&page=SAYFA" to "Popüler",
        "$mainUrl/movie/now_playing?api_key=$apiKey&language=tr-TR&page=SAYFA" to "Sinemalarda",
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
        var url =
            "${mainUrl}/discover/movie?api_key=$apiKey&page=${page}&include_adult=false&with_watch_monetization_types=flatrate%7Cfree%7Cads&watch_region=TR&language=tr-TR&with_genres=${request.data}&sort_by=popularity.desc"
        if (request.name == "Popüler" || request.name == "Sinemalarda") {
           url = request.data.replace("SAYFA", page.toString())
        }
        Log.d("XPR", "URL -> $url")
        val movies = app.get(url).parsedSafe<MovieResponse>()
        val home =
            movies?.results?.map { it.toMainPageResult() }

        return newHomePageResponse(request.name, home!!)
    }

    private fun XMovie.toMainPageResult(): SearchResponse {
        val title = this.title.toString()
        val href = this.id.toString()
        val posterUrl = imgUrl + this.posterPath

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }

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

        return movies.results.filter { it.mediaType == "movie" }.map { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val id = url.split("/").last()
        Log.d("XPR", "id -> $id")
        val movieUrl =
            "$mainUrl/movie/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations"
        Log.d("XPR", "movieUrl -> $movieUrl")
        val document = app.get(movieUrl)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val movie: XMovie = objectMapper.readValue(document.text)
        Log.d("XPR", movie.toString())

        val title = movie.title
        val orgTitle = movie.originalTitle
        val totTitle =
            if (title?.isNotEmpty() == true && orgTitle != title) "$orgTitle - $title" else orgTitle
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
        //var trailer = trailers.results.filter { it.site == "Youtube" }
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
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("XPR", "data » ${data}")
        val id = data.split("/").last()
        val movieUrl =
            "$mainUrl/movie/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits,recommendations"
        Log.d("XPR", "movieUrl -> $movieUrl")
        val document = app.get(movieUrl)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val movie: XMovie = objectMapper.readValue(document.text)
        val subtitleUrl = "https://sub.wyzie.ru/search?id=$id"
        val subtitleDocument = app.get(subtitleUrl)
        val subtitles: List<Subtitle> = objectMapper.readValue(subtitleDocument.text)
        subtitles.forEach { it ->
            subtitleCallback.invoke(
                SubtitleFile(
                    lang = it.display,
                    url = it.url
                )
            )
        }
        val serversUrl = "https://backend.xprime.tv/servers"
        val servers    = app.get(serversUrl).parsedSafe<Servers>()
        servers?.servers?.forEach {
            try {
                loadServers(it, id, movie, callback, subtitleCallback)
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
            stream.qualities.forEach {
                val source = objectMapper.readTree(streamText).get("streams").get(it).textValue()
                callback.invoke(
                    newExtractorLink(
                        source = server.name.capitalize() + " - " + it,
                        name = server.name.capitalize() + " - " + it,
                        url = source,
                        ExtractorLinkType.VIDEO
                    ) {
                        this. quality = getQualityFromName(it)
                        this.headers = mapOf("Origin" to mainUrl)
                        this.referer = xUrl
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
}
