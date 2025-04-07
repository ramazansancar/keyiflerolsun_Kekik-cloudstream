package com.nikyokki

import com.fasterxml.jackson.annotation.JsonProperty


data class HDSearchResponse(
    @JsonProperty("result") val result: List<Movie>,
    @JsonProperty("query") val query: String?
)

data class Movie(
    @JsonProperty("postid") val postid: String?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("access") val access: String?,
    @JsonProperty("poster") val poster: String?,
    @JsonProperty("cover") val cover: String?,
    @JsonProperty("imdb") val imdb: String?,
    @JsonProperty("original_title") val originalTitle: String?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("second_title") val secondTitle: String?,
    @JsonProperty("slug") val slug: String?,
    @JsonProperty("year") val year: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("akatitle") val akatitle: String?,
    @JsonProperty("slug_prefix") val slugPrefix: String?
)

data class Vidload(
    @JsonProperty("status") val status: String?,
    @JsonProperty("image") val image: String?,
    @JsonProperty("file") val file: String?,
    @JsonProperty("hash") val hash: String?
)


