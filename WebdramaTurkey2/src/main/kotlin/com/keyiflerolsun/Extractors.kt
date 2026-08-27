package com.patron

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

private const val TAG = "WDT2_EXT"

open class WebDramaTurkeyExtractor : ExtractorApi() {
    override val name            = "WebDramaTurkey"
    override val mainUrl         = "https://dtpasn.asia"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(TAG, "WebDramaTurkeyExtractor » url: $url")
        try {
            val iframeHtml = app.get(url, referer = referer ?: mainUrl).text

            val fireplayerCookieMatch = Regex("""cookie\s*=\s*["']([^"']+)["']""").find(iframeHtml)
            val fireplayerCookie = fireplayerCookieMatch?.groupValues?.get(1)

            val apiResponse = app.post(
                "${mainUrl}/api/source",
                headers = buildMap {
                    put("Referer", url)
                    put("Content-Type", "application/x-www-form-urlencoded")
                    if (fireplayerCookie != null) put("Cookie", fireplayerCookie)
                },
                data = mapOf("r" to (referer ?: ""), "d" to mainUrl)
            ).text

            Log.d(TAG, "dtpasn API yanıtı: $apiResponse")

            val videoSource = Regex("""["']?videoSource["']?\s*:\s*["']([^"']+)["']""")
                .find(apiResponse)?.groupValues?.get(1)
            val securedLink = Regex("""["']?securedLink["']?\s*:\s*["']([^"']+)["']""")
                .find(apiResponse)?.groupValues?.get(1)

            val finalUrl = securedLink ?: videoSource
            if (finalUrl != null) {
                val isM3u8 = finalUrl.contains(".m3u8", ignoreCase = true)
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name   = name,
                        url    = finalUrl,
                        type   = if (isM3u8) INFER_TYPE else ExtractorLinkType.VIDEO
                    ) {
                        quality = Qualities.Unknown.value
                        headers = mapOf("Referer" to mainUrl)
                    }
                )
                return
            }

            val fileMatches = Regex("""file\s*:\s*["']([^"']+\.(m3u8|mp4)[^"']*)["']""")
                .findAll(apiResponse)
            fileMatches.forEach { match ->
                val fileUrl = match.groupValues[1]
                val isM3u8  = fileUrl.contains(".m3u8", ignoreCase = true)
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name   = name,
                        url    = fileUrl,
                        type   = if (isM3u8) INFER_TYPE else ExtractorLinkType.VIDEO
                    ) {
                        quality = Qualities.Unknown.value
                        headers = mapOf("Referer" to mainUrl)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebDramaTurkeyExtractor hatası: ${e.message}")
        }
    }
}

open class VkExtractor : ExtractorApi() {
    override val name            = "Vk"
    override val mainUrl         = "https://vkvideo.ru"
    override val requiresReferer = true

    private val commonUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(TAG, "VkExtractor » url: $url")
        try {
            val headers = mapOf(
                "User-Agent" to commonUserAgent,
                "Referer"    to (referer ?: mainUrl)
            )

            val response = app.get(url, headers = headers).text

            val oid  = Regex("""[?&]oid=(-?\d+)""").find(url)?.groupValues?.get(1)
                ?: Regex("""[?&]oid=(-?\d+)""").find(response)?.groupValues?.get(1)
            val id   = Regex("""[?&]id=(\d+)""").find(url)?.groupValues?.get(1)
                ?: Regex("""[?&]id=(\d+)""").find(response)?.groupValues?.get(1)
            val hash = Regex("""[?&]hash=([a-f0-9]+)""").find(url)?.groupValues?.get(1)
                ?: Regex("""access_key\s*:\s*["']([^"']+)["']""").find(response)?.groupValues?.get(1)

            val tokenRegex         = Regex(""""access_token"\s*:\s*"([^"]+)"""")
            val fallbackTokenRegex = Regex("""vk_access_token_settings=([^;]+)""")
            val token = tokenRegex.find(response)?.groupValues?.get(1)
                ?: fallbackTokenRegex.find(response)?.groupValues?.get(1)

            if (oid != null && id != null) {
                val apiUrl   = "https://api.vk.com/method/video.get"
                val postData = buildMap<String, String> {
                    put("videos", "${oid}_${id}")
                    if (hash != null)  put("access_key", hash)
                    if (token != null) put("access_token", token)
                    put("v", "5.131")
                }

                val apiResponse = app.post(apiUrl, data = postData, headers = headers).text
                if (extractLinksFromText(apiResponse, commonUserAgent, callback)) return
            }

            extractLinksFromText(response, commonUserAgent, callback)
        } catch (e: Exception) {
            Log.e(TAG, "VkExtractor hatası: ${e.message}")
        }
    }

    suspend fun extractLinksFromText(
        text: String,
        userAgent: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAny = false
        val streamRegex = Regex(
            """["']?(url|src)["']?\s*:\s*["']?(https?://[^"'\s,>]+\.(m3u8|mp4|mpd)[^"'\s,>]*)["']?""",
            RegexOption.IGNORE_CASE
        )

        for (match in streamRegex.findAll(text)) {
            val videoUrl = match.groupValues[2].trim()
            if (videoUrl.isBlank()) continue

            val isDash   = videoUrl.contains(".mpd", ignoreCase = true)
            val isM3u8   = videoUrl.contains(".m3u8", ignoreCase = true)
            val typeRaw  = when {
                isDash  -> "DASH"
                isM3u8  -> "HLS"
                else    -> "MP4"
            }
            val typeName = "$name ($typeRaw)"
            val linkType = when {
                isDash  -> ExtractorLinkType.DASH
                isM3u8  -> INFER_TYPE
                else    -> ExtractorLinkType.VIDEO
            }

            callback.invoke(
                newExtractorLink(
                    source = typeName,
                    name   = typeName,
                    url    = videoUrl,
                    type   = linkType
                ) {
                    quality = Qualities.Unknown.value
                    headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer"    to mainUrl
                    )
                }
            )
            foundAny = true
        }
        return foundAny
    }
}

class VkCom : VkExtractor() {
    override var mainUrl = "https://vk.com"
}

class Abstream : ExtractorApi() {
    override var name            = "Abstream"
    override var mainUrl         = "https://abstream.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(TAG, "Abstream » url: $url")
        try {
            val response = app.get(url, referer = referer ?: mainUrl).text

            val m3u8Matches = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
                .findAll(response)

            m3u8Matches.forEach { match ->
                val m3u8Url = match.groupValues[1]
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name   = name,
                        url    = m3u8Url,
                        type   = INFER_TYPE
                    ) {
                        quality = Qualities.Unknown.value
                        headers = mapOf("Referer" to mainUrl)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Abstream hatası: ${e.message}")
        }
    }
}
