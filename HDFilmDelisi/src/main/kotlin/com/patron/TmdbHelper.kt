//Eklenti kraptor reposundan çekilip düzenlenmiştir.
package com.patron

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.app
import com.lagradost.api.Log
import me.xdrop.fuzzywuzzy.FuzzySearch

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSearchResult(
    @JsonProperty("id")            val id: Int?,
    @JsonProperty("name")          val name: String?,
    @JsonProperty("title")         val title: String?,
    @JsonProperty("original_name") val original_name: String?,
    @JsonProperty("original_title")val original_title: String?,
    @JsonProperty("first_air_date")val first_air_date: String?,
    @JsonProperty("release_date")  val release_date: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSearchBase(
    @JsonProperty("results") val results: List<TmdbSearchResult>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbEpisode(
    @JsonProperty("id")             val id: Int?,
    @JsonProperty("episode_number") val episode_number: Int?,
    @JsonProperty("season_number")  val season_number: Int?,
    @JsonProperty("name")           val name: String?,
    @JsonProperty("overview")       val overview: String?,
    @JsonProperty("air_date")       val air_date: String?,
    @JsonProperty("still_path")     val still_path: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSeasonResponse(
    @JsonProperty("episodes")       val episodes: List<TmdbEpisode>?,
    @JsonProperty("season_number")  val season_number: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbDetailsExtended(
    val tmdbId: Int?,
    val imdbId: String?,
    val poster: String?,
    val plot: String?,
    val year: Int?,
    val duration: Int?,
    val tags: List<String>?,
    val actors: List<ActorData>?,
    val recommendations: List<Any>?,
    val trailerId: String?,
    val syncMap: Map<String, String>?,
    val status: ShowStatus?,
    val isTv: Boolean,
    val finalPoster: String?,
    val finalPlot: String?,
    val finalYear: Int?,
    val finalDuration: Int?
)

object TmdbHelper {

    private const val API_KEY     = "500330721680edb6d5f7f12ba7cd9023"
    private const val TMDB_BASE   = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE  = "https://image.tmdb.org/t/p/w500"

    val tmdbNull = TmdbDetailsExtended(
        null, null, null, null, null, null, null,
        null, null, null, null, null, false,
        null, null, null, null
    )

    suspend fun getTmdbDataById(
        tmdbId: Int?,
        imdbId: String?,
        tvTurleri: TvType,
        sitePlot: String? = null,
        localSeasonCount: Int? = null
    ): TmdbDetailsExtended? {
        val resolvedId: Int? = when {
            tmdbId != null -> tmdbId
            !imdbId.isNullOrBlank() -> resolveIdFromImdb(imdbId, tvTurleri)
            else -> null
        }
        resolvedId ?: return null
        val yol = if (tvTurleri == TvType.Movie || tvTurleri == TvType.AnimeMovie) "movie" else "tv"
        return fetchDetails(resolvedId, yol, sitePlot, localSeasonCount)
    }

    suspend fun getTmdbDataByName(
        isim: String,
        tvTurleri: TvType,
        filtreYil: Int? = null,
        sitePlot: String? = null,
        localSeasonCount: Int? = null,
        ikinciIsim: String? = null
    ): TmdbDetailsExtended? {
        val temizlik  = Regex("(?i)\\s*(türkçe\\s+dublaj|türkçe\\s+altyazı(?:lı)?|izle)\\s*")
        val temizIsim = isim.replace(temizlik, "").trim()
        val temizIkinci = ikinciIsim?.replace(temizlik, "")?.trim()
        val yol = if (tvTurleri == TvType.Movie || tvTurleri == TvType.AnimeMovie) "movie" else "tv"
        val yilParam = when {
            filtreYil == null -> ""
            yol == "movie"   -> "&primary_release_year=$filtreYil"
            else              -> "&first_air_date_year=$filtreYil"
        }
        val aramaUrl = "$TMDB_BASE/search/$yol?api_key=$API_KEY&query=${temizIsim.replace(" ", "+")}&language=tr-TR$yilParam"
        Log.d("TmdbHelper", "Arama: $aramaUrl")

        return try {
            val raw     = app.get(aramaUrl).text
            val yanit   = parseJson<TmdbSearchBase>(raw)
            val sonuclar = yanit.results ?: return null

            val puan = sonuclar.mapNotNull { s ->
                val ad = s.name ?: s.title ?: return@mapNotNull null
                val skor1 = FuzzySearch.ratio(temizIsim.lowercase(), ad.lowercase())
                val skor2 = temizIkinci?.let { FuzzySearch.ratio(it.lowercase(), ad.lowercase()) } ?: 0
                Triple(s, ad, maxOf(skor1, skor2))
            }

            val enIyi = puan.maxByOrNull { it.third } ?: return null
            val secilen = enIyi.first
            secilen.id ?: return null
            fetchDetails(secilen.id, yol, sitePlot, localSeasonCount)
        } catch (e: Exception) {
            Log.d("TmdbHelper", "isim araması hatası: ${e.message}")
            null
        }
    }

    private suspend fun resolveIdFromImdb(imdbId: String, tvTurleri: TvType): Int? {
        return try {
            val url = "$TMDB_BASE/find/$imdbId?api_key=$API_KEY&external_source=imdb_id&language=tr-TR"
            val json = parseJson<Map<String, Any>>(app.get(url).text)
            val key  = if (tvTurleri == TvType.Movie || tvTurleri == TvType.AnimeMovie) "movie_results" else "tv_results"
            @Suppress("UNCHECKED_CAST")
            val arr  = json[key] as? List<Map<String, Any>>
            (arr?.firstOrNull()?.get("id") as? Double)?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchDetails(
        tmdbId: Int,
        yol: String,
        sitePlot: String?,
        localSeasonCount: Int?
    ): TmdbDetailsExtended? {
        return try {
            val detailUrl = "$TMDB_BASE/$yol/$tmdbId?api_key=$API_KEY&language=tr-TR&append_to_response=credits,videos,external_ids,recommendations"
            val raw = app.get(detailUrl).text
            val json = parseJson<Map<String, Any>>(raw)

            val poster = (json["poster_path"] as? String)?.let { "$IMAGE_BASE$it" }
            val plot   = (json["overview"] as? String)?.takeIf { it.isNotBlank() } ?: sitePlot
            val year   = (json["release_date"] as? String ?: json["first_air_date"] as? String)
                ?.split("-")?.firstOrNull()?.toIntOrNull()
            val duration = (json["runtime"] as? Double)?.toInt()
                ?: (json["episode_run_time"] as? List<*>)?.firstOrNull()?.let { (it as? Double)?.toInt() }
            val tags   = (json["genres"] as? List<*>)?.mapNotNull {
                (it as? Map<*, *>)?.get("name") as? String
            }
            val trailerId = (json["videos"] as? Map<*, *>)?.let { videos ->
                @Suppress("UNCHECKED_CAST")
                (videos["results"] as? List<Map<String, Any>>)
                    ?.firstOrNull { it["type"] == "Trailer" && it["site"] == "YouTube" }
                    ?.get("key") as? String
            }
            val imdbId = (json["external_ids"] as? Map<*, *>)?.get("imdb_id") as? String
            val tmdbFinal = json["id"]?.let { (it as? Double)?.toInt() }

            val actors: List<ActorData> = try {
                @Suppress("UNCHECKED_CAST")
                val cast = (json["credits"] as? Map<*, *>)?.get("cast") as? List<Map<String, Any>>
                cast?.take(10)?.mapNotNull { c ->
                    val name  = c["name"] as? String ?: return@mapNotNull null
                    val imgPath = c["profile_path"] as? String
                    ActorData(
                        actor = com.lagradost.cloudstream3.Actor(
                            name = name,
                            image = imgPath?.let { "$IMAGE_BASE$it" }
                        ),
                        roleString = c["character"] as? String
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            val status: ShowStatus? = when (json["status"] as? String) {
                "Ended", "Canceled" -> ShowStatus.Completed
                "Returning Series"  -> ShowStatus.Ongoing
                else                -> null
            }

            TmdbDetailsExtended(
                tmdbId          = tmdbFinal,
                imdbId          = imdbId,
                poster          = poster,
                plot            = plot,
                year            = year,
                duration        = duration,
                tags            = tags,
                actors          = actors,
                recommendations = emptyList(),
                trailerId       = trailerId,
                syncMap         = imdbId?.let { mapOf("imdb" to it, "tmdb" to (tmdbFinal?.toString() ?: "")) },
                status          = status,
                isTv            = yol == "tv",
                finalPoster     = poster,
                finalPlot       = plot,
                finalYear       = year,
                finalDuration   = duration
            )
        } catch (e: Exception) {
            Log.d("TmdbHelper", "fetchDetails hatası: ${e.message}")
            null
        }
    }

    suspend fun getTmdbSeason(
        tmdbId: Int?,
        sNo: Int?,
        totalSeasons: Int? = null
    ): TmdbSeasonResponse? {
        tmdbId ?: return null
        sNo    ?: return null
        return try {
            val url = "$TMDB_BASE/tv/$tmdbId/season/$sNo?api_key=$API_KEY&language=tr-TR"
            parseJson<TmdbSeasonResponse>(app.get(url).text)
        } catch (e: Exception) {
            null
        }
    }
}
