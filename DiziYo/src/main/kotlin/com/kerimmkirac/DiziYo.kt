

package com.kerimmkirac

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.util.EnumSet

class DiziYo : MainAPI() {
    override var mainUrl              = getDomain()
    override var name                 = "DiziYo"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    companion object {
        private fun getDomain(): String {
            return runBlocking {
                try {
                    val domainListesi =
                        app.get("https://raw.githubusercontent.com/Kraptor123/domainListesi/refs/heads/main/eklenti_domainleri.txt").text
                    val domain = domainListesi
                        .split("|")
                        .first { it.trim().startsWith("DiziYo") }
                        .substringAfter(":")
                        .trim()
                    domain
                } catch (e: Exception){
                    "https://diziyo.is"
                }
            }
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler"          to "Filmler",
        "${mainUrl}/diziler"                      to "Diziler",
    )



    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}?page=$page").document

        val home     = document.select("div.w-1-7").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("h4")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.let { img ->
                img.attr("data-wpfc-original-src").takeIf { it.isNotBlank() }
                    ?: img.attr("src").takeIf { it.isNotBlank() }
            }
        )
        val rating = this.selectFirst("div.movie-info-rating strong")?.text().toString()
        val score = if ((rating.toIntOrNull() ?: 0) >= 10) {
            Score.from100(rating)
        } else {
            Score.from10(rating)
        }
        return  newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.score     = score
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val responseString = app.post("${mainUrl}/search", data = mapOf("query" to query.replace(" ","+")), headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With" to "XMLHttpRequest",
            "Connection" to "keep-alive",
            "Referer" to "${mainUrl}/",
        )).text
        val mapper = jacksonObjectMapper().registerKotlinModule()
        val response: DiziyoApi = mapper.readValue(responseString)
        val doc = Jsoup.parse(response.theme)

        val aramaCevap = doc.select("div.w-1-7").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document        = app.get(url).document
        val title           = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.replace("izle","") ?: return null
        val poster          = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description     = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()?.replace("izle","") ?: return null
        val year            = document.selectFirst("a.nbl-link_style_wovou")?.text()?.trim()?.toIntOrNull()
        val tags            = document.select("th.nbl-plankMeta__title:contains(Türler) + td.nbl-plankMeta__aside a").map { it.text() }
        val rating          = document.selectFirst("span.dt_rating_vgs")?.text()?.trim()
        val duration        = document.selectFirst("td.nbl-plankMeta__aside:contains(dk)")?.text()?.split("dk")?.first()?.trim()?.toIntOrNull()
        val recommendations = document.select("div.srelacionados article").mapNotNull { it.toRecommendationResult() }
        val actors = document.select("li.cast-member").map { aktor ->
            val aktorIsim   = aktor.selectFirst("h4")?.text().toString()
            val aktorRol    = aktor.selectFirst("p")?.text()
            val aktorPoster = fixUrlNull(aktor.selectFirst("img")?.attr("src")).toString()
            val actor = Actor(aktorIsim, aktorPoster)
            actor to aktorRol
        }

        val trailer = document.selectFirst("a.episode-link.block")?.attr("data-youtube")?.let {
            "https://youtu.be/$it"
        }

        val bolumler = document.select("div.swiper-slide.episode-card a").mapNotNull { bolum ->
            val bPoster = fixUrlNull(bolum.selectFirst("img")?.attr("src")).toString()
            val bName   = bolum.selectFirst("div.episode-info p.line-clamp-3")?.text()
            val bHref   = fixUrlNull(bolum.selectFirst("a")?.attr("href")).toString()
            val bEp     = bolum.selectFirst("data")?.text()?.toIntOrNull()
            val bSezon  = bHref.substringAfter("sezon-").substringBefore("/").toIntOrNull()
            newEpisode(bHref,{
                this.name = bName
                this.episode = bEp
                this.season  = bSezon
                this.posterUrl = bPoster
            })
        }

        return if (url.contains("film/")){
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl       = poster
                this.plot            = description
                this.year            = year
                this.tags            = tags
                this.score           = Score.from10(rating)
                this.duration        = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else if (url.contains("anime/")){
            newAnimeLoadResponse(   title, url, TvType.Anime, true) {
                this.posterUrl       = poster
                this.plot            = description
                this.year            = year
                this.tags            = tags
                this.episodes        = mutableMapOf(DubStatus.Subbed to bolumler)
                this.score           = Score.from10(rating)
                this.duration        = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, bolumler) {
                this.posterUrl       = poster
                this.plot            = description
                this.year            = year
                this.tags            = tags
                this.score           = Score.from10(rating)
                this.duration        = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("kraptor_$name", "data = ${data}")
        val document = app.get(data).document

        document.select("ul.tab.alternative-group li").map { sifre ->
            val hash = sifre.attr("data-group-hash")
            val videoDil = sifre.text()
            val postData = "hash=${java.net.URLEncoder.encode(hash, "UTF-8")}"
            val post = app.post(
                "${mainUrl}/get/video/group",
                requestBody = postData.toRequestBody("application/x-www-form-urlencoded".toMediaType()),
                headers = mapOf(
                    "Referer" to "${mainUrl}/",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).text
            val mapper = jacksonObjectMapper().registerKotlinModule()
            val response: DiziYoVideo = mapper.readValue(post)
           response.videos.forEach { videoAl ->
               if (videoAl.link.contains("player/video/")){
                   val postData = "hash=${java.net.URLEncoder.encode(videoAl.hash, "UTF-8")}"
                   val videolinki = videoAl.link
                   val videoAl = app.post("${videoAl.link}?do=getVideo", requestBody = postData.toRequestBody("application/x-www-form-urlencoded".toMediaType()), headers = mapOf("Referer" to "${mainUrl}/", "X-Requested-With" to "XMLHttpRequest")).text
                   val vResponse: VideoAl = mapper.readValue(videoAl)
                   vResponse.videoSources.forEach { video ->
                       callback.invoke(newExtractorLink(
                           "${this.name} $videoDil",
                           "${this.name} $videoDil",
                           video.file,
                           type = ExtractorLinkType.M3U8,
                           {
                               this.referer = videolinki
                               this.quality = getQualityFromName(video.label)
                           }
                       ))
                   }
               } else {
                   loadExtractor(videoAl.link, "${mainUrl}/", subtitleCallback, callback)
               }

           }
        }
        return true
    }
}
@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoAl(
    val videoImage: String?,
    val videoSources: List<VideoSource>
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoSource(
    val file: String,
    val label: String,
    val type: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiziYoVideo(
    val success: Boolean,
    val videos: List<Videolar>
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Videolar(
    val name: String,
    val link: String,
    val hash: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiziyoApi(
    val success: Boolean,
    val theme: String
)