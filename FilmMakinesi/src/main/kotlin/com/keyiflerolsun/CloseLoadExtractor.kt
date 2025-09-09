package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Base64
import java.nio.charset.StandardCharsets

class CloseLoad : ExtractorApi() {
    override val name = "CloseLoad"
    override val mainUrl = "https://closeload.filmmakinesi.de"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers2 = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer" to mainUrl,
            "Origin" to mainUrl
        )
        
        Log.d("Kekik_${this.name}", "url » $url")

        try {
            val iSource = app.get(url, referer = mainUrl, headers = headers2)

            val obfuscatedScript = iSource.document.select("script[type=text/javascript]")[1].data().trim()
            val rawScript = getAndUnpack(obfuscatedScript)
            
            Log.d("Kekik_${this.name}", "Raw Script: ${rawScript.take(200)}...")
            
            // Base64 input'u daha güvenli şekilde al
            val base64Input = extractBase64Input(rawScript)
            if (base64Input.isNullOrEmpty()) {
                throw ErrorLoadingException("Base64 input not found")
            }

            Log.d("Kekik_${this.name}", "Base64 Input: $base64Input")
            
            val lastUrl = dcHello(base64Input)
            Log.d("Kekik_${this.name}", "Decoded URL: $lastUrl")

            if (!lastUrl.startsWith("http")) {
                throw ErrorLoadingException("Invalid URL format")
            }

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = lastUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                    this.headers = headers2
                }
            )

            // Altyazıları işle
            processSubtitles(iSource, subtitleCallback, headers2)

        } catch (e: Exception) {
            Log.e("Kekik_${this.name}", "Error: ${e.message}")
            throw ErrorLoadingException("Failed to extract video URL: ${e.message}")
        }
    }

    private fun extractBase64Input(rawScript: String): String? {
        return try {
            // Farklı pattern'ler deneyelim
            val patterns = listOf(
                "dc_hello\\(\"([^\"]+)\"\\)",
                "dc_hello\\('([^']+)'\\)",
                "dc_hello\\(['\"]([^'\"]+)['\"]\\)"
            )
            
            for (pattern in patterns) {
                val regex = Regex(pattern)
                val match = regex.find(rawScript)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e("Kekik_${this.name}", "Extract base64 error: ${e.message}")
            null
        }
    }

    private fun dcHello(base64Input: String): String {
        return try {
            Log.d("Kekik_${this.name}", "Input for dcHello: $base64Input")
            
            // Base64 input temizleme (geçersiz karakterleri kaldır)
            val cleanedInput = base64Input.replace("[^A-Za-z0-9+/=]".toRegex(), "")
            
            // İlk decode
            val decodedOnce = base64Decode(cleanedInput)
            Log.d("Kekik_${this.name}", "First decode: $decodedOnce")
            
            // Reverse
            val reversedString = decodedOnce.reversed()
            Log.d("Kekik_${this.name}", "Reversed: $reversedString")
            
            // İkinci decode (reversed string'i temizle)
            val cleanedReversed = reversedString.replace("[^A-Za-z0-9+/=]".toRegex(), "")
            val decodedTwice = base64Decode(cleanedReversed)
            Log.d("Kekik_${this.name}", "Second decode: $decodedTwice")
            
            // URL çıkarma
            when {
                decodedTwice.contains("+") -> {
                    val result = "https" + decodedTwice.substringAfterLast("+")
                    Log.d("Kekik_${this.name}", "Extracted from +: $result")
                    result
                }
                decodedTwice.contains("|") -> {
                    val parts = decodedTwice.split("|")
                    val result = if (parts.size > 1) "https" + parts[1] else decodedTwice
                    Log.d("Kekik_${this.name}", "Extracted from |: $result")
                    result
                }
                else -> {
                    Log.d("Kekik_${this.name}", "Using raw result: $decodedTwice")
                    if (decodedTwice.startsWith("http")) decodedTwice else "https://$decodedTwice"
                }
            }
        } catch (e: Exception) {
            Log.e("Kekik_${this.name}", "dcHello error: ${e.message}")
            throw ErrorLoadingException("Failed to decode URL: ${e.message}")
        }
    }

    // Güvenli base64 decode fonksiyonu
    private fun base64Decode(input: String): String {
        return try {
            // Android Base64 kullan
            val decodedBytes = Base64.decode(input, Base64.DEFAULT)
            String(decodedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Kotlin Base64 fallback
            try {
                kotlin.io.encoding.Base64.decode(input)
            } catch (e2: Exception) {
                Log.e("Kekik_${this.name}", "Base64 decode failed for: $input")
                throw e2
            }
        }
    }

    private fun processSubtitles(iSource: AppResponse, subtitleCallback: (SubtitleFile) -> Unit, headers: Map<String, String>) {
        iSource.document.select("track").forEach { track ->
            val rawSrc = track.attr("src").trim()
            val label = track.attr("label").ifBlank { "Altyazı" }
            
            if (rawSrc.isNotBlank()) {
                val fullUrl = if (rawSrc.startsWith("http")) {
                    rawSrc
                } else {
                    mainUrl.trimEnd('/') + "/" + rawSrc.trimStart('/')
                }
                
                if (fullUrl.startsWith("http")) {
                    Log.d("Kekik_${this.name}", "Altyazı bulundu: $label -> $fullUrl")
                    subtitleCallback(SubtitleFile(label, fullUrl))
                }
            }
        }
    }
}
