package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.*
import org.jsoup.Jsoup


class TRasyalog : MainAPI() {
    override var mainUrl        = "https://asyalog.com"
    override var name           = "TRasyalog"
    override val hasMainPage    = true
    override var lang           = "tr"
    override val hasQuickSearch = false
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
        val home     = document.select("div.post-container").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.thumbnail img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/search?name=${query}").document

        return document.select("div.post-container").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

override suspend fun load(url: String): LoadResponse? {
    val document = app.get(url).document

    val title       = document.selectFirst("h1")?.text()?.trim() ?: return null
    val poster      = fixUrlNull(document.selectFirst("div.aligncenter img")?.attr("src"))
    val description = document.selectFirst("div.entry-content > p")?.text()?.trim()
    val tags        = document.select("div.post-meta a[href*='/category/']").map { it.text() }

    val episodeses = mutableListOf<Episode>()

    for (bolum in document.select("div.entry-content a[href*='-bolum']")) {
        val epHref = fixUrlNull(bolum.attr("href")) ?: continue
        val epName = bolum.text()?.trim() ?: continue
        val epEpisode = epName.replace(Regex("Bölüm\\s*(\\d+).*"), "$1").trim().toIntOrNull()

        val newEpisode = newEpisode(epHref) {
            this.name = epName
            this.episode = epEpisode
        }
        episodeses.add(newEpisode)
    }

    return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
        this.posterUrl = poster
        this.plot = description
        this.tags = tags
    }
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    Log.d("TRAS", "data » $data")
    val document = app.get(data).document

    // 1. video_source içeren <script> etiketi
    val scriptContent = document.select("script").firstOrNull {
        it.html().contains("video_source")
    }?.html() ?: return false

    // 2. video_source içindeki JSON array’i çek
    val videoSourceJson = Regex("""video_source\s*=\s*`(\[.*?])`""", RegexOption.DOT_MATCHES_ALL)
        .find(scriptContent)
        ?.groups?.get(1)
        ?.value
        ?: return false

    val videoSourceArray = JSONArray(videoSourceJson)

    // 3. Her bir API URL'sine istek at
    for (i in 0 until videoSourceArray.length()) {
        val source = videoSourceArray.getJSONObject(i)
        val apiUrl = source.getString("url")
        Log.d("TRAS", "apiUrl » $apiUrl")

        // 4. API sayfasını çek
        val apiHtml = app.get(apiUrl, headers = mapOf("Referer" to "https://trTRASmaci.com/")).text
        val apiDoc = Jsoup.parse(apiHtml)
        Log.d("TRAS", "apiDoc » $apiDoc")

        // 5. const sources = [...] içeren <script> bul
        val sourcesScript = apiDoc.select("script").firstOrNull {
            it.html().contains("const sources")
        } ?: continue

        val sourcesArrayRaw = Regex("""const\s+sources\s*=\s*(\[[\s\S]*?])\s*;""")
            .find(sourcesScript.html())
            ?.groups?.get(1)
            ?.value
            ?: continue

        // 6. MP4 linklerini JSON olarak parse et
        val mp4Array = JSONArray(sourcesArrayRaw)
        Log.d("TRAS", "mp4Array » $mp4Array")

        for (j in 0 until mp4Array.length()) {
            val mp4 = mp4Array.getJSONObject(j)
            val videoUrl = mp4.getString("src")
            val quality = mp4.optInt("size", Qualities.Unknown.value)

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "${this.name} - ${quality}p",
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://api.TvSeriesuzayi.com/"
                    this.quality = quality
                }
            )
        }
    }

    return true
}
}
