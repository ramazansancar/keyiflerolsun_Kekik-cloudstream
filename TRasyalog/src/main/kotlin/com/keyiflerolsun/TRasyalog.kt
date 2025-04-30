package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.*
import org.jsoup.Jsoup


class TRasyalog : MainAPI() {
    override var mainUrl        = "https://asyalog.com"
    override var name           = "TRasyalog"
    override val hasMainPage    = true
    override var lang           = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/library/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 500L  // ? 0.5 saniye
    override var sequentialMainPageScrollDelay = 500L  // ? 0.5 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/category/kore-dizileri-izle-guncel/" to "Kore Dizileri",
        "${mainUrl}/category/cin-dizileri/"              to "Çin Dizileri",
        "${mainUrl}/category/tayland-dizileri/"          to "TaylandDizileri",
        "${mainUrl}/category/japon-dizileri/"            to "Japon Diziler",
        "${mainUrl}/category/endonezya-dizileri/"        to "Endonezya Diziler",
        "${mainUrl}/category/seri-diziler/"              to "Seri Diziler",
        "${mainUrl}/category/devam-eden-diziler/"        to "Devam eden Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page").document
        val home     = document.select("div.post-container").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt")?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.thumbnail img")?.let { img ->
        img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src")
    }
)

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/search?name=${query}").document

        return document.select("div.post-container").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

override suspend fun load(url: String): LoadResponse? {
    val document = app.get(url).document

    val title = document.selectFirst("h1")?.text()?.trim() ?: return null
    val poster = fixUrlNull(document.selectFirst("img.wp-image-66892")?.attr("src"))
    val description = document.selectFirst("h2 > p")?.text()?.trim()
    val tags = document.select("div.post-meta a[href*='/category/']").map { it.text() }

    val episodeses = mutableListOf<Episode>()

    for (bolum in document.select("span[data-url*='-bolum']")) {
        val epPath = bolum.attr("data-url")?.trim() ?: continue
        val epHref = fixUrlNull(mainUrl + epPath) ?: continue
        val epName = bolum.text()?.trim() ?: continue

        // "1-5. Bölüm" için başlangıç ve bitiş numaralarını al
        val episodeRangeMatch = Regex("(\\d+)-(\\d+)\\.\\s*Bölüm").find(epName)
        if (episodeRangeMatch != null) {
            val startEpisode = episodeRangeMatch.groupValues[1].toIntOrNull() ?: continue
            val endEpisode = episodeRangeMatch.groupValues[2].toIntOrNull() ?: continue

            // Bölüm sayfasını ziyaret et
            val episodePageDoc = app.get(epHref).document

            // Bölüm metinlerini ve iframe'leri al
            val episodeElements = episodePageDoc.select("div.su-tabs-pane, div:contains(Bölüm)")
            if (episodeElements.isEmpty()) {
                Log.d("TRASYA", "Episode elements bulunamadı: $epHref")
                continue
            }

            // Her bölüm için iframe ve metni eşleştir
            episodeElements.forEachIndexed { index, element ->
                val episodeText = element.text()
                val episodeNumMatch = Regex("(\\d+)\\.\\s*Bölüm").find(episodeText)
                val episodeNum = episodeNumMatch?.groupValues?.get(1)?.toIntOrNull()
                if (episodeNum == null || episodeNum < startEpisode || episodeNum > endEpisode) return@forEachIndexed

                // Iframe'i elementin içinden veya sonraki kardeşinden al
                val iframeUrl = element.selectFirst("iframe")?.attr("data-src")
                    ?: element.nextElementSibling()?.selectFirst("iframe")?.attr("data-src")
                if (iframeUrl.isNullOrEmpty()) {
                    Log.d("TRASYA", "Iframe bulunamadı for episode $episodeNum")
                    return@forEachIndexed
                }

                // Iframe URL'sine "https:" ekle
                val fixedIframeUrl = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
                val dynamicEpName = "$episodeNum. Bölüm"

                Log.d("TRASYA", "Episode $episodeNum: $fixedIframeUrl")
                val newEpisode = newEpisode(fixedIframeUrl) {
                    this.name = dynamicEpName
                    this.episode = episodeNum
                }
                episodeses.add(newEpisode)
            }
        } else {
            // Tek bölüm varsa
            val singleEpisodeMatch = Regex("(\\d+)\\.\\s*Bölüm").find(epName)
            val epEpisode = singleEpisodeMatch?.groupValues?.get(1)?.toIntOrNull()

            if (epEpisode != null) {
                // Tek bölümün iframe'ini çek
                val episodePageDoc = app.get(epHref).document
                val iframeUrl = episodePageDoc.selectFirst("iframe")?.attr("data-src")
                if (iframeUrl.isNullOrEmpty()) {
                    Log.d("TRASYA", "Iframe bulunamadı for single episode $epEpisode")
                    continue
                }

                // Iframe URL'sine "https:" ekle
                val fixedIframeUrl = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
                Log.d("TRASYA", "Single Episode $epEpisode: $fixedIframeUrl")
                val newEpisode = newEpisode(fixedIframeUrl) {
                    this.name = epName
                    this.episode = epEpisode
                }
                episodeses.add(newEpisode)
            }
        }
    }

    if (episodeses.isEmpty()) {
        Log.d("TRASYA", "Hiçbir bölüm bulunamadı")
        return null
    }

    return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
        this.posterUrl = poster
        this.plot = description
        this.tags = tags
    }
}

override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    Log.d("TRASYA", "loadLinks data: $data")

    // Iframe URL'sine "https:" ekle (gerekirse)
    val fixedIframe = if (data.startsWith("//")) "https:$data" else data
    Log.d("TRASYA", "fixedIframe: $fixedIframe")

    // Iframe URL'sini loadExtractor ile işle
    return try {
        loadExtractor(fixedIframe, "${mainUrl}/", subtitleCallback, callback)
        true
    } catch (e: Exception) {
        Log.e("TRASYA", "loadExtractor hatası: ${e.message}")
        false
    }
}
