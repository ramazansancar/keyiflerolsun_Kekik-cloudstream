package com.patron

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.app

class VidMody : ExtractorApi() {
    override val name            = "VidMody"
    override val mainUrl         = "https://player.vidmody.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val headers = mapOf(
            "User-Agent" to userAgent,
            "Referer"    to (referer ?: mainUrl)
        )

        val playerBody = app.get(url, headers = headers).text

        val jsRegex = Regex("""(?:decrypt|\))\s*\(\s*["']([0-9a-fA-F]+)["']\s*,\s*(\d+)\s*\)""")
        val jsMatch = jsRegex.find(playerBody) ?: return

        val hexString      = jsMatch.groupValues[1]
        val key            = jsMatch.groupValues[2].toIntOrNull() ?: return
        val decryptedBlock = decrypt(hexString, key)

        val fileRegex = Regex("""file:\s*['"](https?://[^'"]+)['"]""")
        val masterUrl = fileRegex.find(decryptedBlock)?.groupValues?.get(1) ?: return

        val masterHeaders = mapOf(
            "User-Agent" to userAgent,
            "Referer"    to url,
            "Origin"     to mainUrl
        )

        M3u8Helper.generateM3u8(name, masterUrl, referer ?: mainUrl).forEach(callback)
    }

    private fun decrypt(hex: String, key: Int): String {
        return try {
            val sb = StringBuilder()
            for (i in 0 until hex.length step 2) {
                sb.append(hex.substring(i, i + 2).toInt(16).toChar())
            }
            val decoded = Base64.decode(sb.reverse().toString(), 0)
            val result  = StringBuilder()
            for (b in decoded) {
                val unsignedByte = b.toInt() and 0xFF
                result.append((unsignedByte - key).toChar())
            }
            val rawString = result.toString()
            Regex("""\\x([0-9a-fA-F]{2})""").replace(rawString) { match ->
                match.groupValues[1].toInt(16).toChar().toString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun decodeHexUrl(input: String): String? {
        return try {
            val cleanHex = input.replace(Regex("[^0-9a-fA-F]"), "")
            val sb = StringBuilder()
            for (i in 0 until cleanHex.length step 2) {
                sb.append(cleanHex.substring(i, i + 2).toInt(16).toChar())
            }
            val result = sb.toString()
            if (result.startsWith("http")) result else null
        } catch (e: Exception) {
            null
        }
    }
}
