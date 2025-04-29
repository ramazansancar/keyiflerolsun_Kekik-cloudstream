package com.sinetech.latte // Veya kendi paket yapınız

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// Sınıf adını PremiumVideoExtractor olarak değiştirdik
class GujanExtractor : ExtractorApi() {
    // İsim Gujan olsun
    override val name = "Gujan" // Veya "PremiumVideoGujan"
    // mainUrl'i gujan olarak ayarlayın
    override val mainUrl = "https://gujan.premiumvideo.click"
    override val requiresReferer = true

    // relevantUrlPatterns satırı buradan SİLİNDİ.

    // Hex decode fonksiyonu (Eğer DiziFun.kt'de varsa burada olmasına gerek yok,
    // ama bağımsız çalışması için burada da durabilir)
    private fun hexToString(hex: String): String {
        return try {
            hex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
                .toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(name, "Hex decode error for: $hex", e)
            ""
        }
    }


    override suspend fun getUrl(
        url: String, // playhouse veya gujan URL'si gelebilir
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(name, "getUrl çağrıldı: url=$url, referer=$referer")

        val embedPageSource = try {
            // timeout süresini artırmak gerekebilir, şimdilik varsayılan
            app.get(url, referer = referer).text
        } catch (e: Exception) {
            Log.e(name, "Embed sayfası alınamadı: $url", e)
            return // Sayfa alınamazsa devam etme
        }

        val baseUri = try { URI(url) } catch (e: Exception) {
            Log.e(name, "Geçersiz URI: $url", e)
            return // Geçersiz URI ise devam etme
        }
        var successful = false // Link bulunup bulunmadığını takip etmek için

        // Gelen URL'ye göre görünen adı belirle
        val displayName = when {
            url.contains("gujan.") -> "Gujan"
            url.contains("/armony/") -> "PlayAmony"
            else -> "Playhouse"
        }

        // --- Önce JW Player Mantığını Dene ---
        try {
            Log.d(name, "JW Player ayrıştırıcı deneniyor for: $url ($displayName)")
            val m3u8Pattern = Regex("""file:\s*['"]([^'"]+\.m3u8)['"]""")
            val relativeM3u8Path = m3u8Pattern.find(embedPageSource)?.groups?.get(1)?.value

            if (!relativeM3u8Path.isNullOrBlank()) {
                val fullM3u8Url = try { baseUri.resolve(relativeM3u8Path).toString() } catch (e: Exception) { null }
                if (fullM3u8Url != null) {
                    Log.i(name, "[JW Player] >>> Nihai M3U8 URL ($displayName): $fullM3u8Url")

                    // URL Farklılaştırma (İsteğe Bağlı Hile - ŞİMDİLİK KULLANMIYORUZ)
                    // val finalUrlForCallback = "$fullM3u8Url?player=jw#ignored"
                    callback.invoke(
                          newExtractorLink(
                            source    = this.name,
                            name      = displayName, // "PlayAmony"
                            url       = fullM3u8Url, // Farklılaştırılmış URL
                            type      = ExtractorLinkType.M3U8
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = url
                          }
                       )
                    successful = true // M3U8 linki başarıyla gönderildi

                    // JW Player Altyazıları
                    val tracksPattern = Regex("""tracks:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                    val trackEntryPattern = Regex("""\{\s*file:\s*['"]([^'"]+)['"],\s*label:\s*['"]([^'"]+)['"]""")
                    tracksPattern.find(embedPageSource)?.groups?.get(1)?.value?.let { tracksBlock ->
                        trackEntryPattern.findAll(tracksBlock).forEach { match ->
                            val subRelativePath = match.groups[1]?.value
                            val subLabel = match.groups[2]?.value
                            val subLang = subLabel?.let {
                                when { it.contains("Türkçe", true) -> "tr"; it.contains("English", true) -> "en"; else -> "und" }
                            } ?: "und"
                            if (!subRelativePath.isNullOrBlank() && subLabel != null) {
                                try { baseUri.resolve(subRelativePath).toString() } catch (e: Exception) { null }?.let { fullSubUrl ->
                                    Log.d(name, "[JW Player] Altyazı Bulundu: $subLabel ($subLang) - $fullSubUrl")
                                    subtitleCallback.invoke(SubtitleFile(subLabel, fullSubUrl))
                                }
                            }
                        }
                    }
                } else {
                    Log.w(name, "[JW Player] M3U8 URL oluşturulamadı: $url ($displayName)")
                }
            } else {
                Log.d(name, "[JW Player] Script içinde M3U8 linki bulunamadı: $url ($displayName)")
            }
        } catch (e: Exception) {
            Log.e(name, "[JW Player] Ayrıştırma hatası: $url ($displayName)", e)
        }

        // --- JW Player başarısız olduysa Video.js Mantığını Dene ---
        if (!successful) {
            try {
                Log.d(name, "Video.js ayrıştırıcı deneniyor for: $url ($displayName)")
                // Video.js için HTML'i ayrıştır
                val document = Jsoup.parse(embedPageSource)
                val sourceTag = document.selectFirst("video#my-video_html5_api > source[type='application/x-mpegURL'], video > source[src*=.m3u8]")
                val relativeM3u8Path = sourceTag?.attr("src")

                if (!relativeM3u8Path.isNullOrBlank()) {
                    val fullM3u8Url = try { baseUri.resolve(relativeM3u8Path).toString() } catch (e: Exception) { null }
                    if (fullM3u8Url != null) {
                        Log.i(name, "[Video.js] >>> Nihai M3U8 URL ($displayName): $fullM3u8Url")

                        // URL Farklılaştırma (İsteğe Bağlı Hile - ŞİMDİLİK KULLANMIYORUZ)
                        // val finalUrlForCallback = "$fullM3u8Url?player=videojs#ignored"
                        callback.invoke(
                          newExtractorLink(
                            source    = this.name,
                            name      = displayName, // "Gujan"
                            url       = fullM3u8Url, // Farklılaştırılmış URL
                            type      = ExtractorLinkType.M3U8
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = url
                          }
                       )
                        successful = true // M3U8 linki başarıyla gönderildi

                        // Video.js Altyazıları (Script'ten Regex)
                        val subtitlePattern = Regex("""player\.addRemoteTextTrack\(\s*\{\s*.*?src:\s*['"]([^'"]+)['"],\s*srclang:\s*['"]([^'"]+)['"],\s*label:\s*['"]([^'"]+)['"].*?\}\s*,\s*false\s*\)""", RegexOption.IGNORE_CASE)
                        subtitlePattern.findAll(embedPageSource).forEach { match ->
                            val subRelativePath = match.groups[1]?.value
                            val subLang = match.groups[2]?.value
                            val subLabel = match.groups[3]?.value
                            if (!subRelativePath.isNullOrBlank() && !subLang.isNullOrBlank()) {
                                try { baseUri.resolve(subRelativePath).toString() } catch (e: Exception) { null }?.let { fullSubUrl ->
                                    Log.d(name, "[Video.js] Altyazı Bulundu: $subLabel ($subLang) - $fullSubUrl")
                                    subtitleCallback.invoke(SubtitleFile(subLabel ?: subLang, fullSubUrl))
                                }
                            }
                        }
                    } else {
                        Log.w(name, "[Video.js] M3U8 URL oluşturulamadı: $url ($displayName)")
                    }
                } else {
                    Log.d(name, "[Video.js] M3U8 source etiketi bulunamadı: $url ($displayName)")
                }
            } catch (e: Exception) {
                Log.e(name, "[Video.js] Ayrıştırma hatası: $url ($displayName)", e)
            }
        }

        // --- Son Kontrol ---
        if (!successful) {
            Log.e(name, "Hiçbir yöntemle M3U8 linki bulunamadı: $url")
        }
    }
}