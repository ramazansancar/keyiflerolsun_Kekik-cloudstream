// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class TauVideo2 : ExtractorApi() {
    override val name            = "TauVideo2"
    override val mainUrl         = "https://fang-heshan.store"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        val videoKey = url.split("/").last()
        val videoUrl = "${mainUrl}/file/tau-video/${videoKey}"
        Log.d("Kekik_${this.name}", "videoUrl » $videoUrl")

        val file = app.get(videoUrl).parsedSafe<TauVideoUrls>() ?: throw ErrorLoadingException("TauVideo2")

        for (video in file.urls) {
            callback.invoke(
                ExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = video.url,
                    referer = extRef,
                    quality = getQualityFromName(video.label),
                    type    = INFER_TYPE
                )
            )
        }
    }


    data class TauVideoUrls(
        @JsonProperty("urls") val urls: List<TauVideoData>
    )

    data class TauVideoData(
        @JsonProperty("url")   val url: String,
        @JsonProperty("label") val label: String,
    )
}