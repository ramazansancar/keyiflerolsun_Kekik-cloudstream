// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer


class FilmMakinesi : MainAPI() {
    override var mainUrl              = "https://filmmakinesi.to"
    override var name                 = "FilmMakinesi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    // ! CloudFlare bypass
    override var sequentialMainPage            = true
    override var sequentialMainPageDelay       = 50L
    override var sequentialMainPageScrollDelay = 50L

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler-1/sayfa/"                                    to "Son Filmler",
        "${mainUrl}/film-izle/olmeden-izlenmesi-gerekenler-fm1/sayfa/"   to "Ölmeden İzle",
        "${mainUrl}/tur/aksiyon-fm1/film/sayfa/"                         to "Aksiyon",
        "${mainUrl}/tur/bilim-kurgu-fm2/film/sayfa/"                     to "Bilim Kurgu",
        "${mainUrl}/tur/macera-fm1/film/sayfa/"                          to "Macera",
        "${mainUrl}/tur/komedi-fm1/film/sayfa/"                          to "Komedi",
        "${mainUrl}/tur/romantik-fm1/film/sayfa/"                        to "Romantik",
        "${mainUrl}/tur/belgesel/film/sayfa/"                            to "Belgesel",
        "${mainUrl}/tur/fantastik-fm1/film/sayfa/"                       to "Fantastik",
        "${mainUrl}/tur/polisiye/film/sayfa/"                            to "Polisiye Suç",
        "${mainUrl}/tur/korku-fm1/film/sayfa/"                           to "Korku"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // URL formatı: /filmler-1/sayfa/2/  (sayfa=1 için trailing slash ile base URL)
        val baseUrl = request.data.removeSuffix("/")
        val url = if (page <= 1) {
            // İlk sayfa: /filmler-1/  (sayfa/ suffix'i olmadan)
            baseUrl.substringBeforeLast("/sayfa")
        } else {
            "$baseUrl/$page/"
        }

        Log.d("FLMM", "getMainPage url=$url page=$page")

        val document = app.get(url, headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer"    to "$mainUrl/"
        )).document

        val home = document.select("div.film-list a.item").mapNotNull { it.toSearchResult() }
        Log.d("FLMM", "Toplam film: ${home.size}")
        return newHomePageResponse(request.name, home)
    }

    // a.item elementi için toSearchResult
    private fun Element.toSearchResult(): SearchResponse? {
        val href  = fixUrlNull(this.attr("href")) ?: return null
        // data-title önce, yoksa img alt'tan al
        val title = this.attr("data-title").takeIf { it.isNotBlank() }
            ?: this.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() }
            ?: return null

        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
        )

        Log.d("FLMM", "Film: $title | $href")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val aTag      = this.selectFirst("a.item") ?: this.selectFirst("a") ?: return null
        val href      = fixUrlNull(aTag.attr("href")) ?: return null
        val title     = aTag.attr("data-title").takeIf { it.isNotBlank() }
            ?: aTag.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() }
            ?: aTag.text().trim().takeIf { it.isNotBlank() }
            ?: return null
        val posterUrl = fixUrlNull(aTag.selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get(
            "$mainUrl/arama/?s=$query",
            headers = mapOf("User-Agent" to USER_AGENT)
        ).document
        return document.select("div.film-list a.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer"    to "$mainUrl/"
        )).document

        val title       = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.select("div.info-description p").last()?.text()?.trim()
        val tags        = document.selectFirst("dt:contains(Tür:) + dd")?.text()?.split(", ")
        val year        = document.selectFirst("dt:contains(Yapım Yılı:) + dd")?.text()?.trim()?.toIntOrNull()

        val durationElement = document.select("dt:contains(Film Süresi:) + dd time").attr("datetime")
        val duration = if (durationElement.startsWith("PT") && durationElement.endsWith("M")) {
            durationElement.drop(2).dropLast(1).toIntOrNull() ?: 0
        } else 0

        val recommendations = document.select("div.film-list div.item-relative").mapNotNull { it.toRecommendResult() }
        val actors = document.selectFirst("dt:contains(Oyuncular:) + dd")?.text()?.split(", ")?.map {
            Actor(it.trim())
        }

        // Fragman: YouTube embed URL'den video ID'sini çek
        val trailer = document.selectFirst("div.left a.trailer-button")
            ?.attr("data-video_url")
            ?.substringAfter("embed/", "")
            ?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("FLMM", "loadLinks » $data")

        val document = app.get(data, headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer"    to "$mainUrl/"
        )).document

        // 1. Birincil kaynak: iframe[data-src]
        val iframeSrc = document.selectFirst("iframe[data-src]")?.attr("data-src")?.trim() ?: ""

        // 2. Ek video kaynaklar: .video-parts butonlarındaki data-video_url (YouTube hariç)
        val videoParts = document.select(".video-parts a[data-video_url]")
            .map { it.attr("data-video_url").trim() }
            .filter { it.isNotBlank() && !it.contains("youtube.com") && !it.contains("youtu.be") }

        // Tüm URL'leri birleştir (tekrar olmadan)
        val allUrls = buildList {
            if (iframeSrc.isNotBlank()) add(iframeSrc)
            addAll(videoParts.filter { it != iframeSrc })
        }

        Log.d("FLMM", "Toplam kaynak: ${allUrls.size} -> $allUrls")

        allUrls.forEach { videoUrl ->
            Log.d("FLMM", "loadExtractor » $videoUrl")
            loadExtractor(videoUrl, "$mainUrl/", subtitleCallback, callback)
        }

        return allUrls.isNotEmpty()
    }
}
