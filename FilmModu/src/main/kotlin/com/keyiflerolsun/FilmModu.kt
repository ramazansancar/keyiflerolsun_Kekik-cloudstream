// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FilmModu : MainAPI() {
    override var mainUrl              = "https://www.filmmodu.life"
    override var name                 = "FilmModu"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/hd-film-kategori/4k-film-izle"          to "4K",
        "${mainUrl}/hd-film-kategori/aile-filmleri"         to "Aile",
        "${mainUrl}/hd-film-kategori/aksiyon"               to "Aksiyon",
        "${mainUrl}/hd-film-kategori/animasyon"             to "Animasyon",
        "${mainUrl}/hd-film-kategori/belgeseller"           to "Belgesel",
        "${mainUrl}/hd-film-kategori/bilim-kurgu-filmleri"  to "Bilim-Kurgu",

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}?page=${page}").document
        val home     = document.select("div.movie").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("picture img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/film-ara?term=${query}").document

        return document.select("div.movie").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val orgTitle    = document.selectFirst("div.titles h1")?.text()?.trim() ?: return null
        val altTitle    = document.selectFirst("div.titles h2")?.text()?.trim() ?: ""
        val title       = if (altTitle.isNotEmpty()) "$orgTitle - $altTitle" else orgTitle
        val poster      = fixUrlNull(document.selectFirst("img.img-responsive")?.attr("src"))
        val description = document.selectFirst("p[itemprop='description']")?.text()?.trim()
        val year        = document.selectFirst("span[itemprop='dateCreated']")?.text()?.trim()?.toIntOrNull()
        val tags        = document.select("div.description a[href*='-kategori/']").map { it.text() }
        val rating  = document.selectFirst("div.description p")?.ownText()?.split(" ")?.last()?.trim()
        val actors      = document.select("div.description a[href*='-oyuncu-']").map { Actor(it.selectFirst("span")!!.text()) }
        val trailer     = document.selectFirst("div.container iframe")?.attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            this.score = Score.from10(rating)
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FLMMD", "data » $data")
        val document = app.get(data).document

        document.select("div.alternates a").forEach {
            val altLink = fixUrlNull(it.attr("href")) ?: return@forEach
            val altName = it.text()
            if (altName == "Fragman") return@forEach

            val altReq  = app.get(altLink)
            val vidId   = Regex("""var videoId = '(.*)'""").find(altReq.text)?.groupValues?.get(1) ?: return@forEach
            val vidType = Regex("""var videoType = '(.*)'""").find(altReq.text)?.groupValues?.get(1) ?: return@forEach

            val vidReq = app.get("${mainUrl}/get-source?movie_id=${vidId}&type=${vidType}").parsedSafe<GetSource>() ?: return@forEach

            if (vidReq.subtitle != null) {
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = "Türkçe",
                        url  = fixUrl(vidReq.subtitle)
                    )
                )
            }

            vidReq.sources?.forEach { source ->
                callback.invoke(
                    newExtractorLink(
                        source  = "${this.name} - $altName",
                        name    = "${this.name} - $altName",
                        url     = fixUrl(source.src),
                        type    = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "${mainUrl}/"
                        this.quality = getQualityFromName(source.label)
                    }
                )
            }
        }

        return true
    }
}
