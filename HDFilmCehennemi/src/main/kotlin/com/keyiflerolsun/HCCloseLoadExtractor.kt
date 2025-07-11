package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

open class HCCloseLoadExtractor : ExtractorApi() {
    override val name            = "CloseLoad"
    override val mainUrl         = "https://hdfilmcehennemi.mobi"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        Log.d("Kekik_${this.name}", "url » $url")

        val iSource = app.get(url, referer = extRef)

        iSource.document.select("track").forEach {
            subtitleCallback.invoke(
                SubtitleFile(
                    lang = it.attr("label"),
                    url = "https://www.hdfilmcehennemi.mobi" + it.attr("src")
                )
            )
        }

        val obfuscatedScript = iSource.document.select("script[type=text/javascript]")[1].data().trim()
        val rawScript        = getAndUnpack(obfuscatedScript)
        Log.d("Kekik_${this.name}", "rawScript » $rawScript")
        val regex = Regex("var player=this\\}\\);var(.*?);myPlayer\\.src")
        val matchResult = regex.find(rawScript)
        val base64Input = rawScript.substringAfter("dc_hello(\"").substringBefore("\");")
        val lastUrl = dcHello(base64Input)
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = lastUrl,
                ExtractorLinkType.M3U8
            ) {
                this.referer = mainUrl
                this.quality = Qualities.Unknown.value
            }
        )
        if (matchResult != null) {
            val extractedString = matchResult.groups[1]?.value?.trim()?.substringAfter("=\"")?.substringBefore("\"")
            val m3uLink = Base64.decode(extractedString, Base64.DEFAULT).toString(Charsets.UTF_8)
            Log.d("Kekik_${this.name}", "m3uLink » $m3uLink")

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = m3uLink,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        } else {
            println("No match found")
        }
    }

    fun dcHello(base64Input: String): String {
        val decodedOnce = base64Decode(base64Input)
        val reversedString = decodedOnce.reversed()
        val decodedTwice = base64Decode(reversedString)
        return decodedTwice.split("|")[1]
    }
}