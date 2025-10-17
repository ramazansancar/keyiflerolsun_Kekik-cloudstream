package com.kerimmkirac

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.Calendar

class SelcukFlix : MainAPI() {
    override var mainUrl = "https://selcukflix.net"
    override var name = "SelcukFlix"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 50L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 50L  // ? 0.05 saniye

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("Just a moment") || doc.html().contains("verifying")) {
                com.lagradost.api.Log.d("SelcukFlix", "!!cloudflare geldi!!")
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

   override val mainPage = mainPageOf(
    "${mainUrl}/tum-bolumler" to "Yeni Eklenen Bölümler",
    "" to "Yeni Diziler",
    "" to "Kore Dizileri",
    "" to "Yerli Diziler",
    "15" to "Aile",
    "17" to "Animasyon",
    "9" to "Aksiyon",
    "5" to "Bilim Kurgu",
    "2" to "Dram",
    "12" to "Fantastik",
    "18" to "Gerilim",
    "3" to "Gizem",
    "8" to "Korku",
    "4" to "Komedi",
    "7" to "Romantik",
)

override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    
    if (request.data.contains("tum-bolumler")) {
        val document = app.get(request.data, interceptor = interceptor).document
        val home = document.select("div.col-span-3 a").mapNotNull { it.sonBolumler() }
        return newHomePageResponse(request.name, home)
    }
    
    
    val urll = if (request.name.contains("Yerli Diziler")) {
        "$mainUrl/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2024&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=&countryIdsComma=29&orderType=date_desc&languageId=-1&currentPage=${page}&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma="
    }
    else if (request.name.contains("Kore Dizileri")) {
        "$mainUrl/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2026&imdbPointMin=1&imdbPointMax=10&categoryIdsComma=&countryIdsComma=21&orderType=date_desc&languageId=-1&currentPage=${page}&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma=KR"
    }
     else {
        "$mainUrl/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2026&imdbPointMin=1&imdbPointMax=10&categoryIdsComma=${request.data}&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=${page}&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma="
    }
    
    val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    
    val catDocument = app.post(
        urll, 
        headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "en-US,en;q=0.5",
            "X-Requested-With" to "XMLHttpRequest",
            "Sec-Fetch-Site" to "same-origin",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Dest" to "empty",
            "Referer" to "${mainUrl}/"
        ),
        referer = "${mainUrl}/",
        interceptor = interceptor
    )
    
    val searchResult: ApiResponse = objectMapper.readValue(catDocument.toString())
    val decodedSearch = base64Decode(searchResult.response.toString())
    val bytes = decodedSearch.toByteArray(Charsets.ISO_8859_1)
    val converted = String(bytes, Charsets.UTF_8)
    val mediaList: MediaList = objectMapper.readValue(converted)
    val home = mediaList.result.map { it.toMainPageResult() }
    
    return newHomePageResponse(request.name, home)
}

private fun MediaItem.toMainPageResult(): SearchResponse {
    val title = this.originalTitle
    val href = fixUrlNull(this.usedSlug)
   val posterUrl = fixUrlNull(
        this.posterUrl?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
            ?.replace("file.dizilla.club", "file.macellan.online")
            ?.replace("images.dizilla.club", "images.macellan.online")
            ?.replace("images.dizimia4.com", "images.macellan.online")
            ?.replace("file.dizimia4.com", "file.macellan.online")
            ?.replace("/f/f/", "/630/910/")
            ?.replace(Regex("(file\\.)[\\w\\.]+\\/?"), "$1macellan.online/")
            ?.replace(Regex("(images\\.)[\\w\\.]+\\/?"), "$1macellan.online/").toString()
    )
    val score = this.imdbPoint

    return newTvSeriesSearchResponse(title!!, href!!, TvType.TvSeries) {
        this.posterUrl = posterUrl
        this.score = Score.from10(score)
    }
}

private suspend fun Element.sonBolumler(): SearchResponse? {
    val name = this.selectFirst("h2")?.text() ?: ""
    val epName = this.selectFirst("div.opacity-80")!!.text().replace(". Sezon ", "x")
        .replace(". Bölüm", "")

    val title = "$name - $epName"

    val epDoc = fixUrlNull(this.attr("href"))?.let { 
        Jsoup.parse(app.get(it, interceptor = interceptor).body.string())
    }

    val href = epDoc?.selectFirst("div.poster a")?.attr("href")?.let { fixUrlNull(it) }
    
    val finalHref = href ?: run {
        epDoc?.selectFirst("a[href*='/dizi/']")?.attr("href")?.let { fixUrlNull(it) }
        ?: epDoc?.selectFirst("link[rel='canonical']")?.attr("href")?.let { 
            val canonicalUrl = it
            val diziSlug = canonicalUrl.substringAfterLast("/").substringBefore("-")
            fixUrlNull("${mainUrl}/dizi/${diziSlug}")
        }
        ?: epDoc?.selectFirst("nav a[href*='/dizi/']")?.attr("href")?.let { fixUrlNull(it) }
    }
    
    if (finalHref == null) {
        return null
    }

    val posterUrl = fixUrlNull(this.selectFirst("div.image img")?.attr("src"))

    return newTvSeriesSearchResponse(title, finalHref, TvType.TvSeries) {
        this.posterUrl = posterUrl
    }
}

    override suspend fun search(query: String): List<SearchResponse> {
    val response = app.post(
        "${mainUrl}/api/bg/searchcontent?searchterm=$query",
        headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "en-US,en;q=0.5",
            "X-Requested-With" to "XMLHttpRequest",
            "Sec-Fetch-Site" to "same-origin",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Dest" to "empty",
            "Referer" to "${mainUrl}/"
        ),
        referer = "${mainUrl}/",
        interceptor = interceptor
    )
    
    val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    
    val searchResult: ApiResponse = objectMapper.readValue(response.toString())
    val decodedSearch = base64Decode(searchResult.response.toString())
    val bytes = decodedSearch.toByteArray(Charsets.ISO_8859_1)
    val converted = String(bytes, Charsets.UTF_8)
    val searchData: SearchResponseData = objectMapper.readValue(converted)
    
    return searchData.result?.mapNotNull { item ->
        val title = item.title.toString()
        val href = fixUrl(item.slug.toString())
        val posterUrl = item.poster?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
            ?.replace("file.dizilla.club", "file.macellan.online")
            ?.replace("images.dizilla.club", "images.macellan.online")
            ?.replace("images.dizimia4.com", "images.macellan.online")
            ?.replace("file.dizimia4.com", "file.macellan.online")
            ?.replace("/f/f/", "/630/910/")
            ?.replace(Regex("(file\\.)[\\w\\.]+\\/?"), "$1macellan.online/")
            ?.replace(Regex("(images\\.)[\\w\\.]+\\/?"), "$1macellan.online/").toString()
        val type = item.type.toString()
        
        if (href.contains("/seri-filmler/")) {
            null
        } else {
            item.toSearchResponse(title, href, posterUrl, type)
        }
    } ?: emptyList()
}

private fun Any.toSearchResponse(title: String, href: String, posterUrl: String, type: String): com.lagradost.cloudstream3.SearchResponse {
    return if (type == "Movies") {
        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    } else {
        newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }
}

    override suspend fun quickSearch(query: String): List<com.lagradost.cloudstream3.SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val encodedDoc = app.get(url, interceptor = interceptor).document
        val script = encodedDoc.selectFirst("script#__NEXT_DATA__")?.data()
        val secureData =
            objectMapper.readTree(script).get("props").get("pageProps").get("secureData")
        val decodedJson = base64Decode(secureData.toString().replace("\"", ""))
        val bytes = decodedJson.toByteArray(Charsets.ISO_8859_1)
        val converted = String(bytes, Charsets.UTF_8)
        val contentDetails: ContentDetails = objectMapper.readValue(converted)
        val item = contentDetails.contentItem
        
        val title = item.originalTitle
        val poster = fixUrlNull(
            item.posterUrl?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
            ?.replace("file.dizilla.club", "file.macellan.online")
            ?.replace("images.dizilla.club", "images.macellan.online")
            ?.replace("images.dizimia4.com", "images.macellan.online")
            ?.replace("file.dizimia4.com", "file.macellan.online")
            ?.replace("/f/f/", "/630/910/")
            ?.replace(Regex("(file\\.)[\\w\\.]+\\/?"), "$1macellan.online/")
            ?.replace(Regex("(images\\.)[\\w\\.]+\\/?"), "$1macellan.online/").toString()
        )
        val description = item.description
        val year = item.releaseYear
        val tags = item.categories?.split(",")
        val rating = item.imdbPoint
        val duration = item.totalMinutes
        val actors = contentDetails.relatedData.cast?.result?.map {
            Actor(
                it.name!!,
                fixUrlNull(
                    it.castImage?.replace(
                        "images-macellan-online.cdn.ampproject.org/i/s/",
                        ""
                    )
                )
            )
        }
        var trailer = ""
        if (contentDetails.relatedData.trailers?.state == true && contentDetails.relatedData.trailers.result?.size!! > 0) {
            
            
            trailer = contentDetails.relatedData.trailers.result[0].rawUrl.toString()
        }

        if (contentDetails.relatedData.seriesData != null) {
            val eps = mutableListOf<Episode>()
            contentDetails.relatedData.seriesData.seasons?.forEach { season ->
                val seasonNo = season.seasonNo
                
                season.episodes?.forEach { episode ->
                    eps.add(newEpisode(fixUrlNull(episode.usedSlug)) {
                        this.name = episode.epText
                        this.season = seasonNo
                        this.episode = episode.episodeNo
                        this.posterUrl = poster
                    })
                }
            }
            return newTvSeriesLoadResponse(title!!, url, TvType.TvSeries, eps) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                addActors(actors)
                addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(title!!, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = Score.from10(rating)
            this.duration = duration
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
    Log.d("kerim", "data » $data")
    val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    val encodedDoc = app.get(data, interceptor = interceptor).document
    val script = encodedDoc.selectFirst("script#__NEXT_DATA__")?.data()
    val secureData =
        objectMapper.readTree(script).get("props").get("pageProps").get("secureData")
    val decodedJson = base64Decode(secureData.toString().replace("\"", ""))
    val bytes = decodedJson.toByteArray(Charsets.UTF_8)
    val converted = String(bytes, Charsets.UTF_8)
    val contentDetails: ContentDetails = objectMapper.readValue(converted)
    val relatedData = contentDetails.relatedData

    var sourceContent: String? = null

    if (data.contains("/dizi/")) {
        if (relatedData.episodeSources?.state == true) {
           
            val firstSource = relatedData.episodeSources.result?.firstOrNull()
            sourceContent = firstSource?.sourceContent?.toString()
        }
    } else {
        if (relatedData.movieParts?.state == true) {
            val ids = mutableListOf<Int>()
            objectMapper.readTree(converted).get("RelatedResults").get("getMoviePartsById")
                .get("result").forEach { it ->
                    ids.add(it.get("id").asInt())
                }
            val firstPart = relatedData.movieParts.result?.firstOrNull()
            if (firstPart != null) {
                val firstSourceNode = objectMapper.readTree(converted).get("RelatedResults")
                    .get("getMoviePartSourcesById_${firstPart.id}")
                    .get("result")?.firstOrNull()
                sourceContent = firstSourceNode?.get("source_content")?.asText()
            }
        }
    }

    if (sourceContent != null) {
        Log.d("kerim", "sourceContent -> $sourceContent")
        val iframe = fixUrlNull(Jsoup.parse(sourceContent).select("iframe").attr("src"))
        Log.d("kerim", "iframe » $iframe")
        if (iframe == null) {
            return false
        }
        val iframeKontrol = if (iframe.contains("sn.dplayer74.site")) {
            iframe.replace("sn.dplayer74.site", "sn.hotlinger.com")
        } else {
            iframe
        }
        loadExtractor(iframeKontrol, "${mainUrl}/", subtitleCallback, callback)
        return true
    }

    return false
}}
