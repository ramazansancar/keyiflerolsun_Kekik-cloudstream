// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class IzleAI : MainAPI() {
    override var mainUrl              = "https://selcukflix.com"
    override var name                 = "720PizleAI"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie)

    // ! CloudFlare bypass
    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/library/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 250L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 250L  // ? 0.05 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/film-izle"                 to "Yeni Eklenen Filmler",
        "${mainUrl}/film-kategori/aile"        to "Aile",
        "${mainUrl}/film-kategori/aksiyon"     to "Aksiyon",
        "${mainUrl}/film-kategori/animasyon"   to "Animasyon",
        "${mainUrl}/film-kategori/belgesel"    to "Belgesel",
        "${mainUrl}/film-kategori/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/film-kategori/dram"        to "Dram",
        "${mainUrl}/film-kategori/fantastik"   to "Fantastik",
        "${mainUrl}/film-kategori/film-noir"   to "Film-Noir",
        "${mainUrl}/film-kategori/gerilim"     to "Gerilim",
        "${mainUrl}/film-kategori/gizem"       to "Gizem",
        "${mainUrl}/film-kategori/kisa"        to "Kısa Film",
        "${mainUrl}/film-kategori/komedi"      to "Komedi",
        "${mainUrl}/film-kategori/korku"       to "Korku",
        "${mainUrl}/film-kategori/macera"      to "Macera",
        "${mainUrl}/film-kategori/muzik"       to "Müzik",
        "${mainUrl}/film-kategori/romantik"    to "Romantik",
        "${mainUrl}/film-kategori/savas"       to "Savaş",
        "${mainUrl}/film-kategori/spor"        to "Spor",
        "${mainUrl}/film-kategori/suc"         to "Suç",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = if (request.data.contains("/film-izle")) {
		document.select("div.grid-cols-2 a").mapNotNull { it.toSearchResult() }
        } else {
		     document.select("div.grid-cols-2 a").mapNotNull { it.toSearchResult() }
		}
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = fixUrlNull(this.selectFirst("img")?.attr("alt"))
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    private fun SearchItem.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(
            title ?: return null,
            "${mainUrl}/${slug}",
            TvType.Movie,
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val mainReq  = app.get(mainUrl)
        val mainPage = mainReq.document
        val cKey     = mainPage.selectFirst("input[name='cKey']")?.attr("value") ?: return emptyList()
        val cValue   = mainPage.selectFirst("input[name='cValue']")?.attr("value") ?: return emptyList()

        val veriler   = mutableListOf<SearchResponse>()

        val searchReq = app.post(
            "${mainUrl}/bg/searchcontent",
            data = mapOf(
                "cKey"       to cKey,
                "cValue"     to cValue,
                "searchterm" to query
            ),
            headers = mapOf(
                "Accept"           to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            ),
            referer = "${mainUrl}/",
            cookies = mapOf(
                "showAllDaFull"   to "true",
                "PHPSESSID"       to mainReq.cookies["PHPSESSID"].toString(),
            )
        ).parsedSafe<SearchResult>()

        if (searchReq?.data?.state != true) {
            throw ErrorLoadingException("Invalid Json response")
        }

        searchReq.data.result?.forEach { searchItem ->
            val title = searchItem.title ?: return@forEach
            if (title.endsWith("Serisi") || title.endsWith("Series")) {
                return@forEach
            }

            veriler.add(searchItem.toSearchResponse() ?: return@forEach)
        }

        return veriler
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("div.gap-3.pt-5 h2")?.text() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.col-span-2 img")?.attr("data-src"))
        val year        = document.selectFirst("a[href*='/yil/']")?.text()?.toIntOrNull()
        val description = document.selectFirst("div.mv-det-p")?.text()?.trim() ?: document.selectFirst("div.w-full div.text-base")?.text()?.trim()
        val tags        = document.select("[href*='film-kategori']").map { it.text() }
        val rating      = document.selectFirst("a[href*='imdb.com'] span.font-bold")?.text()?.trim().toRatingInt()
        val duration    = document.selectXpath("//span[contains(text(), ' dk.')]").text().trim().split(" ").first().toIntOrNull()
        val trailer     = document.selectFirst("iframe[data-src*='youtube.com/embed/']")?.attr("data-src")
        val actors      = document.select("div.flex.overflow-auto [href*='oyuncu']").map {
            Actor(it.selectFirst("span span")!!.text(), it.selectFirst("img")?.attr("data-srcset")?.split(" ")?.first())
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.rating    = rating
            this.duration  = duration
            addTrailer(trailer)
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("IAI", "data » $data")
        val document = app.get(data).document
        val iframe   = fixUrlNull(document.selectFirst("div.Player iframe")?.attr("src")) ?: return false
        Log.d("IAI", "iframe » $iframe")

        loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)

        return true
    }
}