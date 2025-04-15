
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class DzenRu : ExtractorApi() {
    override val name            = "DzenRu"
    override val mainUrl         = "https://dzen.ru"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef   = referer ?: ""
        val videoKey = url.split("/").last()
        val videoUrl = "${mainUrl}/embed/${videoKey}"

        val api = app.get(videoUrl).parsedSafe<DzenRuUrls>() ?: throw ErrorLoadingException("DzenRu")

        for (video in api.urls) {
            callback.invoke(
                newExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = video.url,
                ) {
                    this.referer = extRef
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }

    data class DzenRuUrls(
        @JsonProperty("urls") val urls: List<DzenRuData>
    )

    data class DzenRuData(
        @JsonProperty("url")   val url: String,
        @JsonProperty("label") val label: String,
    )
}
