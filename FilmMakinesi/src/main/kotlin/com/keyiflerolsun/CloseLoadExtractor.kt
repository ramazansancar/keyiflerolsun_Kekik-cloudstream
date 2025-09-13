package com.keyiflerolsun

import com.lagradost.cloudstream3.ExtractorApi
import com.lagradost.cloudstream3.utils.Video
import com.lagradost.cloudstream3.utils.Subtitle
import com.lagradost.cloudstream3.utils.loadHtml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class CloseLoad : ExtractorApi() {
    override val name: String = "CloseLoad"
    override val mainUrl: String = "https://closeload.filmmakinesi.de"
    override val requiresReferer: Boolean = true

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    // Session state (izleme süresi ve alt yazı durumu)
    private val sessionState = mutableMapOf<String, Any>(
        "watchingtimetotal" to 0,
        "resumeAt" to 0,
        "forced_altyazi_durum" to 0
    )

    // Ana video listesi
    override suspend fun getVideoList(url: String): List<Video> {
        val videos = mutableListOf<Video>()
        try {
            val doc: Document = loadHtml(url)

            // Master.txt veya HLS linki
            val masterTxtUrl = doc.selectFirst("source[src\$=.m3u8]")?.attr("src")
                ?: doc.selectFirst("a[href\$=.txt]")?.attr("href")
                ?: throw Exception("Master.txt bulunamadı")

            // Cookie ve header simülasyonu
            val cookies = "watchingtimetotal=${sessionState["watchingtimetotal"]}; " +
                    "resumeAt=${sessionState["resumeAt"]}; " +
                    "forced_altyazi_durum=${sessionState["forced_altyazi_durum"]}"

            // Master.txt içeriğini al
            val masterContent = getMasterTxtWithCookie(masterTxtUrl, cookies)

            // Alt yazı parse
            val subtitles = parseSubtitles(masterContent)

            // Video ekle
            videos.add(
                Video(
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
        return videos
    }

    // Master.txt'yi fetch et
    private fun getMasterTxtWithCookie(masterTxtUrl: String, cookie: String): String? {
        val request = Request.Builder()
            .url(masterTxtUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36")
            .header("Referer", mainUrl)
            .header("Cookie", cookie)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch master.txt: ${response.code}")
            return response.body?.string()
        }
    }

    // Alt yazıları parse et
    private fun parseSubtitles(masterContent: String?): List<Subtitle> {
        val subs = mutableListOf<Subtitle>()
        masterContent?.lines()?.forEach { line ->
            if (line.contains(".vtt") || line.contains(".srt")) {
                val subUrl = line.trim()
                subs.add(Subtitle(name = "Türkçe", url = subUrl, language = "tr"))
            }
        }
        return subs
    }

    // İzleme süresini güncelle
    fun updateWatchingTime(currentTime: Int) {
        sessionState["watchingtimetotal"] = (sessionState["watchingtimetotal"] as Int) + currentTime
        sessionState["resumeAt"] = currentTime
    }

    // Alt yazı durumunu set et
    fun setForcedSubtitle(enabled: Boolean) {
        sessionState["forced_altyazi_durum"] = if (enabled) 1 else 0
    }
}
