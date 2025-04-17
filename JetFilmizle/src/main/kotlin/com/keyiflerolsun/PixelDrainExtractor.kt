// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class PixelDrain : ExtractorApi() {
    override val name            = "PixelDrain"
    override val mainUrl         = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val pixelId      = url.substringAfterLast("/")
        val downloadLink = "${mainUrl}/api/file/${pixelId}?download"
        Log.d("Kekik_${this.name}", "downloadLink » $downloadLink")

        callback.invoke(
            newExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = downloadLink,
                type    = INFER_TYPE
			) {
                headers = mapOf("Referer" to url)
                quality = Qualities.Unknown.value
            }
        )
    }
}