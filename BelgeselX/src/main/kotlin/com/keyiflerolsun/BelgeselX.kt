// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import java.util.Locale
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class BelgeselX : MainAPI() {
    override var mainUrl              = "https://belgeselx.com"
    override var name                 = "BelgeselX"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Documentary)
	
    override val mainPage = mainPageOf(
        "${mainUrl}/konu/turk-tarihi-belgeselleri&page=" to "Türk Tarihi",
        "${mainUrl}/konu/tarih-belgeselleri&page="		 to "Tarih",
        "${mainUrl}/konu/seyehat-belgeselleri&page="	 to "Seyahat",
        "${mainUrl}/konu/seri-belgeseller&page="		 to "Seri",
        "${mainUrl}/konu/savas-belgeselleri&page="		 to "Savaş",
        "${mainUrl}/konu/sanat-belgeselleri&page="		 to "Sanat",
        "${mainUrl}/konu/psikoloji-belgeselleri&page="	 to "Psikoloji",
        "${mainUrl}/konu/polisiye-belgeselleri&page="	 to "Polisiye",
        "${mainUrl}/konu/otomobil-belgeselleri&page="	 to "Otomobil",
        "${mainUrl}/konu/nazi-belgeselleri&page="		 to "Nazi",
        "${mainUrl}/konu/muhendislik-belgeselleri&page=" to "Mühendislik",
        "${mainUrl}/konu/kultur-din-belgeselleri&page="	 to "Kültür Din",
        "${mainUrl}/konu/kozmik-belgeseller&page="		 to "Kozmik",
        "${mainUrl}/konu/hayvan-belgeselleri&page="		 to "Hayvan",
        "${mainUrl}/konu/eski-tarih-belgeselleri&page="	 to "Eski Tarih",
        "${mainUrl}/konu/egitim-belgeselleri&page="		 to "Eğitim",
        "${mainUrl}/konu/dunya-belgeselleri&page="		 to "Dünya",
        "${mainUrl}/konu/doga-belgeselleri&page="		 to "Doğa",
        "${mainUrl}/konu/bilim-belgeselleri&page="		 to "Bilim"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}", cacheTime = 60).document
        val home     = document.select("div.gen-movie-contain > div.gen-info-contain > div.gen-movie-info").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun String.toTitleCase(): String {
        val locale = Locale("tr", "TR")
        return this.split(" ").joinToString(" ") { word ->
            word.lowercase(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.gen-movie-info > h3 a")?.text()?.trim()?.toTitleCase() ?: return null
        val href      = fixUrlNull(this.selectFirst("div.gen-movie-info > h3 a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.parent()?.parent()?.selectFirst("div.gen-movie-img > img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.Documentary) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cx = "016376594590146270301:iwmy65ijgrm" // ! Might change in the future

        val tokenResponse = app.get("https://cse.google.com/cse.js?cx=${cx}")
        val cseLibVersion = Regex("""cselibVersion": "(.*)"""").find(tokenResponse.text)?.groupValues?.get(1)
        val cseToken      = Regex("""cse_token": "(.*)"""").find(tokenResponse.text)?.groupValues?.get(1)

        val response = app.get("https://cse.google.com/cse/element/v1?rsz=filtered_cse&num=100&hl=tr&source=gcsc&cselibv=${cseLibVersion}&cx=${cx}&q=${query}&safe=off&cse_tok=${cseToken}&sort=&exp=cc%2Capo&oq=${query}&callback=google.search.cse.api9969&rurl=https%3A%2F%2Fbelgeselx.com%2F")
        Log.d("BLX", "response » $response")
        val titles     = Regex(""""titleNoFormatting": "(.*)"""").findAll(response.text).map { it.groupValues[1] }.toList()
        val urls       = Regex(""""url": "(.*)"""").findAll(response.text).map { it.groupValues[1] }.toList()
        val posterUrls = Regex(""""ogImage": "(.*)"""").findAll(response.text).map { it.groupValues[1] }.toList()

        val searchResponses = mutableListOf<TvSeriesSearchResponse>()

        for (i in titles.indices) {
            val title     = titles[i].split("İzle")[0].trim().toTitleCase()
            val url       = urls.getOrNull(i) ?: continue
            val posterUrl = posterUrls.getOrNull(i) ?: continue

        if (url.contains("belgesel")) {
            val modifiedUrl = url.replace("/belgesel/", "/belgeseldizi/")
            searchResponses.add(newTvSeriesSearchResponse(title, modifiedUrl, TvType.Documentary) {
                this.posterUrl = posterUrl
            })
        } else {
            continue
        }
        }
        return searchResponses
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h2.gen-title")?.text()?.trim()?.toTitleCase() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.gen-tv-show-top img")?.attr("src")) ?: return null
        val description = document.selectFirst("div.gen-single-tv-show-info p")?.text()?.trim()
        val tags        = document.select("div.gen-socail-share a[href*='belgeselkanali']").map { it.attr("href").split("/").last().replace("-", " ").toTitleCase() }

        var counter  = 0
        val episodes = document.select("div.gen-movie-contain").mapNotNull {
            val epName     = it.selectFirst("div.gen-movie-info h3 a")?.text()?.trim() ?: return@mapNotNull null
            val epHref     = fixUrlNull(it.selectFirst("div.gen-movie-info h3 a")?.attr("href")) ?: return@mapNotNull null

            val seasonName = it.selectFirst("div.gen-single-meta-holder ul li")?.text()?.trim() ?: ""
            var epEpisode  = Regex("""Bölüm (\d+)""").find(seasonName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val epSeason   = Regex("""Sezon (\d+)""").find(seasonName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            if (epEpisode == 0) {
                epEpisode = counter++
            }

            newEpisode(epHref) {
                this.name    = epName
                this.season  = epSeason
                this.episode = epEpisode
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Documentary, episodes) {
            this.posterUrl = poster
            this.plot      = description
            this.tags      = tags
        }
    }

override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    Log.d("BLX", "data » $data")
    
    val source = app.get(data)

    // Sayfa kaynağından ilk fnc_addWatch içeren div elemanının data-episode numarasını al
    val firstEpisodeId = Regex("""<div[^>]*class=["'][^"']*fnc_addWatch[^"']*["'][^>]*data-episode=["'](\d+)["']""")
        .find(source.text)?.groupValues?.get(1)

    if (firstEpisodeId == null) {
        Log.e("BLX", "İlk fnc_addWatch data-episode bulunamadı.")
        return false
    }

    Log.d("BLX", "İlk fnc_addWatch data-episode: $firstEpisodeId")
    
    // Bu ID ile iframe URL’si oluştur
    val iframeUrl = "https://belgeselx.com/video/data/new4.php?id=$firstEpisodeId"
    Log.d("BLX", "iframeUrl oluşturuldu » $iframeUrl")
    
    // iframe URL’si üzerinden veriyi al
    val alternatifResp = app.get(iframeUrl, referer = data)

    // new4.php içindeki video linklerini parse et
    Regex("""file:"([^"]+)", label: "([^"]+)""").findAll(alternatifResp.text).forEach {
        var thisName = this.name
        val videoUrl = it.groupValues[1]
        var quality = it.groupValues[2]

        if (quality == "FULL") {
            quality = "1080p"
            thisName = "Google"
        }
        // Callback ile video bilgilerini geri gönder
        callback.invoke(
            newExtractorLink(
                source = thisName,
                name = thisName,
                url = videoUrl,
                type = ExtractorLinkType.VIDEO
            ) {
                headers = mapOf("Referer" to data)
                quality = getQualityFromName(quality).toString()
            }
        )
    }

    return true
}
}
