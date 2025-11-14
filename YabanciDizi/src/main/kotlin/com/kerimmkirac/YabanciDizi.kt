
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
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class YabanciDizi : MainAPI() {
    override var mainUrl = "https://yabancidizi.so"
    override var name = "YabanciDizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries ,TvType.Movie)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 250L
    override var sequentialMainPageScrollDelay = 250L
    //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())
            if (doc.html().contains("Just a moment...") || doc.html().contains("verifying")) {
//                Log.d("kraptor_YabanciDizi", "cloudflare geldi!")
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    private val YabanciHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
        "Referer" to "${mainUrl}/",
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/kesfet/eyJvcmRlciI6ImRhdGVfYm90dG9tIiwiY291bnRyeSI6eyJLUiI6IktSIn19"         to "Kdrama",
        "${mainUrl}/kesfet/eyJvcmRlciI6ImRhdGVfYm90dG9tIiwiY291bnRyeSI6eyJKUCI6IkpQIn0sImNhdGVnb3J5IjpbXX0=" to "Jdrama",
        "${mainUrl}/kesfet/eyJvcmRlciI6ImRhdGVfYm90dG9tIiwiY2F0ZWdvcnkiOnsiMyI6IjMifX0="                     to "Animasyon",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = if (page == 1) {
            app.get("${request.data}", headers = YabanciHeaders, interceptor = interceptor).document
        } else {
            app.get("${request.data}/$page", headers = YabanciHeaders, interceptor = interceptor).document
        }
        val home = document.select("li.mb-lg, li.segment-poster").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val score = this.selectFirst("span.rating")?.text()

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
            this.posterHeaders = mapOf("Referer" to "${mainUrl}/")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "${mainUrl}/search?qr=$query",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            referer = "${mainUrl}/",
        ).text
        val mapper = jacksonObjectMapper().registerKotlinModule()
        val cevap: JsonResponse = mapper.readValue(response)

        return cevap.data.result.mapNotNull { item ->
            val title = item.s_name
            val posterUrl = fixUrlNull("$mainUrl/uploads/series/${item.s_image}") ?: ""
            when (item.s_type) {
                "0" -> {
                    val href = fixUrlNull("$mainUrl/dizi/${item.s_link}") ?: ""
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    }
                }
                "1" -> {
                    val href = fixUrlNull("$mainUrl/film/${item.s_link}") ?: ""
                    newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl
                    }
                }
                else -> null
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = YabanciHeaders, interceptor = interceptor, allowRedirects = true).document

        val title           = document.selectFirst("meta[property=og:title]")?.attr("content")?.split("|")?.first()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description     = document.selectFirst("p#tv-series-desc")?.text()?.substringBefore("----")?.trim()
        val year            = document.selectFirst("td div.truncate")?.text()?.trim()?.toIntOrNull()
        val tags            = document.select("div.item span.label:contains(türü:) ~ a").map { it.text() }
        val rating          = document.selectFirst("td div:contains(IMDB) + div")?.text()?.trim()
        val duration        = document.selectFirst("td div:contains(süre) + div")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val actors          = document.select("div.item span.label:contains(oyuncular:) ~ a").map { Actor(it.text()) }
        val aktorler        = document.select("div#common-cast-list div.item").map { oyuncu ->
            val isim        = oyuncu.selectFirst("h5")?.text().toString()
            val poster      = fixUrlNull(oyuncu.selectFirst("img")?.attr("src"))
            Actor(isim, poster)
        }

        val trailer = Regex("""embed\/(.*)\?rel""").find(document.html())?.groupValues?.get(1)
            ?.let { "https://www.youtube.com/embed/$it" }

        val bolumler = document.select("div.episodes-list div.ui td:has(h6)").map { bolum ->
            val bolumHref = fixUrlNull(bolum.selectFirst("a")?.attr("href")).toString()
            val bolumAdi  = bolum.selectFirst("h6")?.text() ?: bolum.selectFirst("a")?.text().toString()
            val bSezon    = bolumHref.substringAfter("sezon-").substringBefore("/").toIntOrNull()
            val bolum     = bolumHref.substringAfter("bolum-").toIntOrNull()
            newEpisode(bolumHref, {
                this.name    = bolumAdi
                this.season  = bSezon
                this.episode = bolum
            })
        }

        return if (url.contains("/film/")) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, bolumler) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration
                addActors(aktorler)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
//        Log.d("kraptor_$name", "data = ${data}")
        val document = app.get(data, headers = YabanciHeaders).document

        document.select("div.alternatives-for-this div.item:not(.active)").map { alternatif ->
            val dataHash = alternatif.attr("data-hash")
            val dataLink = alternatif.attr("data-link")
            val queryType = alternatif.attr("data-querytype")
            val postIstek = app.post("${mainUrl}/ajax/service", data = mapOf(
                "link" to dataLink,
                "hash" to dataHash,
                "querytype" to queryType,
                "type" to "videoGet"
            ), headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "${mainUrl}/"
            )
                , cookies = mapOf(
                    "udys"  to "1760709729873",
                    "level" to "1"
                )).text
            val mapper = jacksonObjectMapper().registerKotlinModule()
            val cevap: Service = mapper.readValue(postIstek)
            val firstResponse = app.get(
                cevap.api_iframe.toString(),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                    "Referer" to "${mainUrl}/"
                ),
                cookies = mapOf(
                    "udys" to "1760709729873",
                    "level" to "1"
                )
            )

            val cookies = firstResponse.cookies.toMutableMap().apply {
                put("udys", "1760709729873")
                put("level", "1")
            }
            val cfyCookie = firstResponse.cookies["_cfy"]
            if (cfyCookie != null) {
                cookies["_cfy"] = cfyCookie
            }

            val iframeDoc = if (firstResponse.text.contains("Lütfen bekleyiniz")) {
                val timestamp = System.currentTimeMillis() / 1000
                Thread.sleep(1000)
                app.get(
                    "${cevap.api_iframe}?t=$timestamp",
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                        "Referer" to "${mainUrl}/"
                    ),
                    cookies = cookies
                ).document
            } else {
                firstResponse.document
            }
            val iframe = iframeDoc.selectFirst("iframe")?.attr("src").toString()
//            Log.d("kraptor_$name", "iframe = ${iframe}")
            loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        }


      document.select("a.ui.pointing[data-eid]").map { id ->
            val dil = id.text()
            val vLang = if (dil.contains("Dublaj")){
                "tr"
            } else {
                "en"
            }
            val dataEid = id.attr("data-eid")
            val postIstek = app.post("${mainUrl}/ajax/service", data = mapOf(
                "e_id" to dataEid,
                "v_lang" to vLang,
                "type" to "get_whatwehave"
            ), headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "${mainUrl}/"
            )
                , cookies = mapOf(
                "udys"  to "1760709729873",
                "level" to "1"
            )).text
//          Log.d("kraptor_$name", "postIstek = ${postIstek}")
           val mapper = jacksonObjectMapper().registerKotlinModule()
          val cevap: Service = mapper.readValue(postIstek)
          val firstResponse = app.get(
              cevap.api_iframe.toString(),
              headers = mapOf(
                  "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                  "Referer" to "${mainUrl}/"
              ),
              cookies = mapOf(
                  "udys" to "1760709729873",
                  "level" to "1"
              )
          )

          val cookies = firstResponse.cookies.toMutableMap().apply {
              put("udys", "1760709729873")
              put("level", "1")
          }
          val cfyCookie = firstResponse.cookies["_cfy"]
          if (cfyCookie != null) {
              cookies["_cfy"] = cfyCookie
          }

          val iframeDoc = if (firstResponse.text.contains("Lütfen bekleyiniz")) {
              val timestamp = System.currentTimeMillis() / 1000
              Thread.sleep(1000)
              app.get(
                  "${cevap.api_iframe}?t=$timestamp",
                  headers = mapOf(
                      "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                      "Referer" to "${mainUrl}/"
                  ),
                  cookies = cookies
              ).document
          } else {
              firstResponse.document
          }
           val iframe = iframeDoc.selectFirst("iframe")?.attr("src").toString()
//           Log.d("kraptor_$name", "iframe = ${iframe}")
           loadExtractor("$iframe|$dil", "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Service(
    val api: String?,
    val api_iframe: String?,
    val vs_id: String?,
    val attr: String?,
    val hash: String?,
    val image: String?,
    val success: Int?,
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonResponse(
    val success: Int,
    val data: Data,
    val type: String?
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Data(
    val result: List<ResultItem>,
    val type: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResultItem(
    val s_type: String,
    val s_link: String,
    val s_name: String,
    val s_image: String,
    val s_year: String
)