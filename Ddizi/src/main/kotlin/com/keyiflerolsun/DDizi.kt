package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink

import org.jsoup.nodes.Element

class DDizi : MainAPI() {
    override var mainUrl              = "https://www.ddizi.im"
    override var name                 = "DDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/yeni-eklenenler1"  to "Son Eklenen Bölümler",
        "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
        "$mainUrl/arama/"            to "Yerli Diziler",
        "$mainUrl/eski.diziler"      to "Eski Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}/$page" else request.data
        val document = app.get(url, headers = getHeaders(mainUrl)).document

        // Tek bir seçiciyle hem dizi-boxpost hem dizi-boxpost-cat alınır
        val home = document.select("div.dizi-boxpost, div.dizi-boxpost-cat")
            .mapNotNull { it.toSearchResult() }

        val hasNextPage = document.selectFirst(".pagination a:contains(Sonraki)") != null
        return newHomePageResponse(request.name, home, hasNextPage)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = selectFirst("a") ?: return null
        val title = linkElement.text().trim() ?: return null
        val href = fixUrl(linkElement.attr("href") ?: return null)
        val posterUrl = selectFirst("img.img-back, img.img-back-cat")
            ?.let { fixUrlNull(it.attr("data-src") ?: it.attr("src")) }

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "$mainUrl/arama/",
            data = mapOf("arama" to query),
            headers = getHeaders(mainUrl)
        ).document

        // Çoklu seçiciyle arama sonuçları alınır, alternatifler birleştirilir
        return document.select("div.dizi-boxpost, div.dizi-boxpost-cat, div.dizi-listesi a, div.yerli-diziler li a, div.yabanci-diziler li a")
            .mapNotNull { element ->
                val title = element.text().trim() ?: return@mapNotNull null
                val href = fixUrl(element.attr("href") ?: return@mapNotNull null)
                newTvSeriesSearchResponse(title, href, TvType.TvSeries)
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = getHeaders(mainUrl)).document
        val fullTitle = document.selectFirst("h1, h2, div.dizi-boxpost-cat a")?.text()?.trim() ?: ""

        // Başlık ayrıştırma için daha basit bir yöntem
        val (title, season, episode) = parseTitle(fullTitle)
        val posterUrl = document.selectFirst("div.afis img, img.afis, img.img-back, img.img-back-cat")
            ?.let { fixUrlNull(it.attr("data-src") ?: it.attr("src")) }
        val plot = document.selectFirst("div.dizi-aciklama, div.aciklama, p")?.text()?.trim()

        val episodes = mutableListOf<Episode>()
        if (url.contains("/dizi/") || url.contains("/diziler/")) {
            // Dizi sayfası için tüm bölümleri topla
            var currentPage = 0
            var hasMorePages = true

            while (hasMorePages) {
                val pageUrl = if (currentPage == 0) url else "$url/sayfa-$currentPage"
                val pageDoc = if (currentPage == 0) document else app.get(pageUrl, headers = getHeaders(mainUrl)).document

                val pageEpisodes = pageDoc.select("div.bolumler a, div.sezonlar a, div.dizi-arsiv a, div.dizi-boxpost-cat a")
                    .mapNotNull { ep ->
                        val name = ep.text().trim()
                        val href = fixUrl(ep.attr("href"))
                        val (epTitle, epSeason, epEpisode) = parseTitle(name)
                        newEpisode(href) {
                            this.name = epTitle
                            this.season = epSeason
                            this.episode = epEpisode
                        }
                    }

                episodes.addAll(pageEpisodes)
                currentPage++
                hasMorePages = pageEpisodes.isNotEmpty() && pageDoc.selectFirst(".pagination a:contains(Sonraki)") != null
            }
        } else {
            // Tek bölüm sayfası
            episodes.add(newEpisode(url) {
                this.name = fullTitle
                this.season = season
                this.episode = episode
                this.description = plot
            })
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = posterUrl
            this.plot = plot
        }
    }

    private fun parseTitle(fullTitle: String): Triple<String, Int, Int?> {
        val seasonMatch = Regex("""(\d+)\.?\s*Sezon""", RegexOption.IGNORE_CASE).find(fullTitle)
        val episodeMatch = Regex("""(\d+)\.?\s*Bölüm""", RegexOption.IGNORE_CASE).find(fullTitle)
        val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val episode = episodeMatch?.groupValues?.get(1)?.toIntOrNull()

        val title = fullTitle.replace(Regex("""^\d+\.?\s*|\d+\.?\s*Sezon\s*|\d+\.?\s*Bölüm\s*|Sezon Finali""", RegexOption.IGNORE_CASE), "").trim()
        return Triple(title, season, episode)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = getHeaders(mainUrl)).document
        val ogVideo = document.selectFirst("meta[property=og:video]")?.attr("content")
            ?: return loadExtractor(data, data, subtitleCallback, callback)

        val playerDoc = app.get(ogVideo, headers = getHeaders(data)).document
        val jwScript = playerDoc.select("script").firstOrNull { it.html().contains("jwplayer") && it.html().contains("sources") }
            ?: return loadExtractor(ogVideo, data, subtitleCallback, callback)

        val sourcesRegex = Regex("""sources:\s*\[\s*\{(.*?)\}\s*,?\s*\]""", RegexOption.DOT_MATCHES_ALL)
        val fileRegex = Regex("""file:\s*["'](.*?)["']""")
        val sourcesMatch = sourcesRegex.find(jwScript.html()) ?: return false
        val fileUrl = fileRegex.find(sourcesMatch.groupValues[1])?.groupValues?.get(1) ?: return false

        val isHls = fileUrl.contains(".m3u8") || fileUrl.contains("hls")
        val quality = Regex("""label:\s*["'](.*?)["']""").find(sourcesMatch.groupValues[1])?.groupValues?.get(1) ?: "Auto"
        val videoHeaders = if (fileUrl.contains("master.txt")) {
            mapOf(
                "accept" to "*/*",
                "user-agent" to USER_AGENT,
                "referer" to ogVideo
            )
        } else {
            getHeaders(ogVideo)
        }

        callback.invoke(ExtractorLink(
            source = name,
            name = "$name - $quality",
            url = fileUrl,
            referer = ogVideo,
            quality = getQualityFromName(quality) ?: Qualities.Unknown.value,
            type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
            headers = videoHeaders
        ))

        if (isHls) {
            M3u8Helper.generateM3u8(name, fileUrl, ogVideo, headers = videoHeaders).forEach(callback)
        }

        return true
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

        private fun getHeaders(referer: String): Map<String, String> = mapOf(
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "user-agent" to USER_AGENT,
            "referer" to referer
        )
    }
}
