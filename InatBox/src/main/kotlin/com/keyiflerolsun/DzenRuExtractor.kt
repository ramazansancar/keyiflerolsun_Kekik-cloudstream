package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.mozilla.javascript.ast.Name

class DzenRu : ExtractorApi(){
    override val name            = "Dzen"
    override val mainUrl         = "https://dzen.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        Log.d("kraptor_${this.name}","url = $url")

        val document = app.get(url, referer = referer).text

        val regex = Regex(pattern = "\\{\"url\":\"([^\"]*)\",\"type\":\"([^\"]*)\"\\}", options = setOf(RegexOption.IGNORE_CASE))

        val regMatch = regex.findAll(document)

        regMatch.forEach { matchResult ->

            val url = matchResult.groupValues[1]
            val type = matchResult.groupValues[2]
            val typeNe = if (type.contains("dash", ignoreCase = true)){
                ExtractorLinkType.DASH
            } else if (type.contains("hls", ignoreCase = true)){
                ExtractorLinkType.M3U8
            } else {
                INFER_TYPE
            }
            Log.d("kraptor_${this.name}","url = $url type = $type")
            Log.d("kraptor_${this.name}","type ne = $typeNe")

            val kaliteKontrol = url.substringAfterLast("=")
            val kalite = if (kaliteKontrol.contains("tiny")){
                "256"
            } else if (kaliteKontrol.contains("lowest")){
                "426"
            } else if (kaliteKontrol.contains("low")){
                "640"
            } else if (kaliteKontrol.contains("medium")){
                "852"
            } else if (kaliteKontrol.contains("high")){
                "1280"
            } else if (kaliteKontrol.contains("fullhd")){
                "1920"
            } else {
                ""
            }

            callback.invoke(newExtractorLink(
                source = name,
                name   = name,
                url    = url,
                type   = typeNe
            ) {
                this.referer = "${mainUrl}/"
                this.quality = getQualityFromName(kalite)
            })


        }

    }
}