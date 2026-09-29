// ! https://github.com/hexated/cloudstream-extensions-hexated/blob/master/Hdfilmcehennemi/src/main/kotlin/com/hexated/Hdfilmcehennemi.kt

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
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
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class HDFilmCehennemi : MainAPI() {
    override var mainUrl              = "https://www.hdfilmcehennemi.nl"
    override var name                 = "HDFilmCehennemi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 150L  // ? 0.15 saniye
    override var sequentialMainPageScrollDelay = 150L  // ? 0.15 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/load/page/sayfano/home/"                                       to "Yeni Eklenen Filmler",
        //"${mainUrl}/load/page/sayfano/categories/nette-ilk-filmler/"               to "Nette İlk Filmler",
        "${mainUrl}/load/page/sayfano/home-series/"                                to "Yeni Eklenen Diziler",
        "${mainUrl}/load/page/sayfano/categories/tavsiye-filmler-izle3/"           to "Tavsiye Filmler",
        "${mainUrl}/load/page/sayfano/imdb7/"                                      to "IMDB 7+ Filmler",
        "${mainUrl}/load/page/sayfano/mostCommented/"                              to "En Çok Yorumlananlar",
        "${mainUrl}/load/page/sayfano/mostLiked/"                                  to "En Çok Beğenilenler",
        //"${mainUrl}/load/page/sayfano/genres/aile-filmleri-izleyin-6/"             to "Aile Filmleri",
        //"${mainUrl}/load/page/sayfano/genres/aksiyon-filmleri-izleyin-5/"          to "Aksiyon Filmleri",
        //"${mainUrl}/load/page/sayfano/genres/animasyon-filmlerini-izleyin-5/"      to "Animasyon Filmleri",
        //"${mainUrl}/load/page/sayfano/genres/belgesel-filmlerini-izle-1/"          to "Belgesel Filmleri",
        //"${mainUrl}/load/page/sayfano/genres/bilim-kurgu-filmlerini-izleyin-3/"    to "Bilim Kurgu Filmleri",
        //"${mainUrl}/load/page/sayfano/genres/komedi-filmlerini-izleyin-1/"         to "Komedi Filmleri",
        //"${mainUrl}/load/page/sayfano/genres/korku-filmlerini-izle-4/"             to "Korku Filmleri",
        //"${mainUrl}/load/page/sayfano/genres/romantik-filmleri-izle-2/"            to "Romantik Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val url = request.data.replace("sayfano", page.toString())
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "Accept" to "*/*", "X-Requested-With" to "fetch"
        )
        val doc = app.get(url, headers = headers, referer = mainUrl, interceptor = interceptor)
        val home: List<SearchResponse>?
        if (!doc.toString().contains("Sayfa Bulunamadı")) {
            val aa: HDFC = objectMapper.readValue(doc.toString())
            val document = Jsoup.parse(aa.html)

            home = document.select("a").mapNotNull { it.toSearchResult() }
            return newHomePageResponse(request.name, home)
        }
        return newHomePageResponse(request.name, emptyList())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title")
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response      = app.get(
            "${mainUrl}/search?q=${query}",
            headers = mapOf("X-Requested-With" to "fetch")
        ).parsedSafe<Results>() ?: return emptyList()
        val searchResults = mutableListOf<SearchResponse>()

        response.results.forEach { resultHtml ->
            val document = Jsoup.parse(resultHtml)

            val title     = document.selectFirst("h4.title")?.text() ?: return@forEach
            val href      = fixUrlNull(document.selectFirst("a")?.attr("href")) ?: return@forEach
            val posterUrl = fixUrlNull(document.selectFirst("img")?.attr("src")) ?: fixUrlNull(document.selectFirst("img")?.attr("data-src"))

            searchResults.add(
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl?.replace("/thumb/", "/list/") }
            )
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val title       = document.selectFirst("h1.section-title")?.text()?.substringBefore(" izle") ?: return null
        val poster      = fixUrlNull(document.select("aside.post-info-poster img.lazyload").lastOrNull()?.attr("data-src"))
        val tags        = document.select("div.post-info-genres a").map { it.text() }
        val year        = document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()
        val tvType      = if (document.select("div.seasons").isEmpty()) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("article.post-info-content > p")?.text()?.trim()
        val actors      = document.select("div.post-info-cast a").map {
            Actor(it.selectFirst("strong")!!.text(), it.select("img").attr("data-src"))
        }

        val recommendations = document.select("div.section-slider-container div.slider-slide").mapNotNull {
                val recName      = it.selectFirst("a")?.attr("title") ?: return@mapNotNull null
                val recHref      = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src")) ?: fixUrlNull(it.selectFirst("img")?.attr("src"))

                newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                    this.posterUrl = recPosterUrl
                }
            }

        return if (tvType == TvType.TvSeries) {
            val trailer  = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/", "")?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
            Log.d("HDCH", "Trailer: $trailer")
            val episodes = document.select("div.seasons-tab-content a").mapNotNull {
                val epName    = it.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
                val epHref    = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                newEpisode(epHref) {
                    this.name = epName
                    this.season = epSeason
                    this.episode = epEpisode
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            val trailer = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/", "")?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
            Log.d("HDCH", "Trailer: $trailer")
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    data class DecOp(val name: String, val rotShift: Int = 0)

    private fun decryptVf9q(script: String): String? {
        try {
            val partsMatch = Regex("""\[\s*((?:['"][^'"]+['"]\s*,?\s*)+)\]""").find(script) ?: return null
            val parts = partsMatch.groupValues[1].split(",").map {
                it.trim().trim('\'', '"').replace("\\/", "/")
            }.filter { it.isNotEmpty() }

            if (parts.isEmpty()) return null

            val isSplice = script.contains("splice")
            val workingParts = parts.toMutableList()

            var qm0jd: String? = null
            var ou6q: String? = null

            val varMatches = Regex("""var\s+[a-zA-Z0-9_]+\s*=\s*["']([^"']+)["'];""").findAll(script).map { it.groupValues[1] }.toList()

            if (varMatches.size >= 2 && !isSplice) {
                qm0jd = varMatches[0]
                ou6q = varMatches[1]
            } else if (isSplice || workingParts.size > 2) {
                val dk0fc = workingParts.size - 2
                val fgzMatch = Regex("""%\s*(\d+)\s*,\s*[a-zA-Z0-9_]+\s*=\s*(\d+)\s*\+\s*\([a-zA-Z0-9_]+\s*%\s*(\d+)\)""").find(script)
                val fgzMod = fgzMatch?.groupValues?.get(1)?.toIntOrNull() ?: 7
                val ugBase = fgzMatch?.groupValues?.get(2)?.toIntOrNull() ?: 8
                val ugMod = fgzMatch?.groupValues?.get(3)?.toIntOrNull() ?: 5

                val fgz26 = dk0fc % fgzMod
                val ug9 = ugBase + (dk0fc % ugMod)

                if (ug9 < workingParts.size && fgz26 < workingParts.size - 1) {
                    ou6q = workingParts.removeAt(ug9)
                    qm0jd = workingParts.removeAt(fgz26)
                }
            }

            if (qm0jd == null || ou6q == null) {
                if (varMatches.size >= 2) {
                    qm0jd = varMatches[0]
                    ou6q = varMatches[1]
                } else {
                    return null
                }
            }

            var jc13 = workingParts.joinToString("")

            val multMatch = Regex("""\*\s*(\d+)\s*\+\s*[a-zA-Z0-9_]+\)\s*%\s*(\d+)""").find(script)
            val hMult = multMatch?.groupValues?.get(1)?.toIntOrNull() ?: if (isSplice) 37 else 31
            val hMod1 = multMatch?.groupValues?.get(2)?.toIntOrNull() ?: if (isSplice) 241 else 251

            var rea2 = 0
            var ec27 = 0
            for (uuv14 in qm0jd.indices) {
                val qq4 = qm0jd[uuv14].code
                rea2 = (rea2 * hMult + qq4) % hMod1
                if (isSplice) {
                    ec27 = (ec27 + ((qq4 shl 1) xor uuv14)) and 255
                } else {
                    ec27 = (ec27 xor (qq4 + uuv14)) and 255
                }
            }

            val pv7 = if (isSplice) ((rea2 * 3 + ec27) % 256) else ((rea2 + ec27) % 256)
            val pa2 = if (isSplice) ((ec27 % 11) + 5) else ((rea2 % 13) + 3)
            var u4y3 = if (isSplice) (((ec27 * 251 + rea2) % 65519) + 1).toLong() else (((rea2 * 256 + ec27) % 65521) + 1).toLong()

            val rotBaseMatch = Regex("""charCodeAt\(0\)\s*-\s*(\d+)""").find(script)
            val rotBase = rotBaseMatch?.groupValues?.get(1)?.toIntOrNull() ?: if (isSplice) 96 else 64

            for (uuv14 in ou6q.length - 1 downTo 0) {
                val a6v = ou6q[uuv14]
                if (a6v == 'b' || a6v == '7') {
                    var padded = jc13
                    while (padded.length % 4 != 0) padded += "="
                    jc13 = String(android.util.Base64.decode(padded, android.util.Base64.NO_WRAP), Charsets.ISO_8859_1)
                } else if (a6v == 'v' || a6v == '3') {
                    jc13 = jc13.reversed()
                } else {
                    val z4o18 = (26 - ((a6v.code - rotBase) % 26)) % 26
                    val sb = StringBuilder()
                    for (c in jc13) {
                        if (c in 'a'..'z') {
                            val shifted = (c.code - 97 + z4o18) % 26 + 97
                            sb.append(shifted.toChar())
                        } else if (c in 'A'..'Z') {
                            val shifted = (c.code - 65 + z4o18) % 26 + 65
                            sb.append(shifted.toChar())
                        } else {
                            sb.append(c)
                        }
                    }
                    jc13 = sb.toString()
                }
            }

            val prngMatch = Regex("""\*\s*(\d+)\s*\+\s*(\d+)\)\s*%\s*(\d+)""").findAll(script).lastOrNull()
            val pMult = prngMatch?.groupValues?.get(1)?.toLongOrNull() ?: if (isSplice) 97L else 75L
            val pAdd = prngMatch?.groupValues?.get(2)?.toLongOrNull() ?: if (isSplice) 41L else 74L
            val pMod = prngMatch?.groupValues?.get(3)?.toLongOrNull() ?: if (isSplice) 65519L else 65537L

            val j4l88 = jc13.length
            val sd0f6 = IntArray(j4l88)
            for (uuv14 in j4l88 - 1 downTo 1) {
                u4y3 = (u4y3 * pMult + pAdd) % pMod
                sd0f6[uuv14] = (u4y3 % (uuv14 + 1)).toInt()
            }

            val h43 = jc13.toCharArray()
            for (uuv14 in 1 until j4l88) {
                val gka3 = sd0f6[uuv14]
                val rv62 = h43[uuv14]
                h43[uuv14] = h43[gka3]
                h43[gka3] = rv62
            }
            jc13 = String(h43)

            var m3l = pv7
            val multXor = if (isSplice) 5 else 1
            val kmqj1 = StringBuilder()
            for (uuv14 in jc13.indices) {
                val qq4 = jc13[uuv14].code
                m3l = (m3l * multXor + pa2) % 256
                kmqj1.append((qq4 xor m3l).toChar())
                m3l = (m3l + qq4) % 256
            }

            return kmqj1.toString()
        } catch (e: Exception) {
            Log.e("HDCH", "decryptVf9q Error: ${e.message}")
            return null
        }
    }

    private fun decryptLegacyLocalUrl(unpackedScript: String): String? {
        try {
            val partsMatch = """\(\[\s*((?:['"][^'"]+['"]\s*,?\s*)+)\]\)""".toRegex().find(unpackedScript)
            val parts = partsMatch?.groupValues?.get(1)?.split(",")?.map { 
                it.trim().trim('\'', '"').replace("\\/", "/") 
            } ?: return null

            val moduloMatch = """(\d+)\s*%\s*\(i\s*\+\s*(\d+)\)""".toRegex().find(unpackedScript)
            val magicNum = moduloMatch?.groupValues?.get(1)?.toLongOrNull() ?: 399756995L
            val magicOffset = moduloMatch?.groupValues?.get(2)?.toIntOrNull() ?: 5

            val funcBody = unpackedScript.substringAfter("function dc_").substringBefore("function d1x")

            val operations = mutableListOf<Pair<Int, DecOp>>()

            var index = funcBody.indexOf("atob(")
            while (index >= 0) {
                operations.add(Pair(index, DecOp("atob")))
                index = funcBody.indexOf("atob(", index + 1)
            }

            index = funcBody.indexOf("reverse")
            while (index >= 0) {
                operations.add(Pair(index, DecOp("reverse")))
                index = funcBody.indexOf("reverse", index + 1)
            }

            index = funcBody.indexOf("replace")
            while (index >= 0) {
                val block = funcBody.substring(index, minOf(index + 300, funcBody.length))
                var shift = 13
                val rotShiftMatch = """charCodeAt\(0\)\s*\+\s*(\d+)""".toRegex().find(block)
                if (rotShiftMatch != null) {
                    shift = rotShiftMatch.groupValues[1].toInt()
                } else {
                    val rotShiftMatch2 = """o\s*-\s*base\s*([+-])\s*(\d+)""".toRegex().find(block)
                    if (rotShiftMatch2 != null) {
                        val sign = rotShiftMatch2.groupValues[1]
                        val num = rotShiftMatch2.groupValues[2].toInt()
                        shift = if (sign == "-") (26 - num) % 26 else num
                    }
                }
                operations.add(Pair(index, DecOp("rot", shift)))
                index = funcBody.indexOf("replace", index + 1)
            }

            operations.sortBy { it.first }

            var result = parts.joinToString("")

            for (op in operations) {
                val action = op.second
                when (action.name) {
                    "reverse" -> {
                        result = result.reversed()
                    }
                    "atob" -> {
                        var paddedResult = result
                        while (paddedResult.length % 4 != 0) {
                            paddedResult += "="
                        }
                        result = String(android.util.Base64.decode(paddedResult, android.util.Base64.NO_WRAP), Charsets.ISO_8859_1)
                    }
                    "rot" -> {
                        val rotShift = action.rotShift
                        val rot = StringBuilder()
                        for (c in result) {
                            if (c in 'a'..'z') {
                                val shifted = c.code + rotShift
                                rot.append(if (shifted > 'z'.code) (shifted - 26).toChar() else shifted.toChar())
                            } else if (c in 'A'..'Z') {
                                val shifted = c.code + rotShift
                                rot.append(if (shifted > 'Z'.code) (shifted - 26).toChar() else shifted.toChar())
                            } else {
                                rot.append(c)
                            }
                        }
                        result = rot.toString()
                    }
                }
            }

            val unmix = StringBuilder()
            for (i in result.indices) {
                val charCode = result[i].code.toLong()
                val decryptedCode = (charCode - (magicNum % (i + magicOffset)) + 256) % 256
                unmix.append(decryptedCode.toInt().toChar())
            }

            return unmix.toString()
        } catch (e: Exception) {
            Log.e("HDCH", "decryptLegacyLocalUrl Error: ${e.message}")
            return null
        }
    }

    private fun decryptLocalUrl(unpackedScript: String): String? {
        val vf9qResult = decryptVf9q(unpackedScript)
        if (vf9qResult != null && (vf9qResult.contains("http") || vf9qResult.contains(".m3u8") || vf9qResult.contains(".mp4") || vf9qResult.contains("master.txt"))) {
            return vf9qResult
        }
        return decryptLegacyLocalUrl(unpackedScript)
    }

    private suspend fun invokeLocalSource(source: String, url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit ) {
        val script = app.get(url, referer = "${mainUrl}/", interceptor = interceptor).document.select("script").find {
            it.data().contains("sources:") || it.data().contains("vf9q") || it.data().contains("eval(") || it.data().contains("function") || it.data().contains("master.txt")
        }?.data() ?: return
        Log.d("HDCH", "script » $script")
        val unpackedScript = getAndUnpack(script)
        val decryptedUrl = decryptLocalUrl(unpackedScript) ?: return
        val lastUrl = decryptedUrl.substringAfter("https").let { "https$it" }
        val subData   = script.substringAfter("tracks: [").substringBefore("]")
        Log.d("HDCH", "subData » $subData")
        AppUtils.tryParseJson<List<SubSource>>("[${subData}]")?.filter { it.kind == "captions"}?.forEach {
            val subtitleUrl = "${mainUrl}${it.file}/"

            val headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                "Referer" to "subtitleUrl"
            )
            val subtitleResponse = app.get(subtitleUrl, headers = headers, allowRedirects=true, interceptor = interceptor)
            if (subtitleResponse.isSuccessful) {
                subtitleCallback(newSubtitleFile(it.language.toString(), subtitleUrl))
                Log.d("HDCH", "Subtitle added: $subtitleUrl")
            } else {
                Log.d("HDCH", "Subtitle URL inaccessible: ${subtitleResponse.code}")
            }
        }
        callback.invoke(
            newExtractorLink(
                source  = source,
                name    = source,
                url     = lastUrl,
                type    = ExtractorLinkType.M3U8
            ) {
                headers = mapOf("Referer" to "${mainUrl}/", "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Norton/124.0.0.0")
                quality = Qualities.Unknown.value
            }
        )
    }

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    Log.d("HDCH", "data » $data")
    val document = app.get(data, interceptor = interceptor).document

    document.select("div.alternative-links").map { element ->
        element to element.attr("data-lang").uppercase()
    }.forEach { (element, langCode) ->
        element.select("button.alternative-link").map { button ->
            button.text().replace("(HDrip Xbet)", "").trim() + " $langCode" to button.attr("data-video")
        }.forEach { (source, videoID) ->
            val apiGet = app.get(
                "${mainUrl}/video/$videoID/", interceptor = interceptor,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "X-Requested-With" to "fetch"
                ),
                referer = data
            ).text
            Log.d("HDCH", "Found videoID: $videoID")
            var iframe = Regex("""data-src=\\"([^"]+)""").find(apiGet)?.groupValues?.get(1)!!.replace("\\", "")
            Log.d("HDCH", "$iframe » $iframe")
            if (iframe.contains("rapidrame")) {
                iframe = "${mainUrl}/rplayer/" + iframe.substringAfter("?rapidrame_id=")
            } else if (iframe.contains("mobi")) {
                val iframeDoc = Jsoup.parse(apiGet)
                iframe = fixUrlNull(iframeDoc.selectFirst("iframe")?.attr("data-src")) ?: return@forEach
            }
            Log.d("HDCH", "$source » $videoID » $iframe")
            invokeLocalSource(source, iframe, subtitleCallback, callback)
        }
    }
    return true
}
    private data class SubSource(
        @JsonProperty("file")    val file: String?  = null,
        @JsonProperty("label")   val label: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("kind")    val kind: String?  = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = arrayListOf()
    )
    data class HDFC(
        @JsonProperty("html") val html: String,
        @JsonProperty("meta") val meta: Meta
    )

    data class Meta(
        @JsonProperty("title") val title: String,
        @JsonProperty("canonical") val canonical: String,
        @JsonProperty("keywords") val keywords: Boolean
    )
}
