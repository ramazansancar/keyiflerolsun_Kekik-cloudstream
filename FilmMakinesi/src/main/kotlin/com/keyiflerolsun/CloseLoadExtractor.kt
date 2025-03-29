// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Base64

private fun getm3uLink(data: String): String {
    val first  = Base64.decode(data,Base64.DEFAULT).reversedArray()
    val second = Base64.decode(first, Base64.DEFAULT)
    val result = second.toString(Charsets.UTF_8).split("|")[1]

    return result
}

open class CloseLoad : ExtractorApi() {
    override val name            = "CloseLoad"
    override val mainUrl         = "https://closeload.filmmakinesi.de"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        Log.d("Kekik_${this.name}", "url » $url")

        val iSource = app.get(url, referer = extRef)

        iSource.document.select("track").forEach {
            subtitleCallback.invoke(
                SubtitleFile(
                    lang = it.attr("label"),
                    url  = fixUrl(it.attr("src"))
                )
            )
        }

        val obfuscatedScript = iSource.document.select("script[type=text/javascript]")[1].data().trim()
        val rawScript        = getAndUnpack(obfuscatedScript)
        val regex = Regex("var player=this\\}\\);var(.*?);myPlayer\\.src")
        val matchResult = regex.find(rawScript)
        if (matchResult != null) {
            val extractedString = matchResult.groups[1]?.value?.trim()?.substringAfter("=\"")?.substringBefore("\"")
            val m3uLink = Base64.decode(extractedString, Base64.DEFAULT).toString(Charsets.UTF_8)
            Log.d("Kekik_${this.name}", "m3uLink » $m3uLink")

            callback.invoke(
                ExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = m3uLink,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8  = true
                )
            )
        } else {
            println("No match found")
        }

    }
}
