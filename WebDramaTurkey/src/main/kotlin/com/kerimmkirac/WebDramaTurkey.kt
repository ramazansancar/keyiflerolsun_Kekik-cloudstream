package com.kerimmkirac

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import org.jsoup.nodes.Element

class WebDramaTurkey : MainAPI() {
    override var mainUrl = "https://webdramaturkey.net"
    override var name = "WebDramaTurkey"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler" to "Diziler",
        "${mainUrl}/filmler" to "Filmler",
        "${mainUrl}/programlar" to "Programlar",
        "${mainUrl}/animeler" to "Animeler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/${page}").document
        val home = document.select("div.col.sonyuklemeler").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("div.list-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val style = this.selectFirst("div.media-episode")?.attr("style") ?: return null
        val posterUrl = extractBackgroundImageUrl(style)?.let { fixUrlNull(it) } ?: return null

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun extractBackgroundImageUrl(style: String): String? {
        val regex = Regex("""url\(["']?(.*?)["']?\)""")
        return regex.find(style)?.groupValues?.get(1)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/?s=${encodedQuery}").document

        return document.select("div.list-movie").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("a.list-title")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a.list-title")?.attr("href")) ?: return null
        val style = this.selectFirst("div.media-cover")?.attr("style") ?: return null
        val posterUrl = extractBackgroundImageUrl(style)?.let { fixUrlNull(it) } ?: return null

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val posterStyle = document.selectFirst("div.media-cover")?.attr("style") ?: return null
        val poster = extractBackgroundImageUrl(posterStyle)?.let { fixUrlNull(it) } ?: return null
        val description = document.selectFirst("div.detail-attr div.text-content")?.text()?.trim()
        val year = document.select("div.featured-attr").firstOrNull {
            it.selectFirst("div.attr")?.text()?.contains("Yayın yılı") == true
        }?.selectFirst("div.text")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.categories a").map { it.text().trim() }
        val episodes = document.select("div.episodes a").mapNotNull {
            val episodeNumber = it.selectFirst("div.episode")?.text()?.trim()
            val episodeUrl = fixUrlNull(it.attr("href"))
            if (episodeNumber != null && episodeUrl != null) {
                Episode(episodeNumber, episodeUrl)
            } else {
                null
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.episodes = episodes
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("TFD", "data » ${data}")
        val document = app.get(data).document
        val iframe = document.selectFirst("iframe")?.attr("src") ?: ""
        Log.d("TFD", iframe)

        return true
    }
}