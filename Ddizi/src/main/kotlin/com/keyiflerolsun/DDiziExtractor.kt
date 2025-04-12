package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class DDiziExtractor : ExtractorApi() {
    override val name            = "DDizi"
    override val mainUrl         = "https://www.ddizi.im"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef  = referer ?: ""
        val iSource = app.get(url, referer=extRef).text
        Log.d("DDZ_EXT", "iSource » ${iSource}")

        val m3uLink = Regex("""file:\"([^\"]+)""").find(iSource)?.groupValues?.get(1)
        Log.d("DDZ_EXT", "m3uLink » ${m3uLink}")

        callback.invoke(
            newExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = m3uLink ?: throw ErrorLoadingException("m3u link not found"),
                type = ExtractorLinkType.M3U8
            ) {
                headers = mapOf("Referer" to url)
                quality = Qualities.Unknown.value,
            }
        )
    }
} 