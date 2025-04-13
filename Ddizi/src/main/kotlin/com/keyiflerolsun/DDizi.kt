package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class DDizi : MainAPI() {
    override var mainUrl              = "https://www.ddizi.im"
    override var name                 = "DDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/yeni-eklenenler1"      to "Yeni Eklenenler",
        "${mainUrl}/yabanci-dizi-izle"     to "Yabancı Diziler",
        "${mainUrl}/eski.diziler"    to "Eski Diziler"
    )

override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    // Sayfalanmayan kategoriler için yalnızca ilk sayfayı getir
    if (request.name != "Eski Diziler" && page > 1) {
        return newHomePageResponse(request.name, listOf())
    }

    // URL oluştur
    val url = when (request.name) {
        "Eski Diziler" -> if (page == 1) request.data else "${request.data}/$page"
        else -> request.data
    }

    val document = app.get(url).document
    val selector = when (request.name) {
        "Yeni Eklenenler" -> "div.col-lg-12 div.dizi-boxpost-cat"
        else               -> "div.col-lg-3 div.dizi-boxpost"
    }

    val elements = document.select(selector)

    val home = elements.mapNotNull {
        val item = it.diziler()
        if (item == null) println("⚠️ diziler() null döndü.")
        else println("DDZ ✅ dizi bulundu: ${item.name}")
        item
    }

    return newHomePageResponse(request.name, home)
}

private fun Element.diziler(): SearchResponse? {
    val aTag = this.selectFirst("a") ?: return null
    val title = aTag.attr("title").substringBefore(" izle").trim()
    val href = fixUrlNull(aTag.attr("href")) ?: return null
    val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

    return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
        this.posterUrl = posterUrl
    }
}


    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document
        return document.select("div.post").mapNotNull { it.diziler() }
    }

override suspend fun load(url: String): LoadResponse? {
    Log.d("ddz", "load başladı: $url")

    val document = app.get(url).document

    val title = document.selectFirst("title")?.text()?.substringBefore(" izle")
    Log.d("ddz", "title: $title")
    if (title == null) {
        Log.d("ddz", "Title bulunamadı, null dönülüyor")
        return null
    }

    val poster = fixUrlNull(document.selectFirst("div.col-lg-12 div.dizi-boxpost-cat img")?.attr("data-src"))
    Log.d("ddz", "poster: $poster")

    val episodes = document.select("div.col-lg-12 div.dizi-boxpost-cat a").mapNotNull {
        val epName = it.text().trim()
        val epHref = fixUrlNull(it.attr("href"))
        Log.d("ddz", "epName: $epName - epHref: $epHref")

        if (epHref == null) {
            Log.d("ddz", "epHref null, bu episode atlandı")
            return@mapNotNull null
        }

        val epSeason = Regex("""(\d+)\.Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val epEpisode = Regex("""(\d+)\.Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
        Log.d("ddz", "epSeason: $epSeason - epEpisode: $epEpisode")

        newEpisode(epHref) {
            this.name = epName.substringBefore(" izle").replace(title, "").trim()
            this.season = epSeason
            this.episode = epEpisode
        }
    }

    Log.d("ddz", "Toplam episode sayısı: ${episodes.size}")

    return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
        this.posterUrl = poster
        Log.d("ddz", "LoadResponse oluşturuldu")
    }
}

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DDZ", "data » ${data}")

        val document = app.get(data).document
        val iframe   = document.selectFirst("iframe")?.attr("src") ?: return false
        Log.d("DDZ", "iframe » ${iframe}")

        loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)

        return true
    }
} 
