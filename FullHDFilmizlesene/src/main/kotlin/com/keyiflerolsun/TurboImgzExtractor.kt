// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class TurboImgz : ExtractorApi() {
    override val name            = "TurboImgz"
    override val mainUrl         = "https://watch.turkeyplayer.com/"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef   = referer ?: ""
        val videoReq = app.get(url.split("/").last()).text
		Log.d("Kekik_${this.name}", "videoReq » $videoReq")

        val videoLink = Regex("""file: "(.*)",""").find(videoReq)?.groupValues?.get(1) ?: throw ErrorLoadingException("File not found")
        Log.d("Kekik_${this.name}", "videoLink » $videoLink")

        callback.invoke(
            newExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = videoLink,
                type = ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
                headers = mapOf("Referer" to extRef)
            }
        )
    }
}
