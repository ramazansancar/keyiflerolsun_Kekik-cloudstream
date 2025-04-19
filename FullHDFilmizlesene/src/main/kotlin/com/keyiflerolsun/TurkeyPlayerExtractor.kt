// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class TurkeyPlayer : ExtractorApi() {
    override val name            = "TurkeyPlayer"
    override val mainUrl         = "https://watch.turkeyplayer.com/"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        val pageContent = app.get(url, referer = extRef).text
        Log.d("Kekik_${this.name}", "PageContent Alındı")

        // JavaScript içindeki video değişkenini çek
        val videoJsonRaw = Regex("""var\s+video\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            .find(pageContent)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Video nesnesi bulunamadı")

        // JSON'a parse et
        val videoJson = JSONObject(videoJsonRaw)
        val id = videoJson.getString("id")
        val md5 = videoJson.getString("md5")

        val masterUrl = "https://watch.turkeyplayer.com/m3u8/8/$md5/master.txt?s=1&id=$id&cache=1"
        Log.d("Kekik_${this.name}", "masterUrl » $masterUrl")

        callback.invoke(
            newExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = masterUrl,
                type = ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
                headers = mapOf("Referer" to extRef)
            }
        )
    }
}
