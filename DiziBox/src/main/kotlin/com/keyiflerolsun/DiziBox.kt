// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziBox : MainAPI() {
    override var mainUrl              = "https://www.dizibox.live"
    override var name                 = "DiziBox"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    // ! CloudFlare bypass
    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 50L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 50L  // ? 0.05 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.text().contains("Güvenlik taramasından geçiriliyorsunuz. Lütfen bekleyiniz..")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler/page/SAYFA/?tip=populer"               to "Popüler Dizilerden Son Bölümler",
        "${mainUrl}/tum-bolumler/page/SAYFA/"                           to "Yeni Eklenen Bölümler",
        "${mainUrl}/dizi-arsivi/page/SAYFA/"                            to "Dizi Arşivi",

        "${mainUrl}/dizi-arsivi/page/SAYFA/?ulke[]=amerikan&yil=&imdb"  to "Yabancı",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?ulke[]=turkiye&yil=&imdb"   to "Yerli",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=aile&yil&imdb"       to "Aile",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=aksiyon&yil&imdb"    to "Aksiyon",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=animasyon&yil&imdb"  to "Animasyon",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=belgesel&yil&imdb"   to "Belgesel",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=bilimkurgu&yil&imdb" to "Bilimkurgu",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=biyografi&yil&imdb"  to "Biyografi",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=dram&yil&imdb"       to "Dram",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=drama&yil&imdb"      to "Drama",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=fantastik&yil&imdb"  to "Fantastik",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=gerilim&yil&imdb"    to "Gerilim",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=gizem&yil&imdb"      to "Gizem",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=komedi&yil&imdb"     to "Komedi",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=korku&yil&imdb"      to "Korku",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=macera&yil&imdb"     to "Macera",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=muzik&yil&imdb"      to "Müzik",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=muzikal&yil&imdb"    to "Müzikal",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=reality-tv&yil&imdb" to "Reality TV",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=romantik&yil&imdb"   to "Romantik",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=savas&yil&imdb"      to "Savaş",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=spor&yil&imdb"       to "Spor",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=suc&yil&imdb"        to "Suç",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=tarih&yil&imdb"      to "Tarih",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=western&yil&imdb"    to "Western",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=yarisma&yil&imdb"    to "Yarışma",

        /* Ülkeler - Çok fazla istek yapıldığı için sayfaların getirilmesinde sorun olabilir. O yüzden sadece belli ülkeler aktif edildi. */

        "${mainUrl}/ulke/amerika/page/SAYFA/"                           to "Ülke: ABD",
        "${mainUrl}/ulke/almanya/page/SAYFA/"                           to "Ülke: Almanya",
        // ! "${mainUrl}/ulke/arjantin/page/SAYFA/"                          to "Ülke: Arjantin",
        // ! "${mainUrl}/ulke/avustralya/page/SAYFA/"                        to "Ülke: Avustralya",
        // ! "${mainUrl}/ulke/avusturya/page/SAYFA/"                         to "Ülke: Avusturya",
        // ! "${mainUrl}/ulke/bae/page/SAYFA/"                               to "Ülke: BAE",
        // ! "${mainUrl}/ulke/belcika/page/SAYFA/"                           to "Ülke: Belçika",
        // ! "${mainUrl}/ulke/belgesel/page/SAYFA/"                          to "Ülke: Belgesel",
        // ! "${mainUrl}/ulke/birlesik-arap-emirlikleri/page/SAYFA/"         to "Ülke: Birleşik Arap Emirlikleri",
        // ! "${mainUrl}/ulke/biyografi/page/SAYFA/"                         to "Ülke: Biyografi",
        // ! "${mainUrl}/ulke/brezilya/page/SAYFA/"                          to "Ülke: Brezilya",
        // ! "${mainUrl}/ulke/bulgaristan/page/SAYFA/"                       to "Ülke: Bulgaristan",
        // ! "${mainUrl}/ulke/cekya/page/SAYFA/"                             to "Ülke: Çekya",
        // ! "${mainUrl}/ulke/cin/page/SAYFA/"                               to "Ülke: Çin",
        // ! "${mainUrl}/ulke/danimarka/page/SAYFA/"                         to "Ülke: Danimarka",
        // ! "${mainUrl}/ulke/endonezya/page/SAYFA/"                         to "Ülke: Endonezya",
        // ! "${mainUrl}/ulke/estonya/page/SAYFA/"                           to "Ülke: Estonya",
        // ! "${mainUrl}/ulke/filipinler/page/SAYFA/"                        to "Ülke: Filipinler",
        // ! "${mainUrl}/ulke/finlandiya/page/SAYFA/"                        to "Ülke: Finlandiya",
        // ! "${mainUrl}/ulke/fransa/page/SAYFA/"                            to "Ülke: Fransa",
        // ! "${mainUrl}/ulke/guney-afrika/page/SAYFA/"                      to "Ülke: Güney Afrika",
        // ! "${mainUrl}/ulke/guney-kore/page/SAYFA/"                        to "Ülke: Güney Kore",
        // ! "${mainUrl}/ulke/hindistan/page/SAYFA/"                         to "Ülke: Hindistan",
        // ! "${mainUrl}/ulke/hirvatistan/page/SAYFA/"                       to "Ülke: Hırvatistan",
        // ! "${mainUrl}/ulke/hollanda/page/SAYFA/"                          to "Ülke: Hollanda",
        // ! "${mainUrl}/ulke/ingil/page/SAYFA/"                             to "Ülke: ingil",
        // ! "${mainUrl}/ulke/ingiltere/page/SAYFA/"                         to "Ülke: İngiltere",
        // ! "${mainUrl}/ulke/iralanda/page/SAYFA/"                          to "Ülke: İralanda",
        // ! "${mainUrl}/ulke/irlanda/page/SAYFA/"                           to "Ülke: İrlanda",
        // ! "${mainUrl}/ulke/ispanya/page/SAYFA/"                           to "Ülke: İspanya",
        // ! "${mainUrl}/ulke/israil/page/SAYFA/"                            to "Ülke: İsrail",
        // ! "${mainUrl}/ulke/isvec/page/SAYFA/"                             to "Ülke: İsveç",
        // ! "${mainUrl}/ulke/isvicre/page/SAYFA/"                           to "Ülke: İsviçre",
        // ! "${mainUrl}/ulke/italya/page/SAYFA/"                            to "Ülke: İtalya",
        // ! "${mainUrl}/ulke/izlanda/page/SAYFA/"                           to "Ülke: İzlanda",
        // ! "${mainUrl}/ulke/japonya/page/SAYFA/"                           to "Ülke: Japonya",
        // ! "${mainUrl}/ulke/kanad/page/SAYFA/"                             to "Ülke: Kanad",
        // ! "${mainUrl}/ulke/kanada/page/SAYFA/"                            to "Ülke: Kanada",
        // ! "${mainUrl}/ulke/kenya/page/SAYFA/"                             to "Ülke: Kenya",
        // ! "${mainUrl}/ulke/kolombiya/page/SAYFA/"                         to "Ülke: Kolombiya",
        // ! "${mainUrl}/ulke/kuveyt/page/SAYFA/"                            to "Ülke: Kuveyt",
        // ! "${mainUrl}/ulke/kuzey-kore/page/SAYFA/"                        to "Ülke: Kuzey Kore",
        // ! "${mainUrl}/ulke/letonya/page/SAYFA/"                           to "Ülke: Letonya",
        // ! "${mainUrl}/ulke/lubnan/page/SAYFA/"                            to "Ülke: Lübnan",
        // ! "${mainUrl}/ulke/luksemburg/page/SAYFA/"                        to "Ülke: Lüksemburg",
        // ! "${mainUrl}/ulke/macaristan/page/SAYFA/"                        to "Ülke: Macaristan",
        // ! "${mainUrl}/ulke/malta/page/SAYFA/"                             to "Ülke: Malta",
        // ! "${mainUrl}/ulke/meksika/page/SAYFA/"                           to "Ülke: Meksika",
        // ! "${mainUrl}/ulke/misir/page/SAYFA/"                             to "Ülke: Mısır",
        // ! "${mainUrl}/ulke/nijerya/page/SAYFA/"                           to "Ülke: Nijerya",
        // ! "${mainUrl}/ulke/norvec/page/SAYFA/"                            to "Ülke: Norveç",
        // ! "${mainUrl}/ulke/peru/page/SAYFA/"                              to "Ülke: Peru",
        // ! "${mainUrl}/ulke/polonya/page/SAYFA/"                           to "Ülke: Polonya",
        // ! "${mainUrl}/ulke/portekiz/page/SAYFA/"                          to "Ülke: Portekiz",
        // ! "${mainUrl}/ulke/romanya/page/SAYFA/"                           to "Ülke: Romanya",
        // ! "${mainUrl}/ulke/rusya/page/SAYFA/"                             to "Ülke: Rusya",
        // ! "${mainUrl}/ulke/sili/page/SAYFA/"                              to "Ülke: Şili",
        // ! "${mainUrl}/ulke/singapur/page/SAYFA/"                          to "Ülke: Singapur",
        // ! "${mainUrl}/ulke/sirbistan/page/SAYFA/"                         to "Ülke: Sırbistan",
        // ! "${mainUrl}/ulke/suudi-arabistan/page/SAYFA/"                   to "Ülke: Suudi Arabistan",
        // ! "${mainUrl}/ulke/tayland/page/SAYFA/"                           to "Ülke: Tayland",
        // ! "${mainUrl}/ulke/tayvan/page/SAYFA/"                            to "Ülke: Tayvan",
        "${mainUrl}/ulke/turkiye/page/SAYFA/"                           to "Ülke: Türkiye",
        // ! "${mainUrl}/ulke/urdun/page/SAYFA/"                             to "Ülke: Ürdün",
        // ! "${mainUrl}/ulke/yeni-zelanda/page/SAYFA/"                      to "Ülke: Yeni Zelanda",
        // ! "${mainUrl}/ulke/yunanistan/page/SAYFA/"                        to "Ülke: Yunanistan",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = request.data.replace("SAYFA", "$page")
        val document = app.get(
            url,
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1722403730363"
            ),
            interceptor = interceptor
        ).document
        val home     = document.select("article").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src")) ?: fixUrlNull(this.selectFirst("img")?.attr("data-src")) ?: return null

        Log.d("DZBX", "toMainPageResult - Title: ${title}, Href: ${href}, Poster: ${posterUrl}")

        if (title == null || href == null) {
            Log.w("DZBX", "toMainPageResult - Article eksik veri içeriyor, atlanıyor!")
            return null
        }
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private suspend fun Element.sonBolumler(): SearchResponse? {
        val name = this.selectFirst("b.series-name")?.text() ?: ""
        val szn = this.selectFirst("span.season")?.text()?.replace(".SEZON", "") ?: ""
        val ep = this.selectFirst("b.episode")?.text()?.replace(".BÖLÜM", "") ?: ""
        val epName = "${szn}x$ep"

        val title = "$name - $epName"

        val epDoc = fixUrlNull(this.selectFirst("a")?.attr("href"))?.let { app.get(it).document }

        val href = fixUrlNull(epDoc?.selectFirst("a.archive-title")?.attr("href")) ?: return null

        val posterUrl = fixUrlNull(epDoc?.selectFirst("img.small-thumbnail")?.attr("src"))?.replace("50x50","200x290")

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get(
            "${mainUrl}/?s=${query}",
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1722403730363"
            ),
            interceptor = interceptor
        ).document

        return document.select("article.detailed-article").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(
            url,
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1722403730363"
            ),
            interceptor = interceptor
        ).document

        val title       = document.selectFirst("div.tv-overview h1 a")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.tv-overview figure img")?.attr("src"))
        val description = document.selectFirst("div.tv-story p")?.text()?.trim()
        val year        = document.selectFirst("a[href*='/yil/']")?.text()?.trim()?.toIntOrNull()
        val tags        = document.select("a[href*='/tur/']").map { it.text() }
        val rating      = document.selectFirst("span.label-imdb b")?.text()?.trim()?.toIntOrNull()
        val actors      = document.select("a[href*='/oyuncu/']").map { Actor(it.text()) }
        val trailer     = document.selectFirst("div.tv-overview iframe")?.attr("src")

        val episodeList = mutableListOf<Episode>()
        document.select("div#seasons-list a").forEach {
            val epUrl = fixUrlNull(it.attr("href")) ?: return@forEach
            val epDoc = app.get(
                epUrl,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1722403730363"
                ),
                interceptor = interceptor
            ).document

            epDoc.select("article.grid-box").forEach ep@ { epElem ->
                val epTitle   = epElem.selectFirst("div.post-title a")?.text()?.trim() ?: return@ep
                val epHref    = fixUrlNull(epElem.selectFirst("div.post-title a")?.attr("href")) ?: return@ep
                val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()

                episodeList.add(newEpisode(epHref) {
                    this.name = epTitle
                    this.season = epSeason
                    this.episode = epEpisode
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            this.rating = rating
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private suspend fun iframeDecode(data:String, iframe:String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        @Suppress("NAME_SHADOWING") var iframe = iframe

        if (iframe.contains("/player/king/king.php")) {
            iframe = iframe.replace("king.php?v=", "king.php?wmode=opaque&v=")
            val subDoc = app.get(
                iframe,
                referer     = data,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1722403730363"
                ),
                interceptor = interceptor
            ).document
            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            val iDoc          = app.get(subFrame, referer="${mainUrl}/").text
            val cryptData     = Regex("""CryptoJS\.AES\.decrypt\("(.*)","""").find(iDoc)?.groupValues?.get(1) ?: return false
            val cryptPass     = Regex("""","(.*)"\);""").find(iDoc)?.groupValues?.get(1) ?: return false
            val decryptedData = CryptoJS.decrypt(cryptPass, cryptData)
            val decryptedDoc  = Jsoup.parse(decryptedData)
            val vidUrl        = Regex("""file: '(.*)',""").find(decryptedDoc.html())?.groupValues?.get(1) ?: return false

            callback.invoke(
                ExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = vidUrl,
                    referer = vidUrl,
                    quality = getQualityFromName("4k"),
                    type    = ExtractorLinkType.M3U8
                )
            )

        } else if (iframe.contains("/player/moly/moly.php")) {
            iframe = iframe.replace("moly.php?h=", "moly.php?wmode=opaque&h=")
            var subDoc = app.get(
                iframe,
                referer     = data,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1722403730363"
                ),
                interceptor = interceptor
            ).document

            val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
            if (atobData != null) {
                val decodedAtob = atobData.decodeUri()
                val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                subDoc          = Jsoup.parse(strAtob)
            }

            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)

        } else if (iframe.contains("/player/haydi.php")) {
            iframe = iframe.replace("haydi.php?v=", "haydi.php?wmode=opaque&v=")
            var subDoc = app.get(
                iframe,
                referer     = data,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1722403730363"
                ),
                interceptor = interceptor
            ).document

            val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
            if (atobData != null) {
                val decodedAtob = atobData.decodeUri()
                val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                subDoc          = Jsoup.parse(strAtob)
            }

            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZBX", "data » $data")
        val document = app.get(
            data,
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1722403730363"
            ),
            interceptor = interceptor
        ).document
        var iframe = document.selectFirst("div#video-area iframe")?.attr("src")?: return false
        Log.d("DZBX", "iframe » $iframe")

        iframeDecode(data, iframe, subtitleCallback, callback)

        document.select("div.video-toolbar option[value]").forEach {
            val altLink = it.attr("value")
            val subDoc  = app.get(
                altLink,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1722403730363"
                ),
                interceptor = interceptor
            ).document
            iframe = subDoc.selectFirst("div#video-area iframe")?.attr("src")?: return false
            Log.d("DZBX", "iframe » $iframe")

            iframeDecode(data, iframe, subtitleCallback, callback)
        }

        return true
    }
}