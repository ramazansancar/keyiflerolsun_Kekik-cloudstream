package com.sinetech.latte

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
import org.jsoup.Jsoup // HTML ayrıştırma için
import org.jsoup.nodes.Document // Document tipi için

class PremiumVideoExtractor : ExtractorApi() {
    override val name = "Playhouse"
    override val mainUrl = "https://playhouse.premiumvideo.click"
    override val requiresReferer = true

    // Hex decode fonksiyonu (gerekiyorsa DiziFun.kt'den alınabilir)
    // private fun hexToString(...) { ... }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(name, "getUrl çağrıldı: url=$url, referer=$referer")

        val embedPageSource = try {
            app.get(url, referer = referer).text
        } catch (e: Exception) {
            Log.e(name, "Embed sayfası alınamadı: $url", e)
            return
        }

        val baseUri = URI(url)
        var sourceDisplayName = "Playhouse" // Varsayılan

        if (url.contains("/armony/")) {
            // === Video.js (/armony/) Mantığı ===
            sourceDisplayName = "PlayAmony" // İsmi güncelle
            Log.d(name, "Video.js ayrıştırıcı kullanılıyor for: $url")
            val document = Jsoup.parse(embedPageSource)
            val sourceTag = document.selectFirst("video#my-video_html5_api > source[type='application/x-mpegURL'], video > source[src*=.m3u8]")
            val relativeM3u8Path = sourceTag?.attr("src")

            if (relativeM3u8Path.isNullOrBlank()) {
                Log.w(name, "[Video.js] M3U8 source etiketi bulunamadı: $url")
                return
            }

            val fullM3u8Url = try { baseUri.resolve(relativeM3u8Path).toString() } catch (e: Exception) { null } ?: return
            Log.i(name, "[Video.js] >>> Nihai M3U8 URL: $fullM3u8Url")

            // === URL Farklılaştırma (Hile) ===
            val finalUrlForCallback = "$fullM3u8Url?player=amony#ignored"
            Log.d(name, "[Video.js] Callback için farklılaştırılmış URL: $finalUrlForCallback")
            // =================================

            callback.invoke(
                newExtractorLink(
                    source    = this.name,
                    name      = sourceDisplayName, // "PlayAmony"
                    url       = finalUrlForCallback, // Farklılaştırılmış URL
                    type      = ExtractorLinkType.M3U8
             ) {
                 this.quality = Qualities.Unknown.value
                 this.referer = url
             }
            )

            // Video.js Altyazıları (Script'ten Regex)
            val subtitlePattern = Regex("""player\.addRemoteTextTrack\(\s*\{\s*.*?src:\s*['"]([^'"]+)['"],\s*srclang:\s*['"]([^'"]+)['"],\s*label:\s*['"]([^'"]+)['"].*?\}\s*,\s*false\s*\)""", RegexOption.IGNORE_CASE)
            subtitlePattern.findAll(embedPageSource).forEach { match ->
                val relativePath = match.groups[1]?.value
                val lang = match.groups[2]?.value
                val label = match.groups[3]?.value
                if (!relativePath.isNullOrBlank() && !lang.isNullOrBlank()) {
                    try { baseUri.resolve(relativePath).toString() } catch (e: Exception) { null }?.let { fullSubUrl ->
                        Log.d(name, "[Video.js] Altyazı Bulundu: $label ($lang) - $fullSubUrl")
                        subtitleCallback.invoke(SubtitleFile(label ?: lang, fullSubUrl))
                    }
                }
            }

        } else {
            // === JW Player (/player/ veya varsayılan) Mantığı ===
            // sourceDisplayName zaten "Playhouse" olarak varsayılan ayarlı
            Log.d(name, "JW Player ayrıştırıcı kullanılıyor for: $url")

            val m3u8Pattern = Regex("""file:\s*['"]([^'"]+\.m3u8)['"]""")
            val relativeM3u8Path = m3u8Pattern.find(embedPageSource)?.groups?.get(1)?.value

            if (relativeM3u8Path.isNullOrBlank()) {
                Log.w(name, "[JW Player] Script içinde M3U8 linki bulunamadı: $url")
                return
            }

             val fullM3u8Url = try { baseUri.resolve(relativeM3u8Path).toString() } catch (e: Exception) { null } ?: return
             Log.i(name, "[JW Player] >>> Nihai M3U8 URL: $fullM3u8Url")

             // === URL Farklılaştırma (Hile) ===
             val finalUrlForCallback = "$fullM3u8Url?player=house#ignored"
             Log.d(name, "[JW Player] Callback için farklılaştırılmış URL: $finalUrlForCallback")
             // =================================

             callback.invoke(
                newExtractorLink(
                    source    = this.name,
                    name      = sourceDisplayName, // "PlayAmony"
                    url       = finalUrlForCallback, // Farklılaştırılmış URL
                    type      = ExtractorLinkType.M3U8
             ) {
                 this.quality = Qualities.Unknown.value
                 this.referer = url
             }
            )

            // JW Player Altyazıları (Script'ten Regex)
            val tracksPattern = Regex("""tracks:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            val trackEntryPattern = Regex("""\{\s*file:\s*['"]([^'"]+)['"],\s*label:\s*['"]([^'"]+)['"]""")

            tracksPattern.find(embedPageSource)?.groups?.get(1)?.value?.let { tracksBlock ->
                trackEntryPattern.findAll(tracksBlock).forEach { match ->
                    val relativePath = match.groups[1]?.value
                    val label = match.groups[2]?.value
                    val lang = label?.let { /* ... dil tahmini ... */
                         when {
                            it.contains("Türkçe", ignoreCase = true) -> "tr"
                            it.contains("English", ignoreCase = true) -> "en"
                            else -> "und"
                        }
                    } ?: "und"

                     if (!relativePath.isNullOrBlank() && label != null) {
                        try { baseUri.resolve(relativePath).toString() } catch (e: Exception) { null }?.let { fullSubUrl ->
                             Log.d(name, "[JW Player] Altyazı Bulundu: $label ($lang) - $fullSubUrl")
                            subtitleCallback.invoke(SubtitleFile(label, fullSubUrl))
                        }
                    }
                }
            }
        }
    }
}