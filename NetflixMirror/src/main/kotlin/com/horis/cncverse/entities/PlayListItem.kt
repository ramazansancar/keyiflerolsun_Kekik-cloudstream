package com.horis.cncverse.entities

data class PlayListItem(
    val image: String,
    val sources: List<Source>,
    val tracks: List<Tracks>?,
    val title: String
)
