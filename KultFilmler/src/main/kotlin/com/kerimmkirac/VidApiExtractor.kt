package com.kerimmkirac

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

open class VidApi : ExtractorApi() {
    override val name            = "VidApi"
    override val mainUrl         = "https://vidpapi.xyz"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        Log.d("kraptor_$name","url geldi = $url")
        val data = url.substringAfter("video/")

        val istek   = "https://vidpapi.xyz/player/index.php?data=$data&do=getVideo"
        val SubLink = "https://vidpapi.xyz/player/index.php?data=$data"

//        Log.d("kraptor_$name","SubLink = $SubLink")

        val subIste = app.post(SubLink, headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With" to "XMLHttpRequest",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0"
        ), data = mapOf(
            "hash" to "$data",
            "r" to "https://kultfilmler.pro/"
        )).document

//        Log.d("kraptor_$name","subIste = $subIste")

        val subtitleAl = subIste.selectFirst("script:containsData(playerjsSubtitle)")?.data().toString()

        val regex = Regex(pattern = "var playerjsSubtitle = \"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))

        val regexMatch = regex.findAll(subtitleAl)

        for (altyazi in regexMatch) {
            val subtitle = altyazi.groupValues[1].substringAfter("]")
            val lang     = altyazi.groupValues[1].substringAfter("[").substringBefore("]")
            val dil      =  if (lang.contains("Türkçe", ignoreCase = true)){
                "Turkish"
            } else if (lang.contains("İngilizce", ignoreCase = true)) {
                "English"
            } else {
                lang
            }
            subtitleCallback.invoke(SubtitleFile(lang = dil, url = subtitle))
        }

        Log.d("kraptor_$name","subtitleAl = $subtitleAl")

        val document = app.post(istek, headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With" to "XMLHttpRequest",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0"
        ), data = mapOf(
            "hash" to "$data",
            "r" to "https://kultfilmler.pro/"
        )).document.body().text()

//        Log.d("kraptor_$name","document = $document")

        val obj = JSONObject(document)
        // securedLink varsa onu tercih et, yoksa videoSource
        val secured = obj.optString("securedLink").takeIf { it.isNotBlank() }
        Log.d("kraptor_$name","secured = $secured")
        val source = obj.optString("videoSource").takeIf { it.isNotBlank() }
        Log.d("kraptor_$name","source = $source")

        listOfNotNull(secured, source)
            .distinct()
            .forEach { link ->
            callback.invoke(newExtractorLink(
                source = name,
                name   = name,
                url    = link,
                type   = ExtractorLinkType.M3U8,
            ))
            }
    }
}
