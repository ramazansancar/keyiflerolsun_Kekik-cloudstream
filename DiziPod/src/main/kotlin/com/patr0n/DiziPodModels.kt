package com.patr0n

import com.fasterxml.jackson.annotation.JsonProperty

data class PlayerResponse(
    @JsonProperty("success") val success: Boolean = false,
    @JsonProperty("data")    val data   : String   = ""
)

data class SearchResult(
    @JsonProperty("title") val title : String = "",
    @JsonProperty("url")   val url   : String = "",
    @JsonProperty("img")   val img   : String = ""
)
