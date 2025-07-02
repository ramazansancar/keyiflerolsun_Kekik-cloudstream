package com.kerimmkirac

import com.fasterxml.jackson.annotation.JsonProperty


data class MovieResponse(
    @JsonProperty("results") val results: List<XMovie>,
)

data class TvResponse(
    @JsonProperty("results") val results: List<XTvShow>,
)

data class SearchMultiResponse(
    @JsonProperty("results") val results: List<SearchResult>,
)


open class SearchResult(
    @JsonProperty("id") val id: Int,
    @JsonProperty("media_type") val mediaType: String? = null,
)


data class XMovie(
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("genre_ids") val genreIds: List<Int>? = null,
    @JsonProperty("genres") val genres: List<Genre>? = null,
    @JsonProperty("id") val id: Int,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("vote_average") val vote: Double? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("credits") val credits: Credits? = null,
    @JsonProperty("recommendations") val recommendations: MovieRecommendations? = null,
    @JsonProperty("imdb_id") val imdb: String? = null,
) : SearchResult(id, mediaType)


data class XTvShow(
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("genre_ids") val genreIds: List<Int>? = null,
    @JsonProperty("genres") val genres: List<Genre>? = null,
    @JsonProperty("id") val id: Int,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("vote_average") val vote: Double? = null,
    @JsonProperty("number_of_seasons") val numberOfSeasons: Int? = null,
    @JsonProperty("number_of_episodes") val numberOfEpisodes: Int? = null,
    @JsonProperty("seasons") val seasons: List<SeasonInfo>? = null,
    @JsonProperty("credits") val credits: Credits? = null,
    @JsonProperty("recommendations") val recommendations: TvRecommendations? = null,
    @JsonProperty("imdb_id") val imdb: String? = null,
) : SearchResult(id, mediaType)


data class SeasonInfo(
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("episode_count") val episodeCount: Int? = null,
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("season_number") val seasonNumber: Int,
)

data class Season(
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("episodes") val episodes: List<EpisodeInfo>? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("id") val id: Int,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("season_number") val seasonNumber: Int,
)

data class EpisodeInfo(
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("episode_number") val episodeNumber: Int,
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("production_code") val productionCode: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int,
    @JsonProperty("show_id") val showId: Int? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("vote_count") val voteCount: Int? = null,
)


data class MovieRecommendations(
    @JsonProperty("results") val results: List<XMovie>
)

data class TvRecommendations(
    @JsonProperty("results") val results: List<XTvShow>
)


data class Credits(
    @JsonProperty("cast") val cast: List<Cast>,
)

data class Cast(
    @JsonProperty("name") val name: String,
    @JsonProperty("profile_path") val profilePath: String? = null,
)


data class Genres(
    @JsonProperty("genres") val genres: List<Genre>?
)

data class Genre(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
)


data class Stream(
    @JsonProperty("available_qualities") val qualities: List<String>,
    @JsonProperty("status") val status: String,
    @JsonProperty("has_subtitles") val hasSubtitles: Boolean,
    @JsonProperty("subtitles") val subtitles: List<PrimeSubs>
)

data class PrimeSubs(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
)


data class Trailers(
    @JsonProperty("id") val id: Int,
    @JsonProperty("results") val results: List<Trailer>
)

data class Trailer(
    @JsonProperty("key") val key: String,
    @JsonProperty("site") val site: String,
)


data class Servers(
    @JsonProperty("servers") val servers: List<Server>? = null
)

data class Server(
    @JsonProperty("name") val name: String,
    @JsonProperty("status") val status: String,
)


data class Subtitle(
    @JsonProperty("url") val url: String,
    @JsonProperty("display") val display: String,
)