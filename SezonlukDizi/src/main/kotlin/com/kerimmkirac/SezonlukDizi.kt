package com.kerimmkirac

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class SezonlukDizi : MainAPI() {
    override var mainUrl              = "https://sezonlukdizi8.com"
    override var name                 = "SezonlukDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
    "${mainUrl}/ajax/dataDefaultSonCikan.asp?d=-1&k=0&s=" to "Yeni Bölümler",
    "${mainUrl}/diziler.asp?siralama_tipi=id&s="          to "Yeni Diziler",
    "${mainUrl}/diziler.asp?siralama_tipi=id&kat=3&s="    to "Asya Dizileri",
    "${mainUrl}/diziler.asp?siralama_tipi=id&kat=1&s="    to "Yabancı Diziler",
    "${mainUrl}/diziler.asp?siralama_tipi=id&kat=4&s="    to "Animasyonlar",
    "${mainUrl}/diziler.asp?siralama_tipi=id&kat=5&s="    to "Animeler",
    "${mainUrl}/diziler.asp?siralama_tipi=id&kat=6&s="    to "Belgeseller",
)
//
//    override var sequentialMainPage = true
//    override var sequentialMainPageDelay       = 50L  // ? 0.05 saniye
//    override var sequentialMainPageScrollDelay = 50L  // ? 0.05 saniye

    private val client = OkHttpClient()


    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.text().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val document = if (request.name == "Yeni Bölümler") {
       
        app.post(
            url = "${request.data}${page}",
            interceptor = interceptor,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "*/*",
                "Referer" to mainUrl
            )
        ).document
    } else {
        
        app.get("${request.data}${page}", referer = "${mainUrl}/", interceptor = interceptor).document
    }
    
    val home = if (request.name == "Yeni Bölümler") {
        
        document.select("div.column div.ui.card").mapNotNull { it.toNewEpisodeSearchResult() }
    } else {
        
        document.select("div.afis a").mapNotNull { it.toSearchResult() }
    }

    return newHomePageResponse(request.name, home)
}

private fun Element.toSearchResult(): SearchResponse? {
    val title     = this.selectFirst("div.description")?.text()?.trim() ?: return null
    val href      = fixUrlNull(this.attr("href")) ?: return null
    val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
    val rating = this.selectFirst("span.imdbp")?.ownText()?.trim()
    Log.d("SZD", "rating » $rating")


    return newTvSeriesSearchResponse(title, href, TvType.TvSeries) 
    { 
        
     this.posterUrl = posterUrl
     this.score = rating?.replace(",", ".")?.toDoubleOrNull()?.let { 
        Score.from10(it) 
    }
}
}

private fun Element.toNewEpisodeSearchResult(): SearchResponse? {
    val link = this.selectFirst("a") ?: return null
    
    val titleElement = link.selectFirst("div.box-title span.title")?.text()?.trim() ?: return null
    val episodeElement = link.selectFirst("div.box-title span.seep")?.text()?.trim() ?: return null
    
    val title = "$titleElement $episodeElement"
    val originalHref = link.attr("href")
    
    
    val seriesName = originalHref.split("/").getOrNull(1) ?: return null
    val href = fixUrlNull("/diziler/$seriesName.html") ?: return null
    
    val posterUrl = fixUrlNull(link.selectFirst("img")?.attr("src"))
    val score = this.selectFirst("span.imdbp")?.ownText()?.replace(",",".")?.trim()?.toDoubleOrNull()
    

    return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
        this.posterUrl = posterUrl
        this.score     = Score.from10(score)
    }
}

    override suspend fun search(query: String): List<SearchResponse> {
        val formBody = FormBody.Builder()
            .add("q", query)
            .build()

        val request = Request.Builder()
            .url("${mainUrl}/ajax/arama.asp")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "${mainUrl}/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0")
            .post(formBody)
            .build()

        return client.newCall(request).execute().use { response ->
            val responseText = response.body?.string() ?: ""
            Log.d("kraptor_SezonlukDizi", "document = $responseText")

            val searchResults = mutableListOf<SearchResponse>()

            try {
                val aramaResponse = parseJson<Arama>(responseText)

                // Eğer response başarılıysa
                if (aramaResponse.status == "success") {
                    val results = aramaResponse.results

                    results.diziler.results.forEach { item ->
                        item.title?.let { title ->
                            val finalUrl = if (item.url?.startsWith("http") == true) {
                                item.url
                            } else {
                                "${mainUrl}${item.url}"
                            }

                            val finalImage = if (item.image?.startsWith("http") == true) {
                                item.image
                            } else {
                                "${mainUrl}${item.image}"
                            }

                            searchResults.add(
                                newTvSeriesSearchResponse(
                                    name = title,
                                    url = finalUrl,
                                    type = TvType.TvSeries,
                                    initializer = {
                                        posterUrl = finalImage
                                        quality = SearchQuality.HD
                                        this.score = Score.from10(item.imdb)
                                    }
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("kraptor_Sezonluk", "JSON Parse Error: ${e.message}")
            }
            searchResults
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, referer = "${mainUrl}/", interceptor = interceptor).document

        val title       = document.selectFirst("div.header")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.image img")?.attr("data-src")) ?: return null
        val year        = document.selectFirst("div.extra span")?.text()?.trim()?.split("-")?.first()?.toIntOrNull()
        val description = document.selectFirst("span#tartismayorum-konu")?.text()?.trim()
        val tags        = document.select("div.labels a[href*='tur']").mapNotNull { it.text().trim() }
        val rating      = document.selectFirst("div.detail")?.text()?.trim()
        val duration    = document.selectXpath("//span[contains(text(), 'Dk.')]").text().trim().substringBefore(" Dk.").toIntOrNull()

        val endpoint    = url.split("/").last()

        val actorsReq  = app.get("${mainUrl}/oyuncular/${endpoint}").document
        val actors     = actorsReq.select("div.doubling div.ui").map {
            Actor(
                it.selectFirst("div.header")!!.text().trim(),
                fixUrlNull(it.selectFirst("img")?.attr("src"))
            )
        }


        val episodesReq = app.get("${mainUrl}/bolumler/${endpoint}", referer = "${mainUrl}/", interceptor = interceptor).document
        val episodes    = mutableListOf<Episode>()
        for (sezon in episodesReq.select("table.unstackable")) {
            for (bolum in sezon.select("tbody tr")) {
                val epName    = bolum.selectFirst("td:nth-of-type(4) a")?.text()?.trim() ?: continue
                val epHref    = fixUrlNull(bolum.selectFirst("td:nth-of-type(4) a")?.attr("href")) ?: continue
                val epEpisode = bolum.selectFirst("td:nth-of-type(3)")?.text()?.substringBefore(".Bölüm")?.trim()?.toIntOrNull()
                val epSeason  = bolum.selectFirst("td:nth-of-type(2)")?.text()?.substringBefore(".Sezon")?.trim()?.toIntOrNull()

                episodes.add(newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                })
            }
        }


        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.score = rating?.replace(",", ".")?.toDoubleOrNull()?.let { 
                Score.from10(it) 
            }
            this.duration  = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("SZD", "data » $data")
        val document = app.get(data, referer = "${mainUrl}/", interceptor = interceptor).document
        val aspData = getAspData()
        val bid      = document.selectFirst("div#dilsec")?.attr("data-id") ?: return false
        Log.d("SZD", "bid » $bid")

        val altyaziResponse = app.post(
            "${mainUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
            referer = "${mainUrl}/",
            interceptor = interceptor,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data    = mapOf(
                "bid" to bid,
                "dil" to "1"
            )
        ).parsedSafe<Kaynak>()
        altyaziResponse?.takeIf { it.status == "success" }?.data?.forEach { veri ->
            Log.d("SZD", "dil»1 | veri.baslik » ${veri.baslik}")

            val veriResponse = app.post(
                "${mainUrl}/ajax/dataEmbed${aspData.embed}.asp",
                referer = "${mainUrl}/",
                interceptor = interceptor,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data    = mapOf("id" to "${veri.id}")
            ).document

            val iframe = when {
    veri.baslik.contains("Dzen", ignoreCase = true) -> {
        val jsScript = veriResponse.selectFirst("script")?.data() ?: return@forEach
        val vid = Regex("""var\s+vid\s*=\s*['"](.+?)['"]""").find(jsScript)?.groupValues?.get(1) ?: return@forEach
        "https://dzen.ru/embed/$vid"
    }

    veri.baslik.contains("Pixel", ignoreCase = true) -> {
        val pixelIframe = fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
        val pixelPage = app.get(pixelIframe).document
        val pixelScript = pixelPage.select("script").mapNotNull { it.data() }.joinToString("\n")

        val hexEncoded = Regex("""file\s*:\s*["']((?:\\x[0-9a-fA-F]{2})+)["']""").find(pixelScript)?.groupValues?.get(1) ?: return@forEach

        val decodedUrl = hexEncoded
            .replace("""\\x""".toRegex(), "")
            .chunked(2)
            .joinToString("") { it.toInt(16).toChar().toString() }

        decodedUrl
    }

    else -> {
        fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
    }
}

            Log.d("SZD", "dil»1 | iframe » $iframe")

            loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                callback.invoke(
                    ExtractorLink(
                        source        = "AltYazı - ${veri.baslik}",
                        name          = "AltYazı - ${veri.baslik}",
                        url           = link.url,
                        referer       = link.referer,
                        quality       = link.quality,
                        headers       = link.headers,
                        extractorData = link.extractorData,
                        type          = link.type
                    )
                )
            }
        }

        val dublajResponse = app.post(
            "${mainUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            referer = "${mainUrl}/",
            interceptor = interceptor,
            data    = mapOf(
                "bid" to bid,
                "dil" to "0"
            )
        ).parsedSafe<Kaynak>()
        dublajResponse?.takeIf { it.status == "success" }?.data?.forEach { veri ->
            Log.d("SZD", "dil»0 | veri.baslik » ${veri.baslik}")

            val veriResponse = app.post(
                "${mainUrl}/ajax/dataEmbed${aspData.embed}.asp",
                referer = "${mainUrl}/",
                interceptor = interceptor,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data    = mapOf("id" to "${veri.id}")
            ).document

            val iframe = when {
    veri.baslik.contains("Dzen", ignoreCase = true) -> {
        val jsScript = veriResponse.selectFirst("script")?.data() ?: return@forEach
        val vid = Regex("""var\s+vid\s*=\s*['"](.+?)['"]""").find(jsScript)?.groupValues?.get(1) ?: return@forEach
        "https://dzen.ru/embed/$vid"
    }

    veri.baslik.contains("Pixel", ignoreCase = true) -> {
        val pixelIframe = fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
        val pixelPage = app.get(pixelIframe).document
        val pixelScript = pixelPage.select("script").mapNotNull { it.data() }.joinToString("\n")

        val hexEncoded = Regex("""file\s*:\s*["']((?:\\x[0-9a-fA-F]{2})+)["']""").find(pixelScript)?.groupValues?.get(1) ?: return@forEach

        val decodedUrl = hexEncoded
            .replace("""\\x""".toRegex(), "")
            .chunked(2)
            .joinToString("") { it.toInt(16).toChar().toString() }

        decodedUrl
    }

    else -> {
        fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
    }
}
            Log.d("SZD", "dil»0 | iframe » $iframe")

            loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                callback.invoke(
                    ExtractorLink(
                        source        = "Dublaj - ${veri.baslik}",
                        name          = "Dublaj - ${veri.baslik}",
                        url           = link.url,
                        referer       = link.referer,
                        quality       = link.quality,
                        headers       = link.headers,
                        extractorData = link.extractorData,
                        type          = link.type
                    )
                )
            }
        }

        return true
    }

    //Helper function for getting the number (probably some kind of version?) after the dataAlternatif and dataEmbed
    private suspend fun getAspData() : AspData{
        val websiteCustomJavascript = app.get("${this.mainUrl}/js/site.min.js")
        val dataAlternatifAsp = Regex("""dataAlternatif(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        val dataEmbedAsp = Regex("""dataEmbed(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        return AspData(dataAlternatifAsp,dataEmbedAsp)
    }
}
