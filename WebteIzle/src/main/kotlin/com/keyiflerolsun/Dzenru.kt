// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class MailRu : ExtractorApi() {
    override val name            = "DzenRu"
    override val mainUrl         = "https://dzen.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        Log.d("Kekik_${this.name}", "url » $url")

        val vidId     = url.substringAfter("embed/").trim()
        Log.d("Kekik_${this.name}", "vidId » $vidId")

        for (video in videoData.videos) {
            Log.d("Kekik_${this.name}", "video » $video")

            val videoUrl = if (video.url.startsWith("//")) "https:${video.url}" else video.url

            callback.invoke(
                newExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = videoUrl,
			) {
                this.headers = mapOf("Referer" to "${mainUrl}/")
                this.quality = Qualities.Unknown.value
            }
            )
        }
    }

    data class DzenRuData(
        @JsonProperty("provider") val provider: String,
        @JsonProperty("videos")   val videos: List<DzenRuVideoData>
    )

    data class DzenRuVideoData(
        @JsonProperty("url") val url: String,
    )
}
