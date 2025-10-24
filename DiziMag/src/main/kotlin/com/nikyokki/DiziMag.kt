package com.nikyokki

import CryptoJS
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern

class DiziMag : MainAPI() {
    override var mainUrl              = "https://dizimag.mom"
    override var name                 = "DiziMag"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    // ! CloudFlare bypass
    override var sequentialMainPage =
        true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay = 250L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 250L  // ? 0.05 saniye

    // ! Haber kısmını boşuna eklemeyin, hata veriyor
    // ! "${mainUrl}/dizi/tur/haber" to "Dizi - Haber", // İçerik yok
    override val mainPage = mainPageOf(
        "${mainUrl}/diziler" to "Diziler - Tümü",
        "${mainUrl}/dizi/tur/aile" to "Dizi - Aile",
        "${mainUrl}/dizi/tur/aksiyon-macera" to "Dizi - Aksiyon & Macera",
        "${mainUrl}/dizi/tur/animasyon" to "Dizi - Animasyon",
        "${mainUrl}/dizi/tur/belgesel" to "Dizi - Belgesel",
        "${mainUrl}/dizi/tur/bilim-kurgu-fantazi" to "Dizi - Bilim Kurgu & Fantazi",
        "${mainUrl}/dizi/tur/cocuk" to "Dizi - Çocuklar",
        "${mainUrl}/dizi/tur/dram" to "Dizi - Dram",
        "${mainUrl}/dizi/tur/gerceklik" to "Dizi - Gerçeklik",
        "${mainUrl}/dizi/tur/gizem" to "Dizi - Gizem",
        
        "${mainUrl}/dizi/tur/komedi" to "Dizi - Komedi",
        "${mainUrl}/dizi/tur/pembe-dizi" to "Dizi - Pembe Dizi",
        "${mainUrl}/dizi/tur/savas-politik" to "Dizi - Savaş Politik",
        "${mainUrl}/dizi/tur/suc" to "Dizi - Suç",
        "${mainUrl}/dizi/tur/talk" to "Dizi - Talk",
        "${mainUrl}/dizi/tur/vahsi-bati" to "Dizi - Vahşi Batı",

        "${mainUrl}/filmler" to "Filmler - Tümü",
        "${mainUrl}/film/tur/aile" to "Film - Aile",
        "${mainUrl}/film/tur/aksiyon" to "Film - Aksiyon",
        "${mainUrl}/film/tur/animasyon" to "Film - Animasyon",
        "${mainUrl}/film/tur/belgesel" to "Film - Belgesel",
        "${mainUrl}/film/tur/bilim-kurgu" to "Film - Bilim-Kurgu",
        "${mainUrl}/film/tur/dram" to "Film - Dram",
        "${mainUrl}/film/tur/fantastik" to "Film - Fantastik",
        "${mainUrl}/film/tur/gerilim" to "Film - Gerilim",
        "${mainUrl}/film/tur/gizem" to "Film - Gizem",
        "${mainUrl}/film/tur/komedi" to "Film - Komedi",
        "${mainUrl}/film/tur/korku" to "Film - Korku",
        "${mainUrl}/film/tur/macera" to "Film - Macera",
        "${mainUrl}/film/tur/muzik" to "Film - Müzik",
        "${mainUrl}/film/tur/romantik" to "Film - Romantik",
        "${mainUrl}/film/tur/savas" to "Film - Savaş",
        "${mainUrl}/film/tur/suc" to "Film - Suç",
        "${mainUrl}/film/tur/tarih" to "Film - Tarih",
        "${mainUrl}/film/tur/tv-film" to "Film - TV Film",
        "${mainUrl}/film/tur/vahsi-bati" to "Film - Vahşi Batı"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val mainReq = app.get("${request.data}/${page}")

        //val document = mainReq.document.body()
        val document = Jsoup.parse(mainReq.body.string())
        val home = document.select("div.poster-long").mapNotNull { it.diziler() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val title =
            this.selectFirst("div.poster-long-subject h2")?.text() ?: return null
        val href =
            fixUrlNull(this.selectFirst("div.poster-long-subject a")?.attr("href"))
                ?: return null
        val posterUrl =
            fixUrlNull(this.selectFirst("div.poster-long-image img")?.attr("data-src"))

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun Element.toPostSearchResult(): SearchResponse? {
        val title = this.selectFirst("span")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        if (href.contains("/dizi/")) {
            return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchReq = app.post(
            "${mainUrl}/search",
            data = mapOf(
                "query" to query
            ),
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Accept-Language" to "en-US,en;q=0.5"
            ),
            referer = "${mainUrl}/"
        ).parsedSafe<SearchResult>()

        if (searchReq?.success != true) {
            throw ErrorLoadingException("Invalid Json response")
        }

        val searchDoc = searchReq.theme

        val document = Jsoup.parse(searchDoc.toString())
        val results = mutableListOf<SearchResponse>()

        document.select("ul li").forEach { listItem ->
            val href = listItem.selectFirst("a")?.attr("href")
            if (href != null && (href.contains("/dizi/") || href.contains("/film/"))) {
                val result = listItem.toPostSearchResult()
                result?.let { results.add(it) }
            }
        }
        return results
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val mainReq = app.get(url, referer = mainUrl)
        val document = mainReq.document
        val title = document.selectFirst("div.page-title h1")?.selectFirst("a")?.text() ?: return null
        val orgtitle = document.selectFirst("div.page-title p")?.text() ?: ""
        var tit = "$title - $orgtitle"
        val poster =
            fixUrlNull(document.selectFirst("div.series-profile-image img")?.attr("src"))
        val year =
            document.selectFirst("h1 span")?.text()?.substringAfter("(")?.substringBefore(")")
                ?.toIntOrNull()
        val rating = document.selectFirst("span.color-imdb")?.text()?.trim()?.toIntOrNull()
        val duration =
            document.selectXpath("//span[text()='Süre']//following-sibling::p").text().trim()
                .split(" ").first().toIntOrNull()
        val description = document.selectFirst("div.series-profile-summary p")?.text()?.trim()
        val tags = document.selectFirst("div.series-profile-type")?.select("a")
            ?.mapNotNull { it.text().trim() }
        val trailer = document.selectFirst("div.series-profile-trailer")?.attr("data-yt")
        val actors = mutableListOf<Actor>()
        document.select("div.series-profile-cast li").forEach {
            val img = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
            val name = it.selectFirst("h5.truncate")?.text()?.trim() ?: return null
            actors.add(Actor(name, img))
        }
        if (url.contains("/dizi/")) {
            val episodeses = mutableListOf<Episode>()
            var szn = 1
            for (sezon in document.select("div.series-profile-episode-list")) {
                var blm = 1
                for (bolum in sezon.select("li")) {
                    val epName = bolum.selectFirst("h6.truncate a")?.text() ?: continue
                    val epHref = fixUrlNull(bolum.select("h6.truncate a").attr("href")) ?: continue
                    val epEpisode = blm++
                    val epSeason = szn
                    episodeses.add(
                        newEpisode(epHref) {
                            this.name = epName
                            this.season = epSeason
                            this.episode = epEpisode
                        }
                    )
                }
                szn++
            }

            return newTvSeriesLoadResponse(tit, url, TvType.TvSeries, episodeses) {
                this.posterUrl = poster
                this.year      = year
                this.plot      = description
                this.tags      = tags
                addActors(actors)
                addTrailer("https://www.youtube.com/embed/${trailer}")
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year      = year
                this.plot      = description
                this.tags      = tags
                this.duration  = duration
                addActors(actors)
                addTrailer("https://www.youtube.com/embed/${trailer}")
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to "$mainUrl/"
        )
        val aa = app.get(mainUrl)
        val ciSession = aa.cookies["ci_session"].toString()
        val document = app.get(
            data, headers = headers, cookies = mapOf(
                "ci_session" to ciSession
            )
        ).document
        val iframe =
            fixUrlNull(document.selectFirst("div#tv-spoox2 iframe")?.attr("src")) ?: return false
        val docum = app.get(iframe, headers = headers, referer = "$mainUrl/").document
        docum.select("script").forEach { sc ->
            if (sc.toString().contains("bePlayer")) {
                val pattern = Pattern.compile("bePlayer\\('(.*?)', '(.*?)'\\)")
                val matcher = pattern.matcher(sc.toString().trimIndent())
                if (matcher.find()) {
                    val key = matcher.group(1)
                    val jsonCipher = matcher.group(2)
                    val cipherData = ObjectMapper().readValue(
                        jsonCipher?.replace("\\/", "/"),
                        Cipher::class.java
                    )
                    val ctt = cipherData.ct
                    val iv = cipherData.iv
                    val s = cipherData.s
                    val decrypt = key?.let { CryptoJS.decrypt(it, ctt, iv, s) }

                    val jsonData = ObjectMapper().readValue(decrypt, JsonData::class.java)

                    for (sub in jsonData.strSubtitles) {
                        subtitleCallback.invoke(
                            SubtitleFile(
                                lang = sub.label.toString(),
                                url = "https://epikplayer.xyz${sub.file}"
                            )
                        )
                    }

                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = jsonData.videoLocation,
                            referer = iframe,
                            quality = Qualities.Unknown.value,
                            type = ExtractorLinkType.M3U8,
                            headers = mapOf("Accept" to "*/*", "Referer" to iframe)
                        )
                    )
                }
            }
        }

        loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        return true
    }
}
