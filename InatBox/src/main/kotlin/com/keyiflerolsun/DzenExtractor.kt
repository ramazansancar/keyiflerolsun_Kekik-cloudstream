
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class DzenRu : ExtractorApi(){
    override val name            = "DzenRu"
    override val mainUrl         = "https://dzen.ru/"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val document = app.get(
            url     = url,
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.7049.38 Mobile Safari/537.36",
			"X-Requested-With" to "XMLHttpRequest"),
            referer = this.mainUrl,
        ).document

        val script = document.select("script").find { it.data().contains("streams") }?.data() ?: ""
        val content = script.substringAfter("\"streams\":").substringBefore("],") + "]"
        val streams = tryParseJson<List<Stream>>(content)
        streams?.forEach { it ->
            val type = it.type ?: ""
            val streamUrl = it.url ?: ""
            Log.d("DZN", "Type -> $type")
            val quality = if (type.contains("fullhd"))  {
                Qualities.P1080.value
            } else if(type.contains("high")) {
                Qualities.P720.value
            } else if(type.contains("medium")) {
                Qualities.P480.value
            } else if(type.contains("low")) {
                Qualities.P360.value
            } else if(type.contains("lowest")) {
                Qualities.P240.value
            } else if(type.contains("tiny")) {
                Qualities.P144.value
            } else Qualities.Unknown.value

            val qualityName = Qualities.getStringByInt(quality)

            val extractorLink = when(type) {
                "hls" -> newExtractorLink(
                    source  = this.name + " - HLS",
                    name    = this.name + " - HLS",
                    url     = streamUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = quality
                }

                "dash" -> newExtractorLink(
                    source  = this.name + " - DASH",
                    name    = this.name + " - DASH",
                    url     = streamUrl,
                    ExtractorLinkType.DASH
                ) {
                    this.referer = ""
                    this.quality = quality
                }

                else -> newExtractorLink(
                    source  = this.name + " - " + qualityName,
                    name    = this.name + " - " + qualityName,
                    url     = streamUrl,
                    ExtractorLinkType.VIDEO
                ) {
                    this.referer = ""
                    this.quality = quality
                }
            }

            callback.invoke(extractorLink)
        }

    }
}

private data class Stream(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("type") val type: String? = null,
)