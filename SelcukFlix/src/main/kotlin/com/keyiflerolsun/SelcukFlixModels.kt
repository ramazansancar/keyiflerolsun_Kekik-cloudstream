package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchData(
    @JsonProperty("state")  val state  : Boolean?         = null,
    @JsonProperty("result") val result : List<SearchItem> = emptyList(),
    @JsonProperty("message") val message : String?        = null,
    @JsonProperty("html")   val html   : String?          = null
)

data class SearchItem(
    @JsonProperty("used_slug")        val slug   : String? = null,
    @JsonProperty("object_name")      val title  : String? = null,
    @JsonProperty("object_poster_url") val poster : String? = null,
    @JsonProperty("imdb_point")       val puan   : String? = null
)

data class ContentDetails(
    @JsonProperty("contentItem")    val contentItem : MediaItem,
    @JsonProperty("RelatedResults") val relatedData : RelatedData
)

data class MediaItem(
    @JsonProperty("original_title") val originalTitle : String? = null,
    @JsonProperty("release_year")   val releaseYear   : Int?    = null,
    @JsonProperty("total_minutes")  val totalMinutes  : Int?    = null,
    @JsonProperty("poster_url")     val posterUrl     : String? = null,
    @JsonProperty("description")    val description   : String? = null,
    @JsonProperty("categories")     val categories    : String? = null,
    @JsonProperty("used_slug")      val usedSlug      : String? = null,
    @JsonProperty("imdb_point")     val imdbPoint     : Double? = null
)

data class RelatedData(
    @JsonProperty("getContentTrailers")       val trailers       : TrailerData?     = null,
    @JsonProperty("getMovieCastsById")        val cast           : CastData?        = null,
    @JsonProperty("getMoviePartsById")        val movieParts     : MoviePartsData?  = null,
    @JsonProperty("getSerieSeasonAndEpisodes") val seriesData    : SeriesData?      = null,
    @JsonProperty("getEpisodeSources")        val episodeSources : SourcesData?     = null
)

data class SeriesData(
    @JsonProperty("result") val seasons : List<SeasonItem>? = null
)

data class SeasonItem(
    @JsonProperty("season_no") val seasonNo : Int?             = null,
    @JsonProperty("episodes")  val episodes : List<EpisodeItem>? = null
)

data class EpisodeItem(
    @JsonProperty("episode_no")   val episodeNo : Int?    = null,
    @JsonProperty("episode_text") val epText    : String? = null,
    @JsonProperty("used_slug")    val usedSlug  : String? = null
)

data class SourcesData(
    @JsonProperty("state")  val state  : Boolean?         = null,
    @JsonProperty("result") val result : List<SourceItem>? = null
)

data class SourceItem(
    @JsonProperty("source_content") val sourceContent : String? = null,
    @JsonProperty("quality_name")   val qualityName   : String? = null
)

data class VideoSource(
    val sourceContent : String,
    val quality       : String
)

data class TrailerData(
    @JsonProperty("result") val result : List<Trailer>? = null
)

data class Trailer(
    @JsonProperty("trailer_url") val trailerUrl : String? = null
)

data class CastData(
    @JsonProperty("result") val result : List<CastMember>? = null
)

data class CastMember(
    @JsonProperty("actor_name") val actorName : String? = null
)

data class MoviePartsData(
    @JsonProperty("result") val result : List<MoviePart>? = null
)

data class MoviePart(
    @JsonProperty("original_title") val originalTitle : String? = null,
    @JsonProperty("used_slug")      val usedSlug      : String? = null
)

data class ApiResponse(
    @JsonProperty("response") val response : String? = null
)
