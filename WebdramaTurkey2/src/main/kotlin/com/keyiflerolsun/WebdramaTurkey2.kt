package com.patron

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

private const val TAG = "WDT2"

class WebdramaTurkey2 : MainAPI() {
    override var mainUrl              = "https://webdramaturkey2.com"
    override var name                 = "WebdramaTurkey2"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(
        TvType.AsianDrama,
        TvType.Movie,
        TvType.Anime,
        TvType.Others
    )

    override var sequentialMainPage            = true
    override var sequentialMainPageDelay       = 150L
    override var sequentialMainPageScrollDelay = 150L

    override val mainPage = mainPageOf(
        "${mainUrl}/"                                                    to "Son Bölümler",
        "${mainUrl}/diziler?page="                                       to "Diziler",
        "${mainUrl}/filmler?page="                                       to "Filmler",
        "${mainUrl}/animeler?page="                                      to "Animeler",
        "${mainUrl}/diziler?filter={\"country\":\"1\",\"sorting\":\"newest\"}&page=" to "Kore Dizileri",
        "${mainUrl}/diziler?filter={\"country\":\"2\",\"sorting\":\"newest\"}&page=" to "Çin Dizileri",
        "${mainUrl}/diziler?filter={\"country\":\"3\",\"sorting\":\"newest\"}&page=" to "Japon Dizileri",
        "${mainUrl}/tur/romantik?page="                                  to "Romantik",
        "${mainUrl}/tur/dram?page="                                      to "Dram",
        "${mainUrl}/tur/fantastik?page="                                 to "Fantastik",
        "${mainUrl}/tur/komedi?page="                                    to "Komedi",
        "${mainUrl}/tur/gerilim?page="                                   to "Gerilim",
        "${mainUrl}/tur/aksiyon?page="                                   to "Aksiyon",
        "${mainUrl}/tur/gizem?page="                                     to "Gizem",
        "${mainUrl}/tur/tarihi?page="                                    to "Tarihi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data
        val url  = if (data == "${mainUrl}/") data else "${data}${page}"

        Log.d(TAG, "getMainPage » url: $url")
        val document = app.get(url).document

        val home = if (data == "${mainUrl}/") {
            document.select("div.col.sonyuklemeler")
                .mapNotNull { it.toLatestEpisodeResult() }
        } else {
            document.select("div.col")
                .mapNotNull { it.toMainPageResult() }
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val titleEl = selectFirst("a.list-title") ?: return null
        val title   = titleEl.text().trim().ifEmpty { return null }

        if (BannedList.filtrelenenler.any { title.contains(it, ignoreCase = true) }) return null

        val categoryEl = selectFirst("a.list-category")
        val blCategory = categoryEl?.text()?.trim() ?: ""
        if (blCategory.equals("BL", ignoreCase = true)) return null

        val linkEl  = selectFirst("a") ?: return null
        val href    = fixUrlNull(linkEl.attr("href")) ?: return null

        val coverEl   = selectFirst("div.media.media-cover")
        val posterUrl = fixUrlNull(coverEl?.attr("data-src"))

        val tvType = when {
            href.contains("/film/")  -> TvType.Movie
            href.contains("/anime/") -> TvType.Anime
            else                     -> TvType.AsianDrama
        }

        return newMovieSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toLatestEpisodeResult(): SearchResponse? {
        val titleEl = selectFirst("div.list-title") ?: return null
        val title   = titleEl.text().trim().ifEmpty { return null }

        val linkEl       = selectFirst("a") ?: return null
        val originalHref = linkEl.attr("href").ifEmpty { return null }

        val cleanHref = originalHref.replace(Regex("""/\d+-sezon/\d+-bolum$"""), "/")
        val href      = fixUrlNull(cleanHref) ?: return null

        var posterUrl: String? = null
        val episodeEl = selectFirst("div.media.media-episode")
        if (episodeEl != null) {
            val styleAttr    = episodeEl.attr("style")
            val decodedStyle = styleAttr.replace("&quot;", "\"")
            posterUrl = Regex("""url\("([^"]+)"\)""").find(decodedStyle)?.groupValues?.get(1)

            if (posterUrl.isNullOrEmpty()) {
                posterUrl = episodeEl.attr("data-src").ifEmpty { null }
            }
            if (posterUrl.isNullOrEmpty()) {
                posterUrl = Regex("""https://[^\s"')]+\.(jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)
                    .find(styleAttr)?.value
            }
        }

        val episodeInfoEl = selectFirst("div.list-category")
        val episodeInfo   = episodeInfoEl?.text()?.trim() ?: ""
        val fullTitle     = if (episodeInfo.isNotEmpty()) "$title - $episodeInfo" else title

        val tvType = when {
            href.contains("/film/")  -> TvType.Movie
            href.contains("/anime/") -> TvType.Anime
            else                     -> TvType.AsianDrama
        }

        return newMovieSearchResponse(fullTitle, href, tvType) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        Log.d(TAG, "search » query: $query")
        val url      = "${mainUrl}/arama/${query}"
        val document = app.get(url).document

        return document.select("div.tab-pane:not(#actors) div.col")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private fun Element.toSearchResult(): SearchResponse? {
        val categoryEl = selectFirst("a.list-category")
        val category   = categoryEl?.text()?.trim() ?: ""
        if (category.equals("BL", ignoreCase = true)) return null

        val titleEl = selectFirst("a.list-title") ?: return null
        val title   = titleEl.text().trim().ifEmpty { return null }

        val linkEl = selectFirst("a") ?: return null
        val href   = fixUrlNull(linkEl.attr("href")) ?: return null

        val coverEl = selectFirst("div.media.media-cover")
        var posterUrl = fixUrlNull(coverEl?.attr("data-src"))

        if (posterUrl.isNullOrEmpty() && coverEl != null) {
            val style = coverEl.attr("style")
            posterUrl = Regex("""url\("?([^"')]+)"?\)""").find(style)
                ?.groupValues?.get(1)
                ?.replace("&quot;", "")
                ?.ifEmpty { null }
        }

        val tvType = when {
            href.contains("/film/")  -> TvType.Movie
            href.contains("/anime/") -> TvType.Anime
            else                     -> TvType.AsianDrama
        }

        return newTvSeriesSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        Log.d(TAG, "load » url: $url")
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null

        if (BannedList.filtrelenenler.any { title.contains(it, ignoreCase = true) }) return null

        val poster = fixUrlNull(
            document.selectFirst("div.media-cover img, div.cover img, div.poster img")?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            }
        )

        val plot = document.selectFirst(
            "div.desc, div.description, div.plot, p.desc"
        )?.text()?.trim()

        val tags = document.select("a[href*='/tur/']").map { it.text().trim() }.filter { it.isNotEmpty() }

        val year = document.select("span, div")
            .find { it.text().matches(Regex("""\d{4}""")) }
            ?.text()?.trim()?.toIntOrNull()

        val durationText = document.selectFirst(
            "span[class*='time'], div[class*='duration'], span[class*='sure']"
        )?.text()?.trim()

        val statusText = document.selectFirst(
            "span[class*='status'], div[class*='status'], span[class*='durum']"
        )?.text()?.trim()

        val actorsWithRoles = document.select("div.actor, div.oyuncu, a[href*='/oyuncu/']")
            .mapNotNull { el ->
                val actorName = el.selectFirst("div.name, span.name, img")?.let {
                    it.text().trim().ifEmpty { it.attr("alt") }
                } ?: return@mapNotNull null
                if (actorName.isEmpty()) return@mapNotNull null
                val imgUrl   = fixUrlNull(el.selectFirst("img")?.attr("src"))
                val roleName = el.selectFirst("div.role, span.role")?.text()?.trim()
                Pair(Actor(actorName, imgUrl), roleName ?: "")
            }

        val recommendations = document.select("div.recommendation div.col, div.benzer div.col")
            .mapNotNull { it.toRecommendationResult() }

        val trailer = document.selectFirst("iframe[src*='youtube.com'], iframe[src*='youtu.be']")
            ?.attr("src")

        val isMovie = url.contains("/film/")

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl     = poster
                this.plot          = plot
                this.tags          = tags
                this.year          = year
                this.duration      = durationText?.let { getDurationFromString(it) }
                this.contentRating = statusText
                addActors(actorsWithRoles)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }

        val isAnime = url.contains("/anime/")

        val episodeElements = document.select(
            "a[href*='-sezon/'][href*='-bolum'], a[href*='/sezon/'][href*='/bolum']"
        )

        val episodes = episodeElements.mapNotNull { el ->
            val epHref     = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
            val seasonNum  = Regex("""(\d+)-sezon""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
            val episodeNum = Regex("""(\d+)-bolum""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
            val epTitle    = el.text().trim()

            val epPoster = fixUrlNull(
                el.selectFirst("div.media, img")?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                }
            )

            newEpisode(epHref) {
                this.name = epTitle.ifEmpty {
                    val sText = if (seasonNum != null) "${seasonNum}. Sezon " else ""
                    val eText = if (episodeNum != null) "${episodeNum}. Bölüm" else "Bölüm"
                    sText + eText
                }
                this.season    = seasonNum
                this.episode   = episodeNum
                this.posterUrl = epPoster
            }
        }.distinctBy { it.data }

        if (isAnime) {
            return newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl     = poster
                this.plot          = plot
                this.tags          = tags
                this.year          = year
                this.contentRating = statusText
                addEpisodes(DubStatus.Dubbed, episodes)
                addActors(actorsWithRoles)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl     = poster
            this.plot          = plot
            this.tags          = tags
            this.year          = year
            this.contentRating = statusText
            this.duration      = durationText?.let { getDurationFromString(it) }
            addActors(actorsWithRoles)
            this.recommendations = recommendations
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val titleEl = selectFirst("a.list-title") ?: return null
        val title   = titleEl.text().trim().ifEmpty { return null }
        val linkEl  = selectFirst("a") ?: return null
        val href    = fixUrlNull(linkEl.attr("href")) ?: return null
        val coverEl = selectFirst("div.media.media-cover")
        val poster  = fixUrlNull(coverEl?.attr("data-src"))
        val tvType  = when {
            href.contains("/film/")  -> TvType.Movie
            href.contains("/anime/") -> TvType.Anime
            else                     -> TvType.AsianDrama
        }
        return newMovieSearchResponse(title, href, tvType) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(TAG, "loadLinks » data: $data")
        val document = app.get(data).document
        var foundLinks = false

        val buttons = document.select("button[data-embed], a[data-embed]")
        for (btn in buttons) {
            val embedId    = btn.attr("data-embed").trim().ifEmpty { continue }
            val sourceName = btn.selectFirst("span.name")?.text()?.trim()
                ?: btn.text().trim().ifEmpty { "Alternatif" }

            Log.d(TAG, "Embed ID bulundu: $embedId | kaynak: $sourceName")

            kotlinx.coroutines.delay(700)

            try {
                val ajaxResponse = app.post(
                    "${mainUrl}/ajax/embed",
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer"          to data
                    ),
                    data = mapOf("id" to embedId)
                ).text

                Log.d(TAG, "AJAX yanıtı: $ajaxResponse")

                val videoPhpUrl = Regex("""src=["'](https?://[^"']*video\.php\?[^"']+)["']""")
                    .find(ajaxResponse)?.groupValues?.get(1)

                if (videoPhpUrl != null) {
                    Log.d(TAG, "video.php URL: $videoPhpUrl")
                    val videoPhpHtml = app.get(videoPhpUrl, referer = data).text

                    val iframeUrl = Regex("""<iframe[^>]+src=["'](https?://[^"']+)["']""")
                        .find(videoPhpHtml)?.groupValues?.get(1)

                    if (iframeUrl != null) {
                        Log.d(TAG, "iframe URL: $iframeUrl")

                        if (loadExtractor(iframeUrl, mainUrl, subtitleCallback, callback)) {
                            foundLinks = true
                            continue
                        }

                        if (iframeUrl.contains("dtpasn.asia") || iframeUrl.contains("dtpasn.com")) {
                            WebDramaTurkeyExtractor().getUrl(iframeUrl, mainUrl, subtitleCallback, callback)
                            foundLinks = true
                            continue
                        }

                        if (iframeUrl.contains("bulgu.net/ok/")) {
                            try {
                                val bulguHtml = app.get(iframeUrl, referer = mainUrl).text
                                val redirectPath = Regex("""url["']?\s*:\s*["'](/ok/redirect[^"']+)["']""")
                                    .find(bulguHtml)?.groupValues?.get(1)
                                if (redirectPath != null) {
                                    val finalUrl = "https://bulgu.net$redirectPath"
                                    val isM3u8   = finalUrl.contains(".m3u8")
                                    callback.invoke(
                                        newExtractorLink(
                                            source = sourceName,
                                            name   = sourceName,
                                            url    = finalUrl,
                                            type   = if (isM3u8) INFER_TYPE else ExtractorLinkType.VIDEO
                                        ) {
                                            quality = Qualities.Unknown.value
                                            headers = mapOf("Referer" to mainUrl)
                                        }
                                    )
                                    foundLinks = true
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "bulgu.net hatası: ${e.message}")
                            }
                            continue
                        }

                        if (iframeUrl.contains("vkvideo.ru") || iframeUrl.contains("vk.com")) {
                            val extractor = if (iframeUrl.contains("vk.com")) VkCom() else VkExtractor()
                            extractor.getUrl(iframeUrl, mainUrl, subtitleCallback, callback)
                            foundLinks = true
                            continue
                        }

                        if (iframeUrl.contains("abstream.to")) {
                            Abstream().getUrl(iframeUrl, mainUrl, subtitleCallback, callback)
                            foundLinks = true
                            continue
                        }

                        if (iframeUrl.contains("vidmoly")) {
                            val fixed = iframeUrl.replace(Regex("""vidmoly\.[a-z]+"""), "vidmoly.to")
                            if (loadExtractor(fixed, mainUrl, subtitleCallback, callback)) {
                                foundLinks = true
                            }
                            continue
                        }
                    }
                }

                val fallbackIframe = Regex("""(?:src|iframe)[^\w]*["'](https?://[^"']+)["']""")
                    .find(ajaxResponse)?.groupValues?.get(1)
                if (fallbackIframe != null && loadExtractor(fallbackIframe, mainUrl, subtitleCallback, callback)) {
                    foundLinks = true
                }

            } catch (e: Exception) {
                Log.e(TAG, "embed işleme hatası [$embedId]: ${e.message}")
            }
        }

        if (!foundLinks) {
            document.select("iframe[src]").forEach { iframe ->
                val iframeSrc = fixUrlNull(iframe.attr("src")) ?: return@forEach
                if (loadExtractor(iframeSrc, mainUrl, subtitleCallback, callback)) {
                    foundLinks = true
                }
            }
        }

        return foundLinks
    }
}
