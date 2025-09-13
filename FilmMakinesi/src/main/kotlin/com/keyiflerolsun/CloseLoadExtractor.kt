package com.keyiflerolsun

import com.lagradost.cloudstream3.ExtractorApi
import com.lagradost.cloudstream3.utils.loadHtml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class CloseLoad : ExtractorApi() {
    override val name = "CloseLoad"
    override val mainUrl = "https://closeload.filmmakinesi.de"
    override val requiresReferer = true

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    // Kullanıcının izleme süresi ve alt yazı durumu için session map
    private val sessionState = mutableMapOf<String, Any>(
        "watchingtimetotal" to 0,
        "resumeAt" to 0,
        "forced_altyazi_durum" to 0
    )

    // Ana video listesi çekme
    override suspend fun getVideoList(url: String) = mutableListOf<ExtractorApi.Video>().apply {
        try {
            val doc: Document = loadHtml(url)

            // Master.txt veya HLS linki
            val masterTxtUrl = doc.selectFirst("source[src$=.m3u8]")?.attr("src")
                ?: doc.selectFirst("a[href$=.txt]")?.attr("href")
                ?: throw Exception("Master.txt bulunamadı")

            // Cookie ve header simülasyonu
            val cookies = "watchingtimetotal=${sessionState["watchingtimetotal"]}; " +
                          "resumeAt=${sessionState["resumeAt"]}; " +
                          "forced_altyazi_durum=${sessionState["forced_altyazi_durum"]}"

            // Master.txt içeriğini al
            val masterContent = getMasterTxtWithCookie(masterTxtUrl, cookies)

            // Alt yazı kontrolü
            val subtitles = parseSubtitles(masterContent)

            // Video ekle
            this.add(
                ExtractorApi.Video(
                    name = "CloseLoad Video",
                    url = masterTxtUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
                        "Referer" to mainUrl,
                        "Cookie" to cookies
                    ),
                    subtitles = subtitles
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Master.txt'yi JS kontrolünü taklit ederek çek
    private fun getMasterTxtWithCookie(masterTxtUrl: String, cookie: String): String? {
        val request = Request.Builder()
            .url(masterTxtUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36")
            .header("Referer", mainUrl)
            .header("Cookie", cookie)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to fetch master.txt: ${response.code}")
            }
            return response.body?.string()
        }
    }

    // Master.txt içinden alt yazıları parse et
    private fun parseSubtitles(masterContent: String?): List<ExtractorApi.Subtitle> {
        val subs = mutableListOf<ExtractorApi.Subtitle>()
        masterContent?.lines()?.forEach { line ->
            if (line.contains(".vtt") || line.contains(".srt")) {
                val subUrl = line.trim()
                subs.add(ExtractorApi.Subtitle(
                    name = "Türkçe", url = subUrl, language = "tr"
                ))
            }
        }
        return subs
    }

    // İzleme süresini güncelle (JS mantığını Kotlin tarafına taşıdık)
    fun updateWatchingTime(currentTime: Int) {
        sessionState["watchingtimetotal"] = (sessionState["watchingtimetotal"] as Int) + currentTime
        sessionState["resumeAt"] = currentTime
    }

    // Alt yazı durumu kontrolü
    fun setForcedSubtitle(enabled: Boolean) {
        sessionState["forced_altyazi_durum"] = if (enabled) 1 else 0
    }
}
