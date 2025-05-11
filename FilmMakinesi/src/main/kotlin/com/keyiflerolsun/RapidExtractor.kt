// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

private fun getm3uLink(data: String): String {
    val first  = Base64.decode(data,Base64.DEFAULT).reversedArray()
    val second = Base64.decode(first, Base64.DEFAULT)
    val result = second.toString(Charsets.UTF_8).split("|")[1]

    return result
}

open class RapidExtractor : ExtractorApi() {
    override val name            = "RapidRame"
    override val mainUrl         = "https://rapid.filmmakinesi.de"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        Log.d("Kekik_${this.name}", "url » $url")

        val iSource = app.get(url, referer = extRef)


        val script = iSource.document.select("script").find { it.data().contains("eval(function(p,a,c,k,e") }?.data() ?: ""
        val subData = script.substringAfter("tracks: ").substringBefore(",image")
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val captions: List<SubSource>? = subData.let { objectMapper.readValue(it) }
        if (!captions.isNullOrEmpty()) {
            captions.forEach {
                if (it.kind == "captions") {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            it.label.toString(),
                            fixUrl(it.file.toString())
                        )
                    )
                }
            }
        }
        val unpacked = JsUnpacker(script).unpack()
        Log.d("Kekik_${this.name}", "unpacked » $unpacked")
        val encoded = unpacked?.substringAfter("file_link=\"")?.substringBefore("\";")
        Log.d("Kekik_${this.name}", "encoded » $encoded")
        val decoded = base64Decode(encoded.toString().replace("\"", ""))
        Log.d("Kekik_${this.name}", "decoded » $decoded")
        val bytes = decoded.toByteArray(Charsets.UTF_8)
        val converted = String(bytes, Charsets.UTF_8)
        Log.d("Kekik_${this.name}", "converted » $converted")
        callback.invoke(
            newExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = converted,
                ExtractorLinkType.M3U8
            ) {
                this.referer = mainUrl
                this.quality = Qualities.Unknown.value
            }
        )

    }
}
private data class Source(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("type") val type: String? = null,
)

private data class SubSource(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("kind") val kind: String? = null,
    @JsonProperty("language") val language: String? = null,
)
