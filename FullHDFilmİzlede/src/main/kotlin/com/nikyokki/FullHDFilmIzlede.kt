

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class FullHDFilmIzlede : MainAPI() {
    override var mainUrl              = "https://fullhdfilmizlede.net"
    override var name                 = "FullHDFilmİzlede"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/kategori/aksiyon-filmleri-izle/"      to "Aksiyon",
        "${mainUrl}/kategori/belgesel-izle/"   to "Belgesel",
        "${mainUrl}/kategori/bilim-kurgu-filmleri-izle/" to "Bilim Kurgu",
        "${mainUrl}/kategori/macera-filmleri-izle/"  to "Macera"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}sayfa=$page").document
        val home     = document.select("li.movie").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("div.movieName a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div.movieName a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post("${mainUrl}/ara", headers = mapOf("Content-Type" to "application/x-www-form-urlencoded")).document

        return document.select("div.result-item article").mapNotNull { it.toMainPageResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.title a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div.title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("div.movieBar h2")?.text()?.replace(" izle", "")?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("div.moviePoster img")?.attr("src"))
        val description     = document.selectFirst("div.movieDescription h2")?.text()?.trim()
        val year            = document.selectXpath("//span[text()='Yapım Yılı:']//following-sibling::span").text().trim().toIntOrNull()
        val tags            = document.selectXpath("//span[text()='Kategori:']//following-sibling::span").map { it.select("a").text() }
        val rating          = document.selectFirst("span.imdb")?.text()?.trim()?.toRatingInt()
        val duration        = document.selectXpath("//span[text()='Film Süresi:']//following-sibling::span").text().split(" ").first().trim().toIntOrNull()
        val recommendations = document.select("div.popularMovieContainer li").mapNotNull { it.toRecommendationResult() }
        val actors          = document.selectXpath("//span[text()='Oyuncular:']//following-sibling::span").text().split(",")
        val trailer         = document.selectFirst("a.js-modal-btn")?.attr("data-video-id")?.let { "https://www.youtube.com/embed/$it" }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.plot            = description
            this.year            = year
            this.tags            = tags
            this.rating          = rating
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("div.movieName")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.movieImage img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FHI", "data » ${data}")
        val document = app.get(data).document
        val iframe = document.selectFirst("iframe")?.attr("src") ?: ""
        Log.d("FHI", "iframe » ${iframe}")
        val iDocument = app.get(iframe, referer = "${mainUrl}/").document
        val script = iDocument.select("script").find { it.data().contains("sources") }?.data() ?: ""
        val file = script.substringAfter("file:\"").substringBefore("\",")
        Log.d("FHI", "File: $file")
        val tracks = script.substringAfter("\"tracks\": [").substringBefore("],").replace("},", "}")
        tryParseJson<List<FHISource>>("[${tracks}]")
            ?.filter { it.kind == "captions" }?.map {
                subtitleCallback.invoke(
                    SubtitleFile(
                        it.label.toString(),
                        fixUrl(it.file.toString())
                    )
                )
            }
        M3u8Helper.generateM3u8(
            name,
            file,
            "$mainUrl/"
        ).forEach(callback)
        return true
    }

    private data class FHISource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null,
        @JsonProperty("default") val default: Boolean? = null,
    )
}