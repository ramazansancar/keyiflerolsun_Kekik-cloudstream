
package com.kerimmkirac

import com.fasterxml.jackson.annotation.JsonProperty

data class ApiResponse(
    @JsonProperty("response") val response: String?
)

data class SearchResponseData(
    @JsonProperty("result") val result: List<SearchResultItem>?
)

data class SearchResultItem(
    @JsonProperty("used_slug") val slug: String?,
    @JsonProperty("object_name") val title: String?,
    @JsonProperty("object_poster_url") val poster: String?,
    @JsonProperty("used_type") val type: String?
)

data class ContentDetails(
    @JsonProperty("contentItem") val contentItem: MediaItem,
    @JsonProperty("RelatedResults") val relatedData: RelatedData
)

data class MediaItem(
    @JsonProperty("original_title") val originalTitle: String?,
    @JsonProperty("release_year") val releaseYear: Int?,
    @JsonProperty("total_minutes") val totalMinutes: Int?,
    @JsonProperty("poster_url") val posterUrl: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("categories") val categories: String?,
    @JsonProperty("used_slug") val usedSlug: String?,
    @JsonProperty("imdb_point") val imdbPoint: Double?
)

data class RelatedData(
    @JsonProperty("getContentTrailers") val trailers: TrailerData?,
    @JsonProperty("getMovieCastsById") val cast: CastData?,
    @JsonProperty("getMoviePartsById") val movieParts: MoviePartsData?,
    @JsonProperty("getSerieSeasonAndEpisodes") val seriesData: SeriesData?,
    @JsonProperty("getEpisodeSources") val episodeSources: SourcesData?
)

data class TrailerData(
    @JsonProperty("state") val state: Boolean?,
    @JsonProperty("result") val result: List<Trailer>?
)

data class Trailer(
    @JsonProperty("raw_url") val rawUrl: String?
)

data class CastData(
    @JsonProperty("result") val result: List<CastMember>?
)

data class CastMember(
    @JsonProperty("name") val name: String?,
    @JsonProperty("cast_image") val castImage: String?
)

data class MoviePartsData(
    @JsonProperty("state") val state: Boolean?,
    @JsonProperty("result") val result: List<MoviePart>?
)

data class MoviePart(
    @JsonProperty("id") val id: Int?
)

data class VideoSource(
    val sourceContent: String,
    val quality: String
)

data class MediaList(
    @JsonProperty("result") val result: List<MediaItem>
)

data class EpisodeItem(
    @JsonProperty("episode_no") val episodeNo: Int?,
    @JsonProperty("episode_text") val epText: String?,
    @JsonProperty("used_slug") val usedSlug: String?
)

data class SeasonItem(
    @JsonProperty("season_no") val seasonNo: Int?,
    @JsonProperty("episodes") val episodes: List<EpisodeItem>?
)

data class SeriesData(
    @JsonProperty("result") val seasons: List<SeasonItem>?
)

data class SourcesData(
    @JsonProperty("state") val state: Boolean?,
    @JsonProperty("result") val result: List<SourceItem>?
)

data class SourceItem(
    @JsonProperty("source_content") val sourceContent: String?,
    @JsonProperty("quality_name") val qualityName: String?
)