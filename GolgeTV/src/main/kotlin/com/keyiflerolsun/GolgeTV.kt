package com.keyiflerolsun

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.network.AppUtils
import com.lagradost.cloudstream3.*
import android.util.Log

data class MainPageResp(
    val icerikler: List<Any>?,
    val ormocChnlx: List<OrmoxChnlx>?,
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

suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    Log.d("GolgeTV", "getMainPage called with request: ${request.name}")

    // İstek gönderimi
    val home = app.post(
        "https://panel.cloudgolge.shop/appMainGetData.php",
        headers = mapOf(
            "x-requested-with" to "com.golge.golgetv2",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:80.0) Gecko/20100101 Firefox/79.0"
        ),
        data = mapOf(
            "ormoxRoks" to "D8C42BC6CD20C00E85659003F62B1F4A7A882DCB",
            "ormxArmegedEryxc" to "",
            "asize" to "FtpfiQA63G0Su9XCYQW9vg==", // Curl'dan gelen Base64 ham hali
            "serverurl" to "https://raw.githubusercontent.com/sevdaliyim/sevdaliyim/refs/heads/main/ssl2.key",
            "glg1Key" to "1FbcLGctAooQU7L6LQ2YaDtpNHNryPGMde7wUd47Jc53lOikXegk4LKREvfKqZYk",
            "kategori" to request.name // Örneğin "ULUSAL", "SPOR", "HABER"
        )
    )
    Log.d("GolgeTV", "Response from POST: ${home.text}")

    // Yanıtın boş olup olmadığını kontrol et
    if (home.text.isEmpty()) {
        Log.e("GolgeTV", "Response is empty")
        throw Exception("Server returned empty response")
    }

    // JSON parse işlemi - Bilinmeyen alanları yok sayacak şekilde yapılandırılmış
    val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    val jsonResponse = try {
        mapper.readValue<MainPageResp>(home.text)
    } catch (e: Exception) {
        Log.e("GolgeTV", "JSON parsing failed: ${e.message}, Raw response: ${home.text}")
        throw Exception("Failed to parse JSON response: ${home.text}, Error: ${e.message}")
    }

    // ormocChnlx kontrolü
    val channels = jsonResponse.ormocChnlx
    if (channels == null) {
        Log.e("GolgeTV", "ormoxChnlx is null in JSON response: ${home.text}")
        throw Exception("ormoxChnlx is null - Server did not return channel data")
    }

    // Kanalları filtreleme ve SearchResponse listesine dönüştürme
    val contents = mutableListOf<SearchResponse>()
    channels
        .filter {
            // Filtreleme koşulları: Kategori eşleşmesi ve player türü
            if (it.kategori != request.name || it.player == "m3u") return@filter false
            if (it.player == "iframe" && !it.link.contains("golge")) return@filter false
            true
        }
        .forEach {
            val toDict = mapper.writeValueAsString(it)
            contents.add(newLiveSearchResponse(it.isim ?: "Unknown", toDict, TvType.Live) {
                this.posterUrl = it.resim
            })
        }

    Log.d("GolgeTV", "Filtered contents size: ${contents.size}")
    return newHomePageResponse(request.name, contents)
}

// AppUtils.tryParseJson yerine özel bir fonksiyon (isteğe bağlı)
inline fun <reified T> tryParseJson(text: String): T? {
    val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    return try {
        mapper.readValue<T>(text)
    } catch (e: Exception) {
        null
    }
}
