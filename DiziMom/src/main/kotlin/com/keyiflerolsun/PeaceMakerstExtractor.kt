

package com.keyiflerolsun

import com.lagradost.api.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

open class PeaceMakerst : ExtractorApi() {
    override val name            = "PeaceMakerst"
    override val mainUrl         = "https://peacemakerst.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val m3uLink:String?
        val extRef  = referer ?: ""
        val postUrl = "${url}?do=getVideo"
        Log.d("kraptor_${this.name}", "postUrl » $postUrl")
        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")

        val response = app.post(
            postUrl,
            data = mapOf(
                "hash" to url.substringAfter("video/"),
                "r"    to extRef,
                "s"    to ""
            ),
            referer = extRef,
            headers = mapOf(
                "Content-Type"     to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
                "sec-ch-ua" to "Not/A)Brand\";v=\"8\", \"Chromium\";v=\"137\", \"Google Chrome\";v=\"137",
                "sec-ch-ua-mobile" to "?1",
                "sec-ch-ua-platform" to "Android"
            )
        )

        Log.d("kraptor_${this.name}", "response » $response")
        if (response.text.contains("teve2.com.tr\\/embed\\/")) {
            val teve2Id       = response.text.substringAfter("teve2.com.tr\\/embed\\/").substringBefore("\"")
            val teve2Response = app.get(
                "https://www.teve2.com.tr/action/media/${teve2Id}",
                referer = "https://www.teve2.com.tr/embed/${teve2Id}"
            ).parsedSafe<Teve2ApiResponse>() ?: throw ErrorLoadingException("teve2 response is null")

            m3uLink           = teve2Response.media.link.serviceUrl + "//" + teve2Response.media.link.securePath
        } else {
            val videoResponse = response.parsedSafe<PeaceResponse>() ?: throw ErrorLoadingException("peace response is null")
            val videoSources  = videoResponse.videoSources
            m3uLink = if (videoSources.isNotEmpty()) {
                videoSources.lastOrNull()?.file
            } else {
                null
            }
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = m3uLink ?: throw ErrorLoadingException("m3u link not found"),
                type = ExtractorLinkType.M3U8 // isM3u8 artık bu şekilde belirtiliyor
            ) {
                headers = mapOf("Referer" to url) // Eski "referer" artık headers içinde
                quality = Qualities.Unknown.value // Kalite ayarlandı
            }
        )
    }

    data class PeaceResponse(
        @JsonProperty("videoImage")   val videoImage: String?,
        @JsonProperty("videoSources") val videoSources: List<VideoSource>,
        @JsonProperty("sIndex")       val sIndex: String,
        @JsonProperty("sourceList")   val sourceList: Map<String, String>
    )

    data class VideoSource(
        @JsonProperty("file")  val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("type")  val type: String
    )

    data class Teve2ApiResponse(
        @JsonProperty("Media") val media: Teve2Media
    )

    data class Teve2Media(
        @JsonProperty("Link") val link: Teve2Link
    )

    data class Teve2Link(
        @JsonProperty("ServiceUrl") val serviceUrl: String,
        @JsonProperty("SecurePath") val securePath: String
    )
}