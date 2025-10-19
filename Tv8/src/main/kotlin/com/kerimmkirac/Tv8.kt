// ! Bu araç @kerimmkirac tarafından | @kerimmkirac için yazılmıştır.

package com.kerimmkirac

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class Tv8 : MainAPI() {
    override var mainUrl              = "https://www.tv8.com.tr"
    override var name                 = "Tv8"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)


    private var allContentCache: List<SearchResponse> = emptyList()
    private var cacheTime: Long = 0
    private val cacheValidityDuration = 30 * 60 * 1000 

    override val mainPage = mainPageOf(
        "${mainUrl}"      to "Yarışmalar",
        "${mainUrl}"   to "Diziler",
        "${mainUrl}" to "Programlar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val sections = mutableListOf<HomePageList>()


        val categoryElement = document.select("li.dropdown")
            .firstOrNull { it.selectFirst("a[title]")?.attr("title")?.equals(request.name, ignoreCase = true) == true }
            ?: return newHomePageResponse(emptyList())


        val items = categoryElement.select("ul.clearfix li")
        val results = items.mapNotNull { it.toMainPageResult() }

        if (results.isNotEmpty()) {
            sections.add(HomePageList(request.name, results))
        }

        return newHomePageResponse(sections)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val link = this.selectFirst("a") ?: return null

        val title = link.attr("data-title").ifEmpty { link.text() }.trim()
        val href = fixUrlNull(link.attr("href")) ?: return null
        val poster = "https://img.tv8.com.tr/" + link.attr("data-image")

        return newMovieSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = fixUrlNull(poster)
        }
    }


    private suspend fun getAllContent(): List<SearchResponse> {
        val currentTime = System.currentTimeMillis()


        if (allContentCache.isNotEmpty() && (currentTime - cacheTime) < cacheValidityDuration) {
            Log.d("TV8", "Cache'den içerik döndürülüyor: ${allContentCache.size} öğe")
            return allContentCache
        }

        Log.d("TV8", "Tüm içerikler toplanıyor")
        val allContent = mutableListOf<SearchResponse>()

        try {
            val document = app.get(mainUrl).document


            val categories = listOf("Yarışmalar", "Diziler", "Programlar")

            for (categoryName in categories) {
                Log.d("TV8", "Kategori işleniyor: $categoryName")

                val categoryElement = document.select("li.dropdown")
                    .firstOrNull { it.selectFirst("a[title]")?.attr("title")?.equals(categoryName, ignoreCase = true) == true }

                if (categoryElement != null) {
                    val items = categoryElement.select("ul.clearfix li")
                    val categoryResults = items.mapNotNull { it.toMainPageResult() }

                    Log.d("TV8", "$categoryName kategorisinden ${categoryResults.size} öğe toplandı")
                    allContent.addAll(categoryResults)
                }
            }


            val uniqueContent = allContent.distinctBy { it.url }
            Log.d("TV8", "Tekrar eden öğeler kaldırıldı. Toplam: ${uniqueContent.size}")


            allContentCache = uniqueContent
            cacheTime = currentTime

            Log.d("TV8", "Tüm içerikler toplandı: ${uniqueContent.size} öğe")
            return uniqueContent

        } catch (e: Exception) {
            Log.e("TV8", "Tüm içerikler toplanırken hata: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) {
            return emptyList()
        }

        Log.d("TV8", "Arama yapılıyor: '$query'")


        val allContent = getAllContent()

        if (allContent.isEmpty()) {
            Log.w("TV8", "Arama için içerik bulunamadı")
            return emptyList()
        }


        val searchQuery = query.lowercase(Locale.getDefault())
        val results = allContent.filter { content ->
            content.name.lowercase(Locale.getDefault()).contains(searchQuery)
        }

        Log.d("TV8", "Arama sonucu: ${results.size} öğe bulundu")
        return results
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.item img[src]")?.attr("src"))


        val tabElement = document.selectFirst("li.tabs a.tab[data-id]")
        val dataId = tabElement?.attr("data-id") ?: return null

        Log.d("TV8", "Found data-id: $dataId")


        val episodes = getEpisodes(dataId)

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
        }
    }

    private suspend fun getEpisodes(dataId: String): List<Episode> {
        val allEpisodes = mutableListOf<Episode>()
        var currentPage = 1


        Log.d("TV8", "Data ID: $dataId")

        try {

            while (currentPage <= 100) {
                val ajaxUrl = "${mainUrl}/Ajax/icerik/haberler/${dataId}/${currentPage}?tip=videolar&id=${dataId}&sayfa=${currentPage}&tip=videolar&hedef=%23tab-alt-${dataId}-icerik"

                Log.d("TV8", "Sayfa $currentPage getiriliyor: $ajaxUrl")

                val headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "Referer" to mainUrl
                )

                Log.d("TV8", "Headers: $headers")

                val response = app.get(ajaxUrl, headers = headers)
                Log.d("TV8", "Response status: ${response.code}")
                Log.d("TV8", "Response body: ${response.text}")


                val responseText = response.text.trim()
                if (responseText == "false" || responseText.isEmpty() || responseText == "null") {
                    Log.d("TV8", "Sayfa $currentPage'da daha fazla episode yok (response: $responseText), döngüden çıkılıyor")
                    break
                }


                val jsonArray = try {
                    JSONArray(responseText)
                } catch (e: Exception) {
                    Log.e("TV8", "JSON parse hatası sayfa $currentPage: ${e.message}")
                    Log.e("TV8", "Response text: $responseText")
                    break
                }

                Log.d("TV8", "JSON array length: ${jsonArray.length()}")

                if (jsonArray.length() == 0) {
                    Log.d("TV8", "Sayfa $currentPage'da episode bulunamadı, döngüden çıkılıyor")
                    break
                }


                for (i in 0 until jsonArray.length()) {
                    val episodeJson = jsonArray.getJSONObject(i)
                    Log.d("TV8", "Episode $i parse ediliyor")


                    val episodeNumber = 0

                    val episode = parseEpisode(episodeJson, episodeNumber)
                    if (episode != null) {
                        allEpisodes.add(episode)
                        Log.d("TV8", "Episode eklendi: ${episode.name} - Episode No: ${episode.episode}")
                    } else {
                        Log.w("TV8", "Episode $i parse edilemedi")
                    }
                }

                Log.d("TV8", "Sayfa $currentPage tamamlandı, toplam episode: ${allEpisodes.size}")
                currentPage++
            }

            Log.d("TV8", "Tüm sayfalar tamamlandı, toplam episode: ${allEpisodes.size}")


            allEpisodes.sortBy { it.date }
            Log.d("TV8", "Episode'lar tarihe göre sıralandı")


            val finalEpisodes = allEpisodes.mapIndexed { index, episode ->
                newEpisode(episode.data) {
    name = episode.name
    this.episode = index + 1
    this.posterUrl = episode.posterUrl
    date = episode.date
}

            }

            Log.d("TV8", "Episode numaraları güncellendi")

            Log.d("TV8", "Toplam dönen episode sayısı: ${finalEpisodes.size}")

            return finalEpisodes

        } catch (e: Exception) {
            Log.e("TV8", "Episode fetch genel hatası: ${e.message}")
            Log.e("TV8", "Hata detayı: ${e.stackTraceToString()}")


            if (allEpisodes.isNotEmpty()) {
                allEpisodes.sortBy { it.date }
                val finalEpisodes = allEpisodes.mapIndexed { index, episode ->
    newEpisode(episode.data) {
        name = episode.name
        this.episode = index + 1
        this.posterUrl = episode.posterUrl
        date = episode.date
    }
}

                Log.d("TV8", "Hata rağmen ${finalEpisodes.size} episode döndürülüyor")
                return finalEpisodes
            }
        }


        Log.d("TV8", "Toplam dönen episode sayısı: ${allEpisodes.size}")

        return allEpisodes
    }

    private fun parseEpisode(json: JSONObject, episodeNumber: Int): Episode? {
        try {

            Log.d("TV8", "JSON: $json")

            val title = json.optString("baslik", "")
            val slug = json.optString("slug", "")
            val duration = json.optString("video_suresi", "")
            val videoUrl = json.optString("tip_deger", "")
            val dateString = json.optString("kayit_tarihi", "")

            Log.d("TV8", "Başlık: $title")
            Log.d("TV8", "Slug: $slug")
            Log.d("TV8", "Süre: $duration")
            Log.d("TV8", "Video URL: $videoUrl")
            Log.d("TV8", "Tarih: $dateString")


            val resimJson = json.optString("resim", "{}")
            Log.d("TV8", "Resim JSON: $resimJson")

            val posterUrl = try {
                val resimObj = JSONObject(resimJson)
                val originalPath = resimObj.optString("original", "")
                if (originalPath.isNotEmpty()) {
                    val fullPosterUrl = "https://img.tv8.com.tr/$originalPath"
                    Log.d("TV8", "Poster URL: $fullPosterUrl")
                    fullPosterUrl
                } else {
                    Log.d("TV8", "Poster URL boş")
                    null
                }
            } catch (e: Exception) {
                Log.e("TV8", "Poster URL parse hatası: ${e.message}")
                null
            }

            if (title.isEmpty() || videoUrl.isEmpty()) {
                Log.w("TV8", "Başlık veya video URL boş - episode atlanıyor")
                return null
            }

            val episodeTitle = if (duration.isNotEmpty()) {
                "$title ($duration)"
            } else {
                title
            }

            Log.d("TV8", "title: $episodeTitle")


            val dateTimestamp = try {
                if (dateString.isNotEmpty()) {

                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val parsedDate = format.parse(dateString)?.time
                    Log.d("TV8", "Tarih parse edildi: $dateString -> $parsedDate")
                    parsedDate
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("TV8", "Tarih parse hatası: $dateString - ${e.message}")
                null
            }

            val episode = newEpisode(videoUrl) {
    name = episodeTitle
    episode = episodeNumber
    this.posterUrl = posterUrl
    date = dateTimestamp
}


            Log.d("TV8", "Episode oluşturuldu: ${episode.name}")

            return episode
        } catch (e: Exception) {
            Log.e("TV8", "Episode parse genel hatası: ${e.message}")
            return null
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("TV8", "Video data: $data")

        try {
            if (data.isBlank()) {
                Log.e("TV8", "Video data boş")
                return false
            }

            if ((data.startsWith("http://") || data.startsWith("https://")) && data.contains(".mp4")) {
                Log.d("TV8", "MP4 video bulundu")

                val httpsUrl = data.replace("http://", "https://")


                val url720p = httpsUrl.replace(".mp4", "-720p.mp4")
                callback.invoke(
                    newExtractorLink(
                        name = name,
                        source = name,
                        url = url720p,
                        type = if (url720p.contains(".mp4")) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                    ){
                        this.referer = mainUrl
                        this.quality = Qualities.P720.value
                    }
                )


                val url480p = httpsUrl.replace(".mp4", "-480p.mp4")
                callback.invoke(
                    newExtractorLink(
                        name = name,
                        source = name,
                        url = url480p,
                        type = if (url480p.contains(".mp4")) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                    ){
                        this.referer = mainUrl
                        this.quality = Qualities.P480.value
                    }
                )

                return true
            } else {
                Log.w("TV8", "MP4 video bulunamadı: $data")
                return false
            }

        } catch (e: Exception) {
            Log.e("TV8", "LoadLinks hatası: ${e.message}")
            return false
        }
    }
}
