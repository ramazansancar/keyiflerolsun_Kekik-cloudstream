package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

class TRasyalog : MainAPI() {
    override var name = "TRasyalog"
    override var mainUrl = "https://asyalog.com"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/category/yeni-eklenen-bolumler/" to "Yeni Eklenen Bölümler",
        "${mainUrl}/category/final-yapan-diziler/" to "Final Yapan Diziler",
        "${mainUrl}/category/kore-dizileri-izle-guncel/" to "Kore Dizileri",
        "${mainUrl}/category/cin-dizileri/" to "Çin Dizileri",
        "${mainUrl}/category/tayland-dizileri/" to "TaylandDizileri",
        "${mainUrl}/category/japon-dizileri/" to "Japon Diziler",
        "${mainUrl}/category/endonezya-dizileri/" to "Endonezya Diziler",
        "${mainUrl}/category/seri-diziler/" to "Seri Diziler",
        "${mainUrl}/category/devam-eden-diziler/" to "Devam eden Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page").document
        val items = document.select("a:has(img)").mapNotNull {
            runCatching {
                val title = it.attr("title") ?: it.selectFirst("img")?.attr("alt")
                val link = fixUrl(it.attr("href"))
                val poster = it.selectFirst("img")?.attr("src")
                if (title.isNullOrBlank() || link.isNullOrBlank() || poster.isNullOrBlank()) null
                else newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
            }.getOrNull()
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("a:has(img)").mapNotNull {
            runCatching {
                val title = it.attr("title") ?: it.selectFirst("img")?.attr("alt")
                val link = fixUrl(it.attr("href"))
                val poster = it.selectFirst("img")?.attr("src")
                if (title.isNullOrBlank() || link.isNullOrBlank() || poster.isNullOrBlank()) null
                else newTvSeriesSearchResponse(title, link, TvType.TvSeries, fixUrl(poster))
            }.getOrNull()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content") ?: "Bölüm"
        val posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = document.selectFirst("div.entry-content p")?.text()

        val seasons = mutableMapOf<Int, MutableList<Episode>>()

        document.select("a[href*=/bolum-], a[href*=/sezon-]").forEach {
            val epLink = fixUrl(it.attr("href"))
            val epTitle = it.attr("title") ?: it.text()
            val epEpisode = Episode(epLink, epTitle)
            val seasonNum = Regex("""([0-9]+)\.sezon""", RegexOption.IGNORE_CASE)
                .find(it.text())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@forEach
            seasons.getOrPut(seasonNum) { mutableListOf() }.add(epEpisode)
        }

        val episodes = seasons.toSortedMap().flatMap { it.value }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = posterUrl
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("iframe").apmap {
            runCatching {
                val iframeUrl = fixUrl(it.attr("src"))
                if (iframeUrl.isNotBlank()) {
                    val iframeHtml = app.get(iframeUrl, referer = mainUrl).text
                    val videoUrl = Regex("""https?:[^"']+\.(m3u8|mp4)""").find(iframeHtml)?.value

                    if (videoUrl != null) {
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = if (videoUrl.endsWith(".m3u8")) "M3U8 Player" else "MP4 Player",
                                url = videoUrl,
                                type = if (videoUrl.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                quality = Qualities.P720.value
                                headers = mapOf("Referer" to iframeUrl)
                            }
                        )
                    } else {
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "Embed Player",
                                url = iframeUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                quality = Qualities.Unknown.value
                                headers = mapOf("Referer" to mainUrl)
                            }
                        )
                    }

                    val subtitleFormats = Regex("""(vtt|srt|ass)""")
                        .findAll(iframeHtml)
                        .map { it.value }
                        .toList()

                    // (isteğe bağlı: altyazı işlemleri burada yapılabilir)
                }
            }
        }
        return true
    }
}
