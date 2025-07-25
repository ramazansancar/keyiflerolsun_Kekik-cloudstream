package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Base64

private fun getm3uLink(data: String): String {
    val first = Base64.decode(data, Base64.DEFAULT).reversedArray()
    val second = Base64.decode(first, Base64.DEFAULT)
    val result = second.toString(Charsets.UTF_8).split("|")[1]
    return result
}

open class CloseLoad : ExtractorApi() {
    override val name = "CloseLoad"
    override val mainUrl = "https://closeload.filmmakinesi.de"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val extRef = referer ?: ""
	val headers2 = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Norton/124.0.0.0"
        )
        Log.d("Kekik_${this.name}", "url » $url")

        val iSource = app.get(url, referer = mainUrl, headers=headers2)

        val obfuscatedScript = iSource.document.select("script[type=text/javascript]")[1].data().trim()
        val rawScript = getAndUnpack(obfuscatedScript)
        val regex = Regex("var player=this\\}\\);var(.*?);myPlayer\\.src")
        val matchResult = regex.find(rawScript)
        val base64Input = rawScript.substringAfter("dc_hello(\"").substringBefore("\");")
        val lastUrl = dcHello(base64Input).substringAfter("https").let { "https$it" }
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = lastUrl,
                ExtractorLinkType.M3U8
            ) {
                this.referer = mainUrl
                this.quality = Qualities.Unknown.value
            }
        )

        if (matchResult != null) {
            val extractedString = matchResult.groups[1]?.value
                ?.trim()?.substringAfter("=\"")?.substringBefore("\"")
            val m3uLink = Base64.decode(extractedString, Base64.DEFAULT).toString(Charsets.UTF_8)
            Log.d("Kekik_${this.name}", "m3uLink » $m3uLink")

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = m3uLink ?: throw ErrorLoadingException("m3u link not found"),
                    type = ExtractorLinkType.M3U8
                ) {
                    quality = Qualities.Unknown.value
                    headers = mapOf("Referer" to mainUrl, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Norton/124.0.0.0")
                }
            )

        // Şimdi altyazıları gönder
        iSource.document.select("track").forEach {
            val rawSrc = it.attr("src").trim()
        
            // Eğer src boşsa veya anlamlı bir yol değilse atla
            if (rawSrc.isBlank() || (!rawSrc.startsWith("http") && !rawSrc.startsWith("/"))) {
                Log.w("Kekik_${this.name}", "Geçersiz veya boş altyazı src: [$rawSrc]")
                return@forEach
            }
        
            // URL'yi düzgün oluştur
            val fullUrl = if (rawSrc.startsWith("http")) {
                rawSrc
            } else {
                mainUrl.trimEnd('/') + rawSrc
            }
        
            // Tam URL'nin geçerli bir http(s) adresi olup olmadığını kontrol et
            if (fullUrl.startsWith("http://") || fullUrl.startsWith("https://")) {
                val label = it.attr("label").ifBlank { "Altyazı" }
                Log.d("Kekik_${this.name}", "Altyazı bulundu: $label -> $fullUrl")
        
                try {
                    val subtitleResponse = app.get(fullUrl, headers = headers2)
                    if (subtitleResponse.isSuccessful) {
                        subtitleCallback(SubtitleFile(label, fullUrl))
                        Log.d("FLMM", "Subtitle added: $fullUrl")
                    } else {
                        Log.d("FLMM", "Subtitle URL erişilemedi: ${subtitleResponse.code}")
                    }
                } catch (e: Exception) {
                    Log.e("Kekik_${this.name}", "Altyazı indirme hatası: ${e.localizedMessage}")
                }
            } else {
                Log.w("Kekik_${this.name}", "Hatalı altyazı URL'si: $fullUrl")
            }
        }
    }
}
    fun dcHello(base64Input: String): String {
        val decodedOnce = base64Decode(base64Input)
        val reversedString = decodedOnce.reversed()
        val decodedTwice = base64Decode(reversedString)
        val flmmLink = if (decodedTwice.contains("+")){
        decodedTwice.substringAfterLast("+")
            } else if (decodedTwice.contains("|")) {
        decodedTwice.split("|")[1]
            } else {
        decodedTwice
        }
        return flmmLink
    }
}
