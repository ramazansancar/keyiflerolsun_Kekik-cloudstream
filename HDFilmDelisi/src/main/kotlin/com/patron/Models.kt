package com.patron

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class HDFilmItem(
    @JsonProperty("baslik")    val baslik: String?,
    @JsonProperty("slug")      val slug: String?,
    @JsonProperty("afis")      val afis: String?,
    @JsonProperty("yayinYili") val yayinYili: Int?,
    @JsonProperty("imdbPuani") val imdbPuani: Double?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CategoryResponse(
    @JsonProperty("films") val films: List<HDFilmItem>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HDFilmSearchResponse(
    @JsonProperty("results") val results: List<HDFilmItem>?
)

data class HdFCResults(
    val results: List<String> = emptyList()
)

data class AltyaziAPI(
    val subtitles: List<TurkceAltyazi>,
    val cacheMaxAge: Long,
    val staleRevalidate: Long,
    val staleError: Long
)

data class TurkceAltyazi(
    val url: String,
    val id: String,
    val lang: String,
    val episode: String
)

data class Subtitle(
    val id: String,
    val url: String,
    val subEncoding: String,
    val lang: String,
    val m: String,
    val g: String
)

data class SubtitlesAPI(
    val subtitles: List<Subtitle>,
    val cacheMaxAge: Long
)

data class WyZIESUB(
    val id: String,
    val url: String,
    val flagUrl: String,
    val format: String,
    val display: String,
    val language: String,
    val media: String,
    val isHearingImpaired: Boolean
)

data class WyZIEResponse(
    val subtitles: List<WyZIESUB>? = null
)
