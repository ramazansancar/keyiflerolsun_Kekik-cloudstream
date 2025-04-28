package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty

data class Root(
    val status: String,
    @JsonProperty("message")
    val serverTime: String,
    val query: Query,
    @JsonProperty("embed_url")
    val embedLink: String,
    @JsonProperty("download_url")
    val downloadLink: String,
    @JsonProperty("request_link")
    val requestLink: String,
    val title: String,
    val poster: String,
    val sources: List<Source>,
    val tracks: List<Track>,
)

data class Query(
    val source: String,
    val id: String,
    val download: String,
)

data class Source(
    val file: String,
    val type: String,
    val label: String,
    val default: Boolean,
)

data class Track(
    val file: String,
    val label: String,
    val default: Boolean?,
)
