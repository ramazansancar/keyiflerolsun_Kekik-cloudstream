// ! Bu araç @kerimmkirac tarafından | @kerimmkirac için yazılmıştır.

package com.kerimmkirac

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class filmIzleIlk : MainAPI() {
    override var mainUrl = "https://www.filmizleilk.com"
    override var name = "Filmizleilk"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl" to "Yeni Eklenenler",
        "$mainUrl/dizi-arsivi" to "Diziler",
        "$mainUrl/film-arsivi" to "Filmler",
        "$mainUrl/film/netflix-filmleri" to "Netflix Filmleri",
        "$mainUrl/dizi-kategori/netflix-dizileri" to "Netflix Dizileri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("${request.data}/page/$page").document

        val allItems = if (request.name == "Yeni Eklenenler") {
           
            doc.select("div.ep-box")
                .mapNotNull { it.toSearchResultSmart() }
        } else {
           
            doc.select("div.series-box, div.film-box, div.ep-box")
                .mapNotNull { it.toSearchResultSmart() }
        }

        return newHomePageResponse(request.name, allItems)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document

        return doc.select("div.series-box, div.film-box")
            .mapNotNull { it.toSearchResultSmart() }
    }

    private suspend fun Element.toSearchResultSmart(): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val href = fixUrlNull(aTag.attr("href")) ?: return null
        val title = this.selectFirst("div.name a")?.attr("title")
            ?: this.selectFirst("img")?.attr("alt")
            ?: return null
        val poster = fixUrlNull(this.selectFirst("img")?.attr("src"))

        val type = if ("/dizi/" in href || "/bolum/" in href) TvType.TvSeries else TvType.Movie
        
        
        val finalHref = convertEpisodeToSeriesUrl(href) 


        return newMovieSearchResponse(title.trim(), finalHref, type) {
            this.posterUrl = poster
        }
    }

    
    private suspend fun convertEpisodeToSeriesUrl(url: String): String {
    return if ("/bolum/" in url) {
        try {
            val doc = app.get(url).document
            val similarLink = doc.selectFirst("#similar-movies li a")?.attr("href")
            if (!similarLink.isNullOrEmpty() && "/dizi/" in similarLink) {
                return fixUrl(similarLink)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        
        val regex = Regex("""/bolum/(.+?)-\d+-bolum""")
        val match = regex.find(url)
        if (match != null) {
            val seriesName = match.groupValues[1]
            "$mainUrl/dizi/$seriesName/"
        } else {
            url.replace("/bolum/", "/dizi/").replace(Regex("""-\d+-bolum/?"""), "/")
        }
    } else {
        url
    }
}


    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(doc.selectFirst("div.poster img")?.attr("src"))
        val description = doc.selectFirst("div.description")?.text()?.trim()
        val year = doc.selectFirst("li.release a")?.text()?.toIntOrNull()
        val tags = doc.select("div.category a").map { it.text().trim() }
        
        
        val actors = doc.select("div.actors a").map { 
            ActorData(actor = Actor(it.text().trim())) 
        }

        val isSeries = "/dizi/" in url

        return if (isSeries) {
            val episodes = mutableListOf<Episode>()
            
            
            val seasonWrappers = doc.select("div.s-wrap")
            
            seasonWrappers.forEach { seasonWrapper ->
                val seasonId = seasonWrapper.attr("id") 
                val seasonNumber = seasonId.replace("s-", "").toIntOrNull() ?: 1
                
                
                val episodeElements = seasonWrapper.select("div.ep-box")
                
                episodeElements.forEach { ep ->
                    val link = fixUrlNull(ep.selectFirst("a")?.attr("href")) ?: return@forEach
                    val episodeTitle = ep.selectFirst(".episodetitle")?.text()?.trim() ?: return@forEach
                    val serieTitle = ep.selectFirst(".serietitle")?.text()?.trim() ?: title
                    val thumbnail = fixUrlNull(ep.selectFirst("img")?.attr("src"))
                    
                    
                    val episodeRegex = Regex("""(\d+)\.\s*Sezon.*?(\d+)\.\s*Bölüm""")
                    val match = episodeRegex.find(episodeTitle)
                    
                    val season = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: seasonNumber
                    val episodeNum = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
                    
                   
                    val epName = "$season. Sezon $episodeNum. Bölüm"
                    
                    episodes.add(
                        Episode(
                            data = link,
                            name = epName,
                            season = season,
                            episode = episodeNum
                        ).apply {
                            this.posterUrl = thumbnail
                        }
                    )
                }
            }

            
            if (episodes.isEmpty()) {
                val fallbackEpisodes = doc.select("div.ep-box").mapNotNull { ep ->
                    val link = fixUrlNull(ep.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    val name = ep.selectFirst(".episodetitle")?.text()?.trim() ?: return@mapNotNull null
                    val seasonEpisode = Regex("""(\d+)\.\s*Sezon.*?(\d+)\.\s*Bölüm""").find(name)
                    val season = seasonEpisode?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                    val episodeNum = seasonEpisode?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
                    val epTitle = ep.selectFirst(".serietitle")?.text()?.trim() ?: name
                    val thumbnail = fixUrlNull(ep.selectFirst("img")?.attr("src"))

                    Episode(link, epTitle, season, episodeNum).apply {
                        this.posterUrl = thumbnail
                    }
                }
                episodes.addAll(fallbackEpisodes)
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.actors = actors 
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.actors = actors 
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("a img")?.attr("alt") ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val poster = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Filmizleilk", "loadLinks başladı, data: $data")

        try {
            
            val doc = app.get(data).document
            Log.d("Filmizleilk", "Sayfa alındı")
            
            
            val iframe = doc.selectFirst("div.video-content iframe")?.attr("src")
            if (iframe.isNullOrEmpty()) {
                Log.d("Filmizleilk", "İframe bulunamadı")
                return false
            }
            
            Log.d("Filmizleilk", "İframe bulundu: $iframe")
            
           
            val dataParam = extractDataParam(iframe)
            if (dataParam.isNullOrEmpty()) {
                Log.d("Filmizleilk", "Data parametresi bulunamadı")
                return false
            }
            
            Log.d("Filmizleilk", "Data parametresi çıkarıldı: $dataParam")
            
            
            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36",
                "Accept" to "*/*",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Origin" to "https://filmdefilm.xyz",
                "Referer" to iframe,
                "Accept-Language" to "tr-TR,tr;q=0.6",
                "sec-ch-ua-platform" to "Android",
                "sec-ch-ua" to "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"138\", \"Brave\";v=\"138\"",
                "sec-ch-ua-mobile" to "?1",
                "sec-gpc" to "1",
                "sec-fetch-site" to "same-origin",
                "sec-fetch-mode" to "cors",
                "sec-fetch-dest" to "empty",
                "priority" to "u=1, i"
            )
            
            
            val cookies = mapOf(
                "fireplayer_player" to "psmom222u8422831i5u813lcah"
            )
            
            // POST URL'i oluştur
            val videoUrl = "https://filmdefilm.xyz/player/index.php?data=${dataParam}&do=getVideo"
            Log.d("Filmizleilk", "POST URL: $videoUrl")
            
            
            val response = app.post(
                videoUrl,
                headers = headers,
                cookies = cookies
            )
            
            Log.d("Filmizleilk", "POST Response Status: ${response.code}")
            Log.d("Filmizleilk", "POST Response Body: ${response.text}")
            
            
            val jsonResponse = response.parsed<VideoResponse>()
            Log.d("Filmizleilk", "JSON Parse edildi")
            Log.d("Filmizleilk", "securedLink: ${jsonResponse.securedLink}")
            Log.d("Filmizleilk", "videoSource: ${jsonResponse.videoSource}")
            Log.d("Filmizleilk", "hls: ${jsonResponse.hls}")
            
            
           jsonResponse.securedLink?.let { securedLink ->
    Log.d("Filmizleilk", "newExtractorLink oluşturuluyor (securedLink): $securedLink")
    callback.invoke(
        newExtractorLink(
            name = "Filmizleilk (Ana)",
            source = name,
            url = securedLink,
            type = ExtractorLinkType.M3U8
        ) {
            this.referer = "https://filmdefilm.xyz/"
            this.quality = Qualities.Unknown.value
        }
    )
}


jsonResponse.videoSource?.let { videoSource ->
    Log.d("Filmizleilk", "ExtractorLink oluşturuluyor (videoSource): $videoSource")
    callback.invoke(
        newExtractorLink(
            name = "Filmizleilk (Yedek)",
            source = name,
            url = videoSource,
            type = ExtractorLinkType.M3U8
        ) {
            this.referer = "https://filmdefilm.xyz/"
            this.quality = Qualities.Unknown.value
        }
    )
}

            
            
            if (jsonResponse.securedLink != null || jsonResponse.videoSource != null) {
                Log.d("Filmizleilk", "En az bir ExtractorLink oluşturuldu")
                return true
            }
            
            Log.d("Filmizleilk", "securedLink null")
            return false
            
        } catch (e: Exception) {
            Log.e("Filmizleilk", "loadLinks hatası: ${e.message}")
            Log.e("Filmizleilk", "Stack trace: ${e.printStackTrace()}")
            return false
        }
    }

    private fun extractDataParam(iframeUrl: String): String? {
        return try {
            Log.d("Filmizleilk", "extractDataParam - input: $iframeUrl")
            
            
            val videoPattern = Regex("""/video/([a-f0-9]+)""")
            val videoMatch = videoPattern.find(iframeUrl)
            
            if (videoMatch != null) {
                val result = videoMatch.groupValues[1]
                Log.d("Filmizleilk", "extractDataParam - video pattern result: $result")
                return result
            }
            
            
            val dataPattern = Regex("""[?&]data=([a-f0-9]+)""")
            val dataMatch = dataPattern.find(iframeUrl)
            
            if (dataMatch != null) {
                val result = dataMatch.groupValues[1]
                Log.d("Filmizleilk", "extractDataParam - data pattern result: $result")
                return result
            }
            
            Log.d("Filmizleilk", "extractDataParam - no pattern matched")
            null
            
        } catch (e: Exception) {
            Log.e("Filmizleilk", "extractDataParam hatası: ${e.message}")
            null
        }
    }

    
    data class VideoResponse(
        val hls: Boolean?,
        val videoImage: String?,
        val videoSource: String?,
        val securedLink: String?,
        val downloadLinks: List<String>?,
        val attachmentLinks: List<String>?,
        val ck: String?
    )
}
