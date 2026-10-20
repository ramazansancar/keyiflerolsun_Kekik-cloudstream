package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchResult(
    @JsonProperty("animes") val animes: List<SearchAnime>? = null
)

data class SearchAnime(
    @JsonProperty("id")     val id: Int?        = null,
    @JsonProperty("name")   val name: String    = "",
    @JsonProperty("url")    val url: String     = "",
    @JsonProperty("poster") val poster: String? = null
)

data class ServerItem(
    @JsonProperty("type")         val type: String?         = null,
    @JsonProperty("label")        val label: String?        = null,
    @JsonProperty("lang")         val lang: String?         = null,
    @JsonProperty("resolveUrl")   val resolveUrl: String?   = null,
    @JsonProperty("streamUrl")    val streamUrl: String?    = null,
    @JsonProperty("metaUrl")      val metaUrl: String?      = null,
    @JsonProperty("qualitiesUrl") val qualitiesUrl: String? = null,
    @JsonProperty("src")          val src: String?          = null,
    @JsonProperty("ytId")         val ytId: String?         = null,
    @JsonProperty("notice")       val notice: String?       = null,
    @JsonProperty("embedId")      val embedId: Any?         = null
)

data class ResolveResponse(
    @JsonProperty("id") val id: String? = null
)

data class TauResponse(
    @JsonProperty("urls") val urls: List<TauUrl>? = null
)

data class TauUrl(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("url")   val url: String    = ""
)

data class MetaResponse(
    @JsonProperty("hls")  val hls: String?        = null,
    @JsonProperty("subs") val subs: List<MetaSub>? = null
)

data class MetaSub(
    @JsonProperty("label")   val label: String?   = null,
    @JsonProperty("lang")    val lang: String?    = null,
    @JsonProperty("default") val default: Boolean? = null,
    @JsonProperty("url")     val url: String?     = null
)

data class QualitiesResponse(
    @JsonProperty("qualities") val qualities: List<QualityItem>? = null
)

data class QualityItem(
    @JsonProperty("q")     val q: String?     = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("url")   val url: String?   = null
)

data class DzenStream(
    @JsonProperty("url")  val url: String?  = null,
    @JsonProperty("type") val type: String? = null
)

data class OkRuVideo(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("url")  val url: String?  = null
)
