package com.kerimmkirac

import android.util.Log
import com.lagradost.cloudstream3.*
import java.net.URLEncoder



import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Xprime : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "XPrime"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private val apiKey = "84259f99204eeb7d45c7e3d8e36c6123"

    override val mainPage = mainPageOf(
        "$mainUrl/trending/tv/week?api_key=$apiKey" to "Trending Tv Shows",
        "$mainUrl/trending/movie/week?api_key=$apiKey" to "Trending Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}&page=$page"
        

        try {
            val response = app.get(url).text
            
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return newHomePageResponse(emptyList()).also {
                
            }

            val items = mutableListOf<SearchResponse>()
            val isMovie = request.data.contains("/movie/")

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)

                val id = item.optInt("id", 0)
                if (id == 0) {
                    
                    continue
                }

                val title = if (isMovie) {
                    item.optString("title", "")
                } else {
                    item.optString("name", "")
                }
                
                if (title.isEmpty()) {
                    
                    continue
                }

                val posterPath = item.optString("poster_path", "")
                val poster = if (posterPath.isNotEmpty()) {
                    "https://image.tmdb.org/t/p/w500$posterPath"
                } else null

                val overview = item.optString("overview", "")
                val dateField = if (isMovie) "release_date" else "first_air_date"
                val releaseDate = item.optString(dateField, "")
                val score = item.optDouble("vote_average", 0.0)

                val year = if (releaseDate.isNotEmpty()) {
                    releaseDate.split("-").getOrNull(0)
                } else null

                val link = if (isMovie) "tmdb://movie/$id" else "tmdb://tv/$id"
                

                val searchResponse = if (isMovie) {
                    newMovieSearchResponse(title, link, TvType.Movie) {
                        this.posterUrl = poster
                         this.score = Score.from10(score)
                    }
                } else {
                    newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                        this.posterUrl = poster
                         this.score = Score.from10(score)
                    }
                }

                items.add(searchResponse)
            }

            
            return newHomePageResponse(request.name, items)
        } catch (e: Exception) {
            
            return newHomePageResponse(emptyList())
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/search/multi?api_key=$apiKey&query=$encodedQuery"
        
        return try {
            val response = app.get(searchUrl).text
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return emptyList()
            
            val items = mutableListOf<SearchResponse>()

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val id = item.optInt("id", 0)
                val mediaType = item.optString("media_type", "")
                
                
                if (id == 0 || (mediaType != "movie" && mediaType != "tv")) {
                    continue
                }

                val title = if (mediaType == "movie") {
                    item.optString("title", "")
                } else {
                    item.optString("name", "")
                }
                
                if (title.isEmpty()) continue
                val score = item.optDouble("vote_average", 0.0)
                val posterPath = item.optString("poster_path", "")
                val poster = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else null

                val link = "tmdb://$mediaType/$id"
                
                val searchResult = if (mediaType == "movie") {
                    newMovieSearchResponse(title, link, TvType.Movie) {
                        this.posterUrl = poster
                        this.score = Score.from10(score)
                    }
                } else {
                    newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                        this.posterUrl = poster
                        this.score = Score.from10(score)
                    }
                }
                
                items.add(searchResult)
            }

            
            items
        } catch (e: Exception) {
            
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private suspend fun getTrailer(mediaType: String, mediaId: String): String? {
        val videosUrl = "$mainUrl/$mediaType/$mediaId/videos?api_key=$apiKey"
        return try {
            val response = app.get(videosUrl).text
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return null
            
           
            for (i in 0 until results.length()) {
                val video = results.getJSONObject(i)
                val type = video.optString("type", "")
                val site = video.optString("site", "")
                val key = video.optString("key", "")
                val official = video.optBoolean("official", false)
                
                if (type == "Trailer" && site == "YouTube" && key.isNotEmpty() && official) {
                    return "https://www.youtube.com/watch?v=$key"
                }
            }
            
            
            for (i in 0 until results.length()) {
                val video = results.getJSONObject(i)
                val type = video.optString("type", "")
                val site = video.optString("site", "")
                val key = video.optString("key", "")
                
                if (type == "Trailer" && site == "YouTube" && key.isNotEmpty()) {
                    return "https://www.youtube.com/watch?v=$key"
                }
            }
            
            null
        } catch (e: Exception) {
            
            null
        }
    }

    private suspend fun getActors(mediaType: String, mediaId: String): List<Pair<Actor, String?>> {
    val creditsUrl = "$mainUrl/$mediaType/$mediaId/credits?api_key=$apiKey"
    return try {
        val response = app.get(creditsUrl).text
        val json = JSONObject(response)
        val cast = json.optJSONArray("cast") ?: return emptyList()
        
        val actors = mutableListOf<Pair<Actor, String?>>()
        val maxActors = minOf(cast.length(), 13) 
        
        for (i in 0 until maxActors) {
            val actor = cast.getJSONObject(i)
            val name = actor.optString("name", "")
            val profilePath = actor.optString("profile_path", "")
            val character = actor.optString("character", "")
            
            if (name.isNotEmpty()) {
                val profileUrl = if (profilePath.isNotEmpty()) {
                    "https://image.tmdb.org/t/p/w185$profilePath"
                } else null
                
                val actorObj = Actor(name, profileUrl)
                actors.add(Pair(actorObj, character.takeIf { it.isNotEmpty() }))
            }
        }
        actors
    } catch (e: Exception) {
        
        emptyList()
    }
}

    override suspend fun load(url: String): LoadResponse? {
    Log.d("Xprime", "load() called with URL: $url")

    var cleanUrl = url
    if (url.contains("$mainUrl/tmdb://")) {
        cleanUrl = url.replace("$mainUrl/", "")
        Log.w("Xprime", " incorrect url fix: $url to $cleanUrl")
    }

    val isMovie = cleanUrl.startsWith("tmdb://movie/")
    val isTv = cleanUrl.startsWith("tmdb://tv/")
    
    if (!isMovie && !isTv) {
        
        return null
    }

    val mediaId = if (isMovie) {
        cleanUrl.removePrefix("tmdb://movie/")
    } else {
        cleanUrl.removePrefix("tmdb://tv/")
    }
    
    if (mediaId.isEmpty()) {
        
        return null
    }

    val mediaType = if (isMovie) "movie" else "tv"
    val apiUrl = "$mainUrl/$mediaType/$mediaId?api_key=$apiKey"
    Log.d("Xprime", "Constructed API URL: $apiUrl")

    try {
        val response = app.get(apiUrl).text
        val json = JSONObject(response)

        val title = if (isMovie) {
            json.optString("title", "")
        } else {
            json.optString("name", "")
        }
        
        if (title.isEmpty()) return null
        
        val poster = json.optString("poster_path", "").takeIf { it.isNotEmpty() }?.let {
            "https://image.tmdb.org/t/p/w500$it"
        }
        val backdrop = json.optString("backdrop_path", "").takeIf { it.isNotEmpty() }?.let {
            "https://image.tmdb.org/t/p/w1280$it"
        }
        val description = json.optString("overview", "")
        
        val year = if (isMovie) {
            json.optString("release_date", "")
        } else {
            json.optString("first_air_date", "")
        }.split("-").getOrNull(0)?.toIntOrNull()

        val tags = json.optJSONArray("genres")?.let { arr ->
            List(arr.length()) { i -> arr.getJSONObject(i).optString("name", "") }
        }?.filter { it.isNotEmpty() } ?: emptyList()

        val score = json.optDouble("vote_average", 0.0)

        
        val status = if (!isMovie) {
            json.optString("status", "")
        } else null

        
        val countries = if (isMovie) {
            json.optJSONArray("production_countries")?.let { arr ->
                List(arr.length()) { i -> 
                    arr.getJSONObject(i).optString("name", "")
                }.filter { it.isNotEmpty() }
            } ?: emptyList()
        } else {
            json.optJSONArray("origin_country")?.let { arr ->
                val countryCodes = List(arr.length()) { i -> arr.getString(i) }
                countryCodes.mapNotNull { code ->
                    when (code) {
                        "US" -> "United States"
                        "KR" -> "South Korea"
                        "GB" -> "United Kingdom"
                        "JP" -> "Japan"
                        "FR" -> "France"
                        "DE" -> "Germany"
                        "ES" -> "Spain"
                        "IT" -> "Italy"
                        "CA" -> "Canada"
                        "AU" -> "Australia"
                        "TR" -> "Turkey"
                        "IN" -> "India"
                        "CN" -> "China"
                        "BR" -> "Brazil"
                        "MX" -> "Mexico"
                        "RU" -> "Russia"
                        "AR" -> "Argentina"
                        "NL" -> "Netherlands"
                        "SE" -> "Sweden"
                        "NO" -> "Norway"
                        "DK" -> "Denmark"
                        "FI" -> "Finland"
                        else -> code
                    }
                }
            } ?: emptyList()
        }

        
val showStatus = if (!isMovie) {
    when (status?.lowercase()) {  
        "returning series", "in production" -> ShowStatus.Ongoing
        "ended", "canceled" -> ShowStatus.Completed
        else -> null
    }
} else null

        
        val actors = getActors(mediaType, mediaId)
        val trailerUrl = getTrailer(mediaType, mediaId)

        
        val recommendedUrl = "$mainUrl/$mediaType/$mediaId/recommendations?api_key=$apiKey"
        val recommendedResponse = app.get(recommendedUrl).text
        val recommendedJson = JSONObject(recommendedResponse)
        val recommendedResults = recommendedJson.optJSONArray("results")
        val recommendations = mutableListOf<SearchResponse>()

        if (recommendedResults != null) {
            for (i in 0 until minOf(recommendedResults.length(), 10)) {
                val rec = recommendedResults.getJSONObject(i)
                val recId = rec.optInt("id")
                val recTitle = if (isMovie) {
                    rec.optString("title", "")
                } else {
                    rec.optString("name", "")
                }
                val recPosterPath = rec.optString("poster_path", "")
                val recPoster = if (recPosterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$recPosterPath" else null

                if (recId != 0 && recTitle.isNotEmpty()) {
                    val recLink = "tmdb://$mediaType/$recId"
                    val recItem = if (isMovie) {
                        newMovieSearchResponse(recTitle, recLink, TvType.Movie) {
                            this.posterUrl = recPoster
                            this.score = Score.from10(score)
                        }
                    } else {
                        newTvSeriesSearchResponse(recTitle, recLink, TvType.TvSeries) {
                            this.posterUrl = recPoster
                            this.score = Score.from10(score)
                        }
                    }
                    recommendations.add(recItem)
                }
            }
        }

        return if (isMovie) {
            
            newMovieLoadResponse(title, "tmdb://movie/$mediaId", TvType.Movie, "tmdb://movie/$mediaId") {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = description
                this.year = year
                this.tags = if (countries.isNotEmpty()) (tags + countries).distinct() else tags
                this.score = Score.from10(score)
                this.recommendations = recommendations
                addActors(actors)
                trailerUrl?.let { addTrailer(it) }
            }
        } else {
            
            val episodes = mutableListOf<Episode>()
            val seasons = json.optJSONArray("seasons")
            if (seasons != null) {
                for (i in 0 until seasons.length()) {
                    val season = seasons.getJSONObject(i)
                    val seasonNumber = season.optInt("season_number", 0)
                    if (seasonNumber == 0) continue 

                    val seasonUrl = "$mainUrl/tv/$mediaId/season/$seasonNumber?api_key=$apiKey"
                    try {
                        val seasonJson = JSONObject(app.get(seasonUrl).text)
                        val eps = seasonJson.optJSONArray("episodes") ?: continue

                        for (j in 0 until eps.length()) {
                            val ep = eps.getJSONObject(j)
                            val epNumber = ep.optInt("episode_number", 0)
                            val epTitle = ep.optString("name", "Episode $epNumber")
                            val epDesc = ep.optString("overview", "")
                            val stillPath = ep.optString("still_path", "")
                            val epPoster = if (stillPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$stillPath" else null
                            val epData = "tmdb://tv/$mediaId/season/$seasonNumber/episode/$epNumber"

                            episodes.add(
                                newEpisode(epData) {
    name = epTitle
    this.season = seasonNumber
    episode = epNumber
    this.posterUrl = epPoster
    this.description = epDesc
}

                            )                    }
                    } catch (e: Exception) {
                        
                        continue
                    }
                }
            }

            newTvSeriesLoadResponse(title, "tmdb://tv/$mediaId", TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = description
                this.year = year
                this.tags = if (countries.isNotEmpty()) (tags + countries).distinct() else tags
                this.score = Score.from10(score)
                this.recommendations = recommendations
                this.showStatus = showStatus
                addActors(actors)
                trailerUrl?.let { addTrailer(it) }
            }
        }
    } catch (e: Exception) {
        
        return null
    }
}

    private suspend fun getAvailableServers(): List<String> {
        return try {
            val response = app.get("https://backend.xprime.tv/servers").text
            val json = JSONObject(response)
            val servers = json.optJSONArray("servers") ?: return emptyList()
            
            val availableServers = mutableListOf<String>()
            for (i in 0 until servers.length()) {
                val server = servers.getJSONObject(i)
                val name = server.optString("name", "")
                val status = server.optString("status", "")
                
                if (name.isNotEmpty() && status == "ok") {
                    availableServers.add(name)
                }
            }
            
            Log.d("Xprime", "Available servers: $availableServers")
            availableServers
        } catch (e: Exception) {
            Log.e("Xprime", "Failed to fetch servers: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getImdbId(mediaType: String, mediaId: String): String? {
        return try {
            val url = "$mainUrl/$mediaType/$mediaId?api_key=$apiKey&append_to_response=external_ids"
            val response = app.get(url).text
            val json = JSONObject(response)
            val externalIds = json.optJSONObject("external_ids")
            externalIds?.optString("imdb_id", "")?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            
            null
        }
    }

    private suspend fun fetchSubtitles(tmdbId: String, season: String? = null, episode: String? = null): List<SubtitleFile> {
        return try {
            val url = if (season != null && episode != null) {
                "https://sub.wyzie.ru/search?id=$tmdbId&season=$season&episode=$episode"
            } else {
                "https://sub.wyzie.ru/search?id=$tmdbId"
            }
            
            Log.d("Xprime", "Fetching subtitles from: $url")
            val response = app.get(url).text
            val jsonArray = JSONArray(response)
            val subtitles = mutableListOf<SubtitleFile>()
            
            for (i in 0 until jsonArray.length()) {
                val subtitle = jsonArray.getJSONObject(i)
                val subtitleUrl = subtitle.optString("url", "")
                val language = subtitle.optString("display", "")
                val languageCode = subtitle.optString("language", "")
                
                if (subtitleUrl.isNotEmpty() && language.isNotEmpty()) {
                    subtitles.add(
                        SubtitleFile(
                            lang = language,
                            url = subtitleUrl
                        )
                    )
                    Log.d("Xprime", "Found subtitle: $language ($languageCode) - $subtitleUrl")
                }
            }
            
            Log.d("Xprime", "Total subtitles found: ${subtitles.size}")
            subtitles
        } catch (e: Exception) {
            
            emptyList()
        }
    }

    private suspend fun fetchFromPrimebox(title: String, year: String?, season: String? = null, episode: String? = null): List<ExtractorLink> {
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val url = if (season != null && episode != null) {
                "https://backend.xprime.tv/primebox?name=$encodedTitle&fallback_year=$year&season=$season&episode=$episode"
            } else {
                "https://backend.xprime.tv/primebox?name=$encodedTitle&fallback_year=$year"
            }
            
            Log.d("Xprime", "Primebox URL: $url")
            val response = app.get(url).text
            val json = JSONObject(response)
            
            if (json.optString("status") != "ok") {
                return emptyList()
            }
            
            val streams = json.optJSONObject("streams") ?: return emptyList()
            val links = mutableListOf<ExtractorLink>()
            
            streams.keys().forEach { quality ->
                val streamUrl = streams.optString(quality, "")
                if (streamUrl.isNotEmpty()) {
                    val qualityInt = when (quality) {
                        "1080P" -> Qualities.P1080.value
                        "720P" -> Qualities.P720.value
                        "480P" -> Qualities.P480.value
                        "360P" -> Qualities.P360.value
                        else -> Qualities.Unknown.value
                    }
                    
                    links.add(
                        newExtractorLink(
                            source = "Primebox",
                            name = "Primebox",
                            url = streamUrl,
                            
                            
                            type = if (streamUrl.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ){
                            this.referer = "https://xprime.tv/"
                            this.quality = qualityInt
                        }
                    )
                }
            }
            
            Log.d("Xprime", "Primebox returned ${links.size} links")
            links
        } catch (e: Exception) {
            Log.e("Xprime", "Primebox fetch failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchFromServer(serverName: String, title: String, year: String?, mediaId: String, imdbId: String?, season: String? = null, episode: String? = null): List<ExtractorLink> {
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val url = if (season != null && episode != null) {
                "https://backend.xprime.tv/$serverName?name=$encodedTitle&year=$year&id=$mediaId&imdb=$imdbId&season=$season&episode=$episode"
            } else {
                "https://backend.xprime.tv/$serverName?name=$encodedTitle&year=$year&id=$mediaId&imdb=$imdbId"
            }
            
            Log.d("Xprime", "$serverName URL: $url")
            val response = app.get(url).text
            val json = JSONObject(response)
            
            val streamUrl = json.optString("url", "")
            if (streamUrl.isEmpty()) {
                return emptyList()
            }
            
            val link = newExtractorLink(
    source = serverName.capitalize(),
    name = serverName.capitalize(),
    url = streamUrl,
    type = ExtractorLinkType.M3U8 
) {
    this.referer = "https://api.themoviedb.org/3"
    this.quality = Qualities.Unknown.value
    this.headers = mapOf(
        "Origin" to "https://xprime.tv"
    )
}

            
            Log.d("Xprime", "$serverName returned 1 link")
            listOf(link)
        } catch (e: Exception) {
            Log.e("Xprime", "$serverName fetch failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Xprime", "loadLinks() called with data: $data")

        var cleanData = data
        if (data.contains("$mainUrl/tmdb://")) {
            cleanData = data.replace("$mainUrl/", "")
            Log.w("Xprime", "incorrect url fix in loadLinks: $data to $cleanData")
        }

        val isMovie = cleanData.startsWith("tmdb://movie/")
        val isTvEpisode = cleanData.startsWith("tmdb://tv/") && cleanData.contains("/season/") && cleanData.contains("/episode/")

        if (isMovie) {
            
            val movieId = cleanData.removePrefix("tmdb://movie/")
            Log.d("Xprime", "Processing movie with ID: $movieId")
            
            
            val apiUrl = "$mainUrl/movie/$movieId?api_key=$apiKey&append_to_response=external_ids"
            try {
                val response = withContext(Dispatchers.IO) { app.get(apiUrl).text }
                val json = JSONObject(response)
                val title = json.optString("title", "")
                val releaseDate = json.optString("release_date", "")
                val year = releaseDate.split("-").getOrNull(0)
                val externalIds = json.optJSONObject("external_ids")
                val imdbId = externalIds?.optString("imdb_id", "")?.takeIf { it.isNotEmpty() }
                
                Log.d("Xprime", "Movie: $title (ID: $movieId, Year: $year, IMDB: $imdbId)")
                
                
                val subtitles = fetchSubtitles(movieId)
                subtitles.forEach { subtitle ->
                    subtitleCallback.invoke(subtitle)
                }
                
                
                val servers = getAvailableServers()
                val allLinks = mutableListOf<ExtractorLink>()
                
                
                if (servers.contains("primebox")) {
                    val primeboxLinks = fetchFromPrimebox(title, year)
                    allLinks.addAll(primeboxLinks)
                }
                
                
                val otherServers = servers.filter { it != "primebox" }
                for (server in otherServers) {
                    val links = fetchFromServer(server, title, year, movieId, imdbId)
                    allLinks.addAll(links)
                }
                
                
                allLinks.forEach { link ->
                    callback.invoke(link)
                }
                
                Log.d("Xprime", "Total links found for movie: ${allLinks.size}, subtitles: ${subtitles.size}")
                return allLinks.isNotEmpty()
            } catch (e: Exception) {
                Log.e("Xprime", "Failed to fetch movie data for ID $movieId: ${e.message}")
                return false
            }
        } else if (isTvEpisode) {
            
            val parts = cleanData.removePrefix("tmdb://tv/").split("/")
            if (parts.size < 5) {
                Log.e("Xprime", "Invalid TV episode data format: $cleanData")
                return false
            }

            val tvId = parts[0]
            val seasonNumber = parts[2]
            val episodeNumber = parts[4]

            
            val tvApiUrl = "$mainUrl/tv/$tvId?api_key=$apiKey&append_to_response=external_ids"
            try {
                val tvResponse = withContext(Dispatchers.IO) { app.get(tvApiUrl).text }
                val tvJson = JSONObject(tvResponse)
                val title = tvJson.optString("name", "")
                val firstAirDate = tvJson.optString("first_air_date", "")
                val year = firstAirDate.split("-").getOrNull(0)
                val externalIds = tvJson.optJSONObject("external_ids")
                val imdbId = externalIds?.optString("imdb_id", "")?.takeIf { it.isNotEmpty() }

                Log.d("Xprime", "TV Episode: $title S$seasonNumber E$episodeNumber (ID: $tvId, Year: $year, IMDB: $imdbId)")

                
                val subtitles = fetchSubtitles(tvId, seasonNumber, episodeNumber)
                subtitles.forEach { subtitle ->
                    subtitleCallback.invoke(subtitle)
                }
                
                
                val servers = getAvailableServers()
                val allLinks = mutableListOf<ExtractorLink>()
                
                
                if (servers.contains("primebox")) {
                    val primeboxLinks = fetchFromPrimebox(title, year, seasonNumber, episodeNumber)
                    allLinks.addAll(primeboxLinks)
                }
                
               
                val otherServers = servers.filter { it != "primebox" }
                for (server in otherServers) {
                    val links = fetchFromServer(server, title, year, tvId, imdbId, seasonNumber, episodeNumber)
                    allLinks.addAll(links)
                }
                
               
                allLinks.forEach { link ->
                    callback.invoke(link)
                }
                
                Log.d("Xprime", "Total links found for episode: ${allLinks.size}, subtitles: ${subtitles.size}")
                return allLinks.isNotEmpty()
            } catch (e: Exception) {
                Log.e("Xprime", "Failed to fetch episode data: ${e.message}")
                return false
            }
        } else {
            Log.e("Xprime", "Unknown data format for loadLinks: $cleanData")
            return false
        }
    }}