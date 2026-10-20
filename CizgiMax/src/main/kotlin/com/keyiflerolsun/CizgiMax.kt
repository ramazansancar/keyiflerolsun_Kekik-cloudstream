// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class CizgiMax : MainAPI() {
    override var mainUrl              = "https://cizgimax.online"
    override var name                 = "ÇizgiMax"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Cartoon, TvType.Anime, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler/cizgi-film/" to "Çizgi Filmler",
        "${mainUrl}/diziler/dizi/"       to "Diziler",
        "${mainUrl}/diziler/anime/"      to "Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val requestUrl = if (page > 1) {
            val separator = if (request.data.contains("?")) "&" else "?"
            "${request.data.removeSuffix("/")}${separator}page=$page"
        } else {
            request.data
        }

        val document = app.get(requestUrl).document
        val home     = document.select("div.film-list div.film-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val nameEl = this.selectFirst("a.film-name") ?: return null
        val title  = nameEl.text().trim()
        val href   = fixUrlNull(nameEl.attr("href")) ?: return null

        val imgEl     = this.selectFirst("a.poster img")
        val posterUrl = fixUrlNull(imgEl?.attr("src") ?: imgEl?.attr("data-src"))

        val isMovie = href.contains("/film/")
        val type    = if (isMovie) TvType.Movie else TvType.Cartoon

        return if (isMovie) {
            newMovieSearchResponse(title, href, type) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, type) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/ara/?q=${query}").document
        return document.select("div.film-list div.film-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val titleEl     = document.selectFirst("h1 a.anime-title-link") ?: document.selectFirst("h1")
        val title       = titleEl?.text()?.trim() ?: return null

        val imgEl       = document.selectFirst("div.anime-poster img")
        val poster      = fixUrlNull(imgEl?.attr("src") ?: imgEl?.attr("data-src"))

        val description = document.selectFirst("p.anime-desc")?.text()?.trim()
        val tags        = document.select("li.meta-genres strong a").map { it.text().trim() }

        val year        = document.select("ul.anime-meta-col li").firstOrNull {
            it.text().contains("Yayın Yılı", ignoreCase = true)
        }?.selectFirst("strong")?.text()?.toIntOrNull()

        val episodes = mutableListOf<Episode>()

        document.select("div.ep-grid-numbers").forEach { pane ->
            val seasonName = pane.attr("data-season-pane")
            val season     = Regex("""\d+""").find(seasonName)?.value?.toIntOrNull() ?: 1

            pane.select("a.ep-num-btn").forEach { element ->
                val epHref    = fixUrlNull(element.attr("href")) ?: return@forEach
                val epName    = element.attr("title").trim().ifEmpty { "Bölüm" }
                val epEpisode = Regex("""\d+""").find(element.text())?.value?.toIntOrNull() ?: 1

                episodes.add(
                    newEpisode(epHref) {
                        this.name    = epName
                        this.season  = season
                        this.episode = epEpisode
                    }
                )
            }
        }

        return if (episodes.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot      = description
                this.tags      = tags
                this.year      = year
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
                this.posterUrl = poster
                this.plot      = description
                this.tags      = tags
                this.year      = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("CZGM", "data » $data")
        val document = app.get(data).document

        val script = document.select("script").find { it.data().contains("var servers") }?.data() ?: return false

        val serversList = mutableListOf<ServerItem>()

        // 1. Parse 'servers' array
        val serversMatch = Regex("""var\s+servers\s*=\s*JSON\.parse\(atob\((["'])(.*?)\1\)\)""").find(script)
        if (serversMatch != null) {
            val decodedJson = decodeBase64Json(serversMatch.groupValues[2])
            if (decodedJson != null) {
                try {
                    val parsed = AppUtils.tryParseJson<List<ServerItem>>(decodedJson)
                    if (parsed != null) serversList.addAll(parsed)
                } catch (e: Exception) {
                    Log.e("CZGM", "Error parsing servers: ${e.message}")
                }
            }
        }

        // 2. Parse 'serversByLang' map
        val serversByLangMatch = Regex("""var\s+serversByLang\s*=\s*JSON\.parse\(atob\((["'])(.*?)\1\)\)""").find(script)
        if (serversByLangMatch != null) {
            val decodedJson = decodeBase64Json(serversByLangMatch.groupValues[2])
            if (decodedJson != null) {
                try {
                    val parsedMap = AppUtils.tryParseJson<Map<String, List<ServerItem>>>(decodedJson)
                    parsedMap?.values?.forEach { list ->
                        serversList.addAll(list)
                    }
                } catch (e: Exception) {
                    Log.e("CZGM", "Error parsing serversByLang: ${e.message}")
                }
            }
        }

        val uniqueServers = serversList
            .distinctBy { it.embedId?.toString() ?: it.streamUrl ?: it.resolveUrl ?: it.metaUrl ?: it.qualitiesUrl ?: it.src ?: "" }
            .sortedBy { if (it.notice == "copyright") 1 else 0 }

        uniqueServers.forEach { server ->
            val baseLabel = buildServerLabel(server)

            when {
                !server.metaUrl.isNullOrEmpty() -> {
                    handleMetaServer(server, baseLabel, data, subtitleCallback, callback)
                }
                !server.resolveUrl.isNullOrEmpty() -> {
                    handleResolveServer(server, baseLabel, data, callback)
                }
                !server.qualitiesUrl.isNullOrEmpty() -> {
                    handleQualitiesServer(server, baseLabel, data, callback)
                }
                !server.streamUrl.isNullOrEmpty() -> {
                    handleStreamServer(server.streamUrl, baseLabel, data, callback)
                }
                !server.src.isNullOrEmpty() -> {
                    handleIframeServer(server, baseLabel, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }

    private fun decodeBase64Json(rawB64: String): String? {
        return try {
            var padded = rawB64.replace("\\/", "/").replace("\\", "").trim()
            while (padded.length % 4 != 0) padded += "="
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("CZGM", "Base64 decode error: ${e.message}")
            null
        }
    }

    private fun buildServerLabel(server: ServerItem): String {
        val rawLabel = server.label?.trim().takeUnless { it.isNullOrEmpty() } ?: "ÇizgiMax"
        val langPrefix = when (server.lang?.lowercase()) {
            "dub" -> if (!rawLabel.contains("dublaj", ignoreCase = true)) "Dublaj - " else ""
            "sub" -> if (!rawLabel.contains("altyaz", ignoreCase = true)) "Altyazı - " else ""
            else  -> ""
        }
        return "$langPrefix$rawLabel"
    }

    private suspend fun handleMetaServer(
        server: ServerItem,
        baseLabel: String,
        refererUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val metaFullUrl = if (server.metaUrl!!.startsWith("http")) server.metaUrl else "${mainUrl}${server.metaUrl}"
            val metaRes     = app.get(metaFullUrl, referer = refererUrl).parsedSafe<MetaResponse>()

            metaRes?.subs?.forEach { sub ->
                val subUrl = sub.url?.takeIf { it.isNotEmpty() } ?: return@forEach
                val fullSubUrl = if (subUrl.startsWith("http")) subUrl else "${mainUrl}${subUrl}"
                subtitleCallback.invoke(
                    newSubtitleFile(
                        lang = sub.label ?: sub.lang ?: "Türkçe",
                        url  = fullSubUrl
                    )
                )
            }

            val rawHls = metaRes?.hls?.takeIf { it.isNotEmpty() } ?: server.streamUrl
            if (!rawHls.isNullOrEmpty()) {
                handleStreamServer(rawHls, baseLabel, refererUrl, callback)
            }
        } catch (e: Exception) {
            Log.e("CZGM", "Meta error: ${e.message}")
            if (!server.streamUrl.isNullOrEmpty()) {
                handleStreamServer(server.streamUrl, baseLabel, refererUrl, callback)
            }
        }
    }

    private suspend fun handleResolveServer(
        server: ServerItem,
        baseLabel: String,
        refererUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val resolveUrl = if (server.resolveUrl!!.startsWith("http")) server.resolveUrl else "${mainUrl}${server.resolveUrl}"
            val resolveRes = app.get(resolveUrl, referer = refererUrl).parsedSafe<ResolveResponse>()
            val embedId    = resolveRes?.id
            if (!embedId.isNullOrEmpty()) {
                val tauRes = app.get("https://tau-video.xyz/api/video/$embedId", referer = "https://tau-video.xyz/").parsedSafe<TauResponse>()
                tauRes?.urls?.forEach { tauUrl ->
                    if (tauUrl.url.isNotEmpty()) {
                        callback.invoke(
                            newExtractorLink(
                                source = baseLabel,
                                name   = "$baseLabel - ${tauUrl.label ?: "Hızlı"}",
                                url    = tauUrl.url,
                                type   = INFER_TYPE
                            ) {
                                quality = parseQuality(tauUrl.label)
                                headers = mapOf("Referer" to "https://tau-video.xyz/")
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CZGM", "Resolve error: ${e.message}")
        }
    }

    private suspend fun handleQualitiesServer(
        server: ServerItem,
        baseLabel: String,
        refererUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val qUrl = if (server.qualitiesUrl!!.startsWith("http")) server.qualitiesUrl else "${mainUrl}${server.qualitiesUrl}"
            val qRes = app.get(qUrl, referer = refererUrl).parsedSafe<QualitiesResponse>()
            qRes?.qualities?.forEach { qItem ->
                val rawUrl = qItem.url?.takeIf { it.isNotEmpty() } ?: return@forEach
                val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "${mainUrl}${rawUrl}"
                val qLabel = qItem.label ?: qItem.q ?: ""
                callback.invoke(
                    newExtractorLink(
                        source = baseLabel,
                        name   = if (qLabel.isNotEmpty()) "$baseLabel - $qLabel" else baseLabel,
                        url    = fullUrl,
                        type   = if (fullUrl.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else INFER_TYPE
                    ) {
                        quality = parseQuality(qLabel)
                        headers = mapOf("Referer" to refererUrl)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("CZGM", "Qualities error: ${e.message}")
        }
    }

    private suspend fun handleStreamServer(
        rawStreamUrl: String,
        baseLabel: String,
        refererUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val streamUrl = if (rawStreamUrl.startsWith("http")) rawStreamUrl else "${mainUrl}${rawStreamUrl}"
        var finalUrl  = streamUrl
        var contentType: String? = null
        var bodyStart = ""

        try {
            val headRes = app.get(streamUrl, referer = refererUrl, allowRedirects = false)
            if (headRes.code == 301 || headRes.code == 302) {
                val redirectLoc = headRes.headers["location"] ?: headRes.headers["Location"]
                if (!redirectLoc.isNullOrEmpty()) {
                    finalUrl = if (redirectLoc.startsWith("http")) redirectLoc else "${mainUrl}${redirectLoc}"
                }
            } else if (headRes.code >= 400) {
                Log.w("CZGM", "Stream returned HTTP ${headRes.code}: $streamUrl")
                return
            } else {
                contentType = headRes.headers["content-type"] ?: headRes.headers["Content-Type"]
                bodyStart   = headRes.text.take(64).trimStart()
            }
        } catch (e: Exception) {
            Log.e("CZGM", "Stream check error: ${e.message}")
        }

        val hlsRegex = Regex("""/api/stream/(hdfc|vidmoly|rapidvid|vidmoxy|byse|fkr|voe)/|/api/stream/mrc/(?![^\s]*[?&]mp4=1)|workers\.dev/|optr\.cizgimax\.online/|\.m3u8(\?|$)""", RegexOption.IGNORE_CASE)
        val isM3u8 = hlsRegex.containsMatchIn(finalUrl)
            || contentType?.contains("mpegurl", ignoreCase = true) == true
            || bodyStart.startsWith("#EXTM3U")

        val headersMap = when {
            finalUrl.contains("sibnet.ru") -> mapOf("Referer" to "https://video.sibnet.ru/")
            else                           -> mapOf("Referer" to refererUrl)
        }

        callback.invoke(
            newExtractorLink(
                source = baseLabel,
                name   = baseLabel,
                url    = finalUrl,
                type   = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                quality = parseQuality(baseLabel)
                headers = headersMap
            }
        )
    }

    private suspend fun handleIframeServer(
        server: ServerItem,
        baseLabel: String,
        refererUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val rawSrc = server.src ?: return
        var iframeSrc = if (rawSrc.startsWith("http")) rawSrc else "${mainUrl}${rawSrc}"

        if (iframeSrc.contains("/oynat/")) {
            try {
                val redirRes = app.get(iframeSrc, referer = refererUrl, allowRedirects = false)
                val loc = redirRes.headers["location"] ?: redirRes.headers["Location"]
                if (!loc.isNullOrEmpty()) {
                    iframeSrc = if (loc.startsWith("http")) loc else "${mainUrl}${loc}"
                } else {
                    return
                }
            } catch (e: Exception) {
                Log.e("CZGM", "Oynat redirect error: ${e.message}")
                return
            }
        }

        Log.d("CZGM", "iframeSrc ($baseLabel) » $iframeSrc")

        when {
            iframeSrc.contains("/api/stream/") -> {
                handleStreamServer(iframeSrc, baseLabel, refererUrl, callback)
            }
            iframeSrc.contains("dzen.ru") -> {
                extractDzen(iframeSrc, baseLabel, callback)
            }
            iframeSrc.contains("vidmoly") -> {
                extractVidmoly(iframeSrc, baseLabel, callback)
            }
            iframeSrc.contains("odnoklassniki.ru") || iframeSrc.contains("ok.ru") -> {
                extractOkRu(iframeSrc, baseLabel, callback)
            }
            iframeSrc.contains("rubyvidhub") || iframeSrc.contains("streamruby") || iframeSrc.contains("rubystm") -> {
                extractStreamRuby(iframeSrc, baseLabel, callback)
            }
            else -> {
                loadExtractor(iframeSrc, "$mainUrl/", subtitleCallback, callback)
            }
        }
    }

    private suspend fun extractDzen(
        url: String,
        baseLabel: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(
                url     = url,
                headers = mapOf(
                    "User-Agent"       to "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.7049.38 Mobile Safari/537.36",
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                referer = "https://dzen.ru/"
            ).document

            val script = document.select("script").find { it.data().contains("\"streams\":") }?.data() ?: return
            val content = script.substringAfter("\"streams\":").substringBefore("],") + "]"
            val streams = AppUtils.tryParseJson<List<DzenStream>>(content)

            streams?.forEach { stream ->
                val type      = stream.type ?: return@forEach
                val streamUrl = stream.url?.takeIf { it.isNotEmpty() } ?: return@forEach
                val quality   = parseQuality(type)

                val linkType = when (type) {
                    "hls"  -> ExtractorLinkType.M3U8
                    "dash" -> ExtractorLinkType.DASH
                    else   -> ExtractorLinkType.VIDEO
                }
                val suffix = when (type) {
                    "hls"  -> "HLS"
                    "dash" -> "DASH"
                    else   -> Qualities.getStringByInt(quality)
                }

                callback.invoke(
                    newExtractorLink(
                        source = baseLabel,
                        name   = "$baseLabel - $suffix",
                        url    = streamUrl,
                        type   = linkType
                    ) {
                        this.referer = "https://dzen.ru/"
                        this.quality = quality
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("CZGM", "Dzen error: ${e.message}")
        }
    }

    private suspend fun extractVidmoly(
        url: String,
        baseLabel: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val headers = mapOf(
                "User-Agent"     to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Sec-Fetch-Dest" to "iframe"
            )
            val targetUrl = url.replace("vidmoly.me", "vidmoly.net").replace("vidmoly.to", "vidmoly.net")
            val iSource   = app.get(targetUrl, headers = headers, referer = "$mainUrl/").text
            val m3uLink   = Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)["']""").find(iSource)?.groupValues?.get(1)

            if (!m3uLink.isNullOrEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        source = baseLabel,
                        name   = baseLabel,
                        url    = m3uLink,
                        type   = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://vidmoly.net/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("CZGM", "VidMoly error: ${e.message}")
        }
    }

    private suspend fun extractOkRu(
        url: String,
        baseLabel: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val userAgent = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
            val videoReq  = app.get(url, headers = userAgent).text
                .replace("\\&quot;", "\"")
                .replace("&quot;", "\"")
                .replace("\\\\", "\\")
                .replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { matchResult ->
                    Integer.parseInt(matchResult.groupValues[1], 16).toChar().toString()
                }
            val videosStr = Regex(""""videos"\s*:\s*(\[[^\]]*\])""").find(videoReq)?.groupValues?.get(1) ?: return
            val videos    = AppUtils.tryParseJson<List<OkRuVideo>>(videosStr) ?: return

            for (video in videos) {
                val rawUrl   = video.url?.takeIf { it.isNotEmpty() } ?: continue
                val videoUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
                val qValue   = parseQuality(video.name)
                val qName    = Qualities.getStringByInt(qValue)

                callback.invoke(
                    newExtractorLink(
                        source = baseLabel,
                        name   = "$baseLabel - $qName",
                        url    = videoUrl,
                        type   = INFER_TYPE
                    ) {
                        headers      = userAgent
                        this.quality = qValue
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("CZGM", "OkRu error: ${e.message}")
        }
    }

    private suspend fun extractStreamRuby(
        url: String,
        baseLabel: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val response = app.get(url, referer = "$mainUrl/").text
            val unpacked = getAndUnpack(response)
            val m3uLink  = Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)?.groupValues?.get(1)
                ?: Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(response)?.groupValues?.get(1)

            if (!m3uLink.isNullOrEmpty()) {
                val origin = Regex("""^(https?://[^/]+)""").find(url)?.groupValues?.get(1) ?: "https://rubyvidhub.com"
                callback.invoke(
                    newExtractorLink(
                        source = baseLabel,
                        name   = baseLabel,
                        url    = m3uLink,
                        type   = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$origin/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("CZGM", "StreamRuby error: ${e.message}")
        }
    }

    private fun parseQuality(qualityName: String?): Int {
        val q = qualityName?.lowercase()?.trim() ?: return Qualities.Unknown.value
        Regex("""(\d{3,4})p""").find(q)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return when {
            q.contains("ultra") || q.contains("4k")     -> Qualities.P2160.value
            q.contains("quad")  || q.contains("1440")   -> Qualities.P1440.value
            q.contains("full")  || q.contains("1080")   -> Qualities.P1080.value
            q.contains("high")  || q.contains("hd")     -> Qualities.P720.value
            q.contains("med")   || q.contains("sd")     -> Qualities.P480.value
            q.contains("lowest")                        -> Qualities.P240.value
            q.contains("low")                           -> Qualities.P360.value
            q.contains("tiny")  || q.contains("mobile") -> Qualities.P144.value
            else                                        -> Qualities.Unknown.value
        }
    }
}
