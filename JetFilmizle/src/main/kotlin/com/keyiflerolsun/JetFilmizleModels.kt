package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty

data class Source(
    @JsonProperty("file") val file: String,
    @JsonProperty("label") val label: String,
    @JsonProperty("type") val type: String,
)