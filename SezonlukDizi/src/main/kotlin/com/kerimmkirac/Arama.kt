package com.kerimmkirac

data class Arama(
    val status: String,
    val results: Results
)

data class Results(
    val kategoriler: Cevap? = null,
    val filmler: Cevap,
    val diziler: Cevap,
    val sanatcilar: Cevap,
)

data class Cevap(
    val name: String,
    val results: List<ResultsIcerik>
)

data class ResultsIcerik(
    val title: String?,
    val description: String?,
    val url: String?,
    val image: String?,
    val did: Int? = null,
    val imdb: Double? = null
)