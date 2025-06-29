package com.nikyokki

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
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
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class RoketDizi : MainAPI() {
    override var mainUrl = "https://www.roketdizi.live"
    override var name = "RoketDizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)


    override val mainPage = mainPageOf(
        "${mainUrl}/dizi/tur/aksiyon" to "Aksiyon",
        "${mainUrl}/dizi/tur/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi/tur/gerilim" to "Gerilim",
        "${mainUrl}/dizi/tur/fantastik" to "Fantastik",
        "${mainUrl}/dizi/tur/komedi" to "Komedi",
        "${mainUrl}/dizi/tur/korku" to "Korku",
        "${mainUrl}/dizi/tur/macera" to "Macera",
        "${mainUrl}/dizi/tur/suc" to "Suç",

        "${mainUrl}/film-kategori/animasyon" to "Animasyon Film",
        "${mainUrl}/film-kategori/aile" to "Aile Film",
        "${mainUrl}/film-kategori/aksiyon" to "Aksiyon Film",
        "${mainUrl}/film-kategori/western" to "Western Film",
        "${mainUrl}/film-kategori/gizem" to "Gizem Film",
        "${mainUrl}/film-kategori/gerilim" to "Gerilim Film",
        "${mainUrl}/film-kategori/bilim-kurgu" to "Bilim Kurgu Film",
        "${mainUrl}/film-kategori/savas" to "Savaş Film",
        "${mainUrl}/film-kategori/romantik" to "Romantik Film",
        "${mainUrl}/film-kategori/fantastik" to "Fantastik Film",
        "${mainUrl}/film-kategori/korku" to "Korku Film",
        "${mainUrl}/film-kategori/macera" to "Macera Film",
        "${mainUrl}/film-kategori/suc" to "Suç Film",
        "${mainUrl}/film-kategori/komedi" to "Komedi Film",
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val mainReq = app.get("${request.data}?&page=${page}")
        val document = mainReq.document
        if (request.data.contains("/dizi/")) {
            val home = document.select("span.bg-\\[\\#232323\\]").mapNotNull { it.diziler() }
            return newHomePageResponse(request.name, home)
        }
        val home = document.select("a.w-full").mapNotNull { it.filmler() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.filmler(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.diziler(): SearchResponse? {
        val title = this.selectFirst("span.font-normal.line-clamp-1")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun toSearchResponse(
        ad: String,
        link: String,
        posterLink: String,
        type: String
    ): SearchResponse {
        if (type == "Movies") {
            return newMovieSearchResponse(
                ad,
                link,
                TvType.Movie,
            ) {
                this.posterUrl = posterLink
            }
        } else {
            return newTvSeriesSearchResponse(
                ad,
                link,
                TvType.TvSeries,
            ) {
                this.posterUrl = posterLink
            }
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchReq = app.post(
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
        )
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val searchResult: SearchResult = objectMapper.readValue(searchReq.toString())
        val decodedSearch = base64Decode(searchResult.response.toString())
        val bytes = decodedSearch.toByteArray(Charsets.ISO_8859_1)
        val converted = String(bytes, Charsets.UTF_8)
        val contentJson: SearchData = objectMapper.readValue(converted)
        if (contentJson.state != true) {
            throw ErrorLoadingException("Invalid Json response")
        }
        val veriler = mutableListOf<SearchResponse>()
        contentJson.result?.forEach {
            val name = it.title.toString()
            val link = fixUrl(it.slug.toString())
            val posterLink =
                it.poster.toString().replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
            val type = it.type.toString()
            val toSearchResponse = toSearchResponse(name, link, posterLink, type)
            if (!link.contains("/seri-filmler/")) {
                veriler.add(toSearchResponse)
            }
        }
        return veriler
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val encodedDoc = app.get(url).document
        val script = encodedDoc.selectFirst("script#__NEXT_DATA__")?.data()
        val secureData =
            objectMapper.readTree(script).get("props").get("pageProps").get("secureData")
        val decodedJson = base64Decode(secureData.toString().replace("\"", ""))
        val bytes = decodedJson.toByteArray(Charsets.ISO_8859_1)
        val converted = String(bytes, Charsets.UTF_8)
        val root: Root = objectMapper.readValue(converted)
        val item = root.contentItem
        val orgTitle = item.originalTitle
        val cultTitle = item.cultureTitle.toString()
        val title =
            if (orgTitle == cultTitle || cultTitle.isEmpty()) orgTitle else "$orgTitle - $cultTitle"
        val poster = fixUrlNull(
            item.posterUrl?.replace(
                "images-macellan-online.cdn.ampproject.org/i/s/",
                ""
            )
        )
        val description = item.description
        val year = item.releaseYear
        val tags = item.categories?.split(",")
        val rating = item.imdbPoint
        val duration = item.totalMinutes
        val actors = root.relatedResults.getMovieCastsById?.result?.map {
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
        if (root.relatedResults.getContentTrailers?.state == true && root.relatedResults.getContentTrailers.result?.size!! > 0) {
            Log.d("SFX", "getContentTrailers null değil")
            Log.d(
                "SFX",
                "getContentTrailers ->  ${root.relatedResults.getContentTrailers.result}"
            )
            trailer = root.relatedResults.getContentTrailers.result[0].rawUrl.toString()
        }

        if (root.relatedResults.getSerieSeasonAndEpisodes != null) {
            Log.d("SFX", "getSerieSeasonAndEpisodes null değil")
            Log.d(
                "SFX",
                "getSerieSeasonAndEpisodes ->  ${root.relatedResults.getSerieSeasonAndEpisodes}"
            )
            val eps = mutableListOf<Episode>()
            root.relatedResults.getSerieSeasonAndEpisodes.seasons?.forEach { it ->
                val szn = it.seasonNo
                Log.d("SFX", "Szn -> $szn")
                val episodes = it.episodes
                Log.d("SFX", "episodes -> $episodes")
                episodes?.forEach {
                    eps.add(newEpisode(fixUrlNull(it.usedSlug)) {
                        this.name = it.epText
                        this.season = szn
                        this.episode = it.episodeNo
                    })
                }
                Log.d("SFX", "eps -> $eps")
            }
            return newTvSeriesLoadResponse(title!!, url, TvType.TvSeries, eps) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(title!!, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.rating = rating
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
        Log.d("SFX", "data » $data")
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val encodedDoc = app.get(data).document
        val script = encodedDoc.selectFirst("script#__NEXT_DATA__")?.data()
        val secureData =
            objectMapper.readTree(script).get("props").get("pageProps").get("secureData")
        val decodedJson = base64Decode(secureData.toString().replace("\"", ""))
        val bytes = decodedJson.toByteArray(Charsets.UTF_8)
        val converted = String(bytes, Charsets.UTF_8)
        val root: Root = objectMapper.readValue(converted)
        val iframes = mutableListOf<SourceItem>()
        val relatedResults = root.relatedResults
        if (data.contains("/dizi/")) {
            if (relatedResults.getEpisodeSources?.state == true) {
                relatedResults.getEpisodeSources.result?.forEach { it ->
                    iframes.add(SourceItem(it.sourceContent.toString(), it.qualityName.toString()))
                }
            }
        } else {
            if (relatedResults.getMoviePartsById?.state == true) {
                val ids = mutableListOf<Int>()
                objectMapper.readTree(converted).get("RelatedResults").get("getMoviePartsById")
                    .get("result").forEach { it ->
                        ids.add(it.get("id").asInt())
                    }
                relatedResults.getMoviePartsById.result?.forEach { it ->
                    objectMapper.readTree(converted).get("RelatedResults")
                        .get("getMoviePartSourcesById_${it.id}")
                        .get("result").forEach { ifs ->
                            iframes.add(
                                SourceItem(
                                    ifs.get("source_content").asText(),
                                    ifs.get("quality_name").asText()
                                )
                            )
                        }
                }
            }
        }
        Log.d("SFX", "iframes -> $iframes")
        iframes.forEach { it ->
            val iframe = fixUrlNull(Jsoup.parse(it.sourceContent).select("iframe").attr("src"))
            Log.d("SFX", "iframe » $iframe")
            loadExtractor(iframe!!, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}
