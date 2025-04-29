package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.*
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
        val document = app.get(request.data + "/page/" + page).document
        val items = document.select("a:has(img)").mapNotNull {
            runCatching {
                val title = it.attr("title") ?: it.selectFirst("img")?.attr("alt")
                val link = fixUrl(it.attr("href"))
                val poster = it.selectFirst("img")?.attr("src")
                if (title.isNullOrBlank() || link.isNullOrBlank() || poster.isNullOrBlank()) null
                else TvSeriesSearchResponse(title, link, fixUrl(poster), TvType.TvSeries)
            }.getOrNull()
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=" + query
        val document = app.get(url).document
        return document.select("a:has(img)").mapNotNull {
            runCatching {
                val title = it.attr("title") ?: it.selectFirst("img")?.attr("alt")
                val link = fixUrl(it.attr("href"))
                val poster = it.selectFirst("img")?.attr("src")
                if (title.isNullOrBlank() || link.isNullOrBlank() || poster.isNullOrBlank()) null
                else TvSeriesSearchResponse(title, link, fixUrl(poster), TvType.TvSeries)
            }.getOrNull()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content") ?: "Bölüm"
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")

        val seasons = mutableMapOf<Int, MutableList<Episode>>()

        document.select("a[href*=/bolum-], a[href*=/sezon-]").forEach {
            val epLink = fixUrl(it.attr("href"))
            val epTitle = it.attr("title") ?: it.text()
            val seasonNum = Regex("""([0-9]+)\.sezon""", RegexOption.IGNORE_CASE).find(it.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
            val episode = Episode(epLink, epTitle)
            seasons.getOrPut(seasonNum) { mutableListOf() }.add(episode)
        }

        val episodeList = seasons.toSortedMap().flatMap { it.value }

        return TvSeriesLoadResponse(
            posterUrl = poster,
            episodes = episodeList,
            type = TvType.TvSeries,
        )
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit ): Boolean {
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
                                source = this.name,
                                name = "M3U8 Player",
                                url = videoUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                quality = Qualities.P720.value
                                headers = mapOf("Referer" to iframeUrl)
                            }
                        )
                    } else if (videoUrl != null) {
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "MP4 Player",
                                url = videoUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                quality = Qualities.P720.value
                                headers = mapOf("Referer" to iframeUrl)
                            }
                        )
                    } else {
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "Embed Player",
                                url = iframeUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                quality = Qualities.Unknown.value
                                headers = mapOf("Referer" to mainUrl)
                            }
                        )
                    }

                    val subtitleFormats = Regex("""(vtt|srt|ass)""").findAll(iframeHtml).map { it.value }.toList()
                    }
                }
            }
        }
    }
