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

        // video = {...} içeriğini bul
        val videoJsonRaw = Regex("""var\s+video\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            .find(pageContent)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Video nesnesi bulunamadı")

        // Cloudstream'e özel parseJson metodu
        val videoJson = parseJson<VideoData>(videoJsonRaw)

        val masterUrl = "https://watch.turkeyplayer.com/m3u8/8/${videoJson.md5}/master.txt?s=1&id=${videoJson.id}&cache=1"
        Log.d("Kekik_${this.name}", "masterUrl » $masterUrl")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name   = this.name,
                url    = masterUrl,
                type   = ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
                headers = mapOf("Referer" to extRef)
            }
        )
    }

    data class VideoData(
        val id: String,
        val md5: String
    )
}
