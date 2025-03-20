package com.keyiflerolsun

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import android.util.Log

class GolgeTV : MainAPI() {
    override var name = "GolgeTV"
    override var mainUrl = "https://panel.cloudgolge.shop/appMainGetData.php"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        this.mainUrl to "ULUSAL",
        this.mainUrl to "SPOR",
        this.mainUrl to "HABER",
        this.mainUrl to "BELGESEL",
        this.mainUrl to "SİNEMA",
        this.mainUrl to "ÇOCUK",
        this.mainUrl to "MÜZİK",
    )

    // JSON veri modelleri
    data class MainPageResp(
        val icerikler: List<Any>?,
        val ormocChnlx: List<OrmoxChnlx>?, // Curl'da "ormoxChnlx" olarak geliyor
        val menuPaylas: String?,
        val menuInstagram: String?,
        val menuTelegram: String?,
        val onlineTime: String?,
        val onlineDurum: String?
    )

    data class OrmoxChnlx(
        val id: String?,
        val isim: String?,
        val resim: String?,
        val link: String?,
        val kategori: String?,
        val player: String?,
        val tip: String?,
        val userAgent: String?,
        val h1Key: String?,
        val h1Val: String?,
        val h2Key: String?,
        val h2Val: String?,
        val h3Key: String?,
        val h3Val: String?,
        val h4Key: String?,
        val h4Val: String?,
        val h5Key: String?,
        val h5Val: String?
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        Log.d("GolgeTV", "getMainPage called with request: ${request.name}")

        val home = app.post(
            request.data, // mainUrl kullanılıyor ("https://panel.cloudgolge.shop/appMainGetData.php")
            headers = mapOf(
                "x-requested-with" to "com.golge.golgetv2",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:80.0) Gecko/20100101 Firefox/79.0"
            ),
            data = mapOf(
                "ormoxRoks" to "D8C42BC6CD20C00E85659003F62B1F4A7A882DCB",
                "ormxArmegedEryxc" to "", // "ormxArmegedEryxc=" yerine düzeltildi
                "asize" to "FtpfiQA63G0Su9XCYQW9vg==", // %3D%3D%0A kaldırıldı, ham hali
                "serverurl" to "https://raw.githubusercontent.com/sevdaliyim/sevdaliyim/refs/heads/main/ssl2.key",
                "glg1Key" to "1FbcLGctAooQU7L6LQ2YaDtpNHNryPGMde7wUd47Jc53lOikXegk4LKREvfKqZYk", // %0A kaldırıldı
                "kategori" to request.name // Kategori filtresi eklendi
            )
        )
        Log.d("GolgeTV", "Response from POST: ${home.text}")

        // Boş yanıt kontrolü
        if (home.text.isEmpty()) {
            Log.e("GolgeTV", "Response is empty")
            throw Exception("Server returned empty response")
        }

        // JSON parse işlemi için Jackson kullanıyoruz, bilinmeyen alanları yok say
        val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
        val jsonResponse = try {
            mapper.readValue<MainPageResp>(home.text)
        } catch (e: Exception) {
            Log.e("GolgeTV", "Failed to parse JSON: ${home.text}, Error: ${e.message}")
            throw Exception("Failed to parse JSON response: ${e.message}")
        }

        val channels = jsonResponse.ormocChnlx
        if (channels == null) {
            Log.e("GolgeTV", "ormoxChnlx is null in JSON response: ${home.text}")
            throw Exception("ormoxChnlx is null")
        }

        val contents = mutableListOf<SearchResponse>()
        channels
            .filter {
                if (it.kategori != request.name || it.player == "m3u") return@filter false
                if (it.player == "iframe" && !it.link.contains("golge")) return@filter false
                return@filter true
            }
            .forEach {
                val toDict = jacksonObjectMapper().writeValueAsString(it)
                contents.add(newLiveSearchResponse(it.isim ?: "Unknown", toDict, TvType.Live) {
                    this.posterUrl = it.resim
                })
            }
        Log.d("GolgeTV", "Contents size: ${contents.size}")
        return newHomePageResponse(request.name, contents)
    }

    override suspend fun load(url: String): LoadResponse? {
        val content = AppUtils.tryParseJson<OrmoxChnlx>(url) ?: return null
        return newLiveStreamLoadResponse(content.isim ?: "Unknown", url, url) {
            this.posterUrl = content.resim
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val content = AppUtils.tryParseJson<OrmoxChnlx>(data) ?: return false
        Log.d("GolgeTV", "Parsed content: $content")

        if (content.player == "iframe") {
            val golgeMatch = Regex("^(golge(?:2|3|4|5|6|7|8|9|1[0-9])://).*").find(content.link)
            if (golgeMatch != null) {
                val (golgeProtocol) = golgeMatch.destructured
                loadExtractor("$golgeProtocol||$data", subtitleCallback, callback)
                return true
            }
            return false
        }

        val headers = mapOf(
            content.h1Key to content.h1Val,
            content.h2Key to content.h2Val,
            content.h3Key to content.h3Val,
            content.h4Key to content.h4Val,
            content.h5Key to content.h5Val
        ).filter { (key, value) -> key != null && value != null && key != "0" }
        
        if (content.link.isNullOrEmpty() || content.isim.isNullOrEmpty()) {
            Log.e("GolgeTV", "Invalid data - Link: ${content.link}, Name: ${content.isim}")
            return false
        }

        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = content.isim,
                url = content.link,
                referer = headers["Referer"] ?: "",
                quality = Qualities.Unknown.value,
                headers = headers,
                isM3u8 = true
            )
        )
        return true
    }
}