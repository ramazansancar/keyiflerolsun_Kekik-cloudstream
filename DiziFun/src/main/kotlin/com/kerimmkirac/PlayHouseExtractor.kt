package com.kerimmkirac

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

open class PlayHouse : ExtractorApi() {
    override val name            = "PlayHouse"
    override val mainUrl         = "https://playhouse.premiumvideo.click"
    override val requiresReferer = true
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
//        Log.d("kerim","url geldi = $url")
        val extRef   = referer ?: ""
        val urlAl = app.get(url, referer =extRef, allowRedirects = true).url
        val document = app.get(urlAl, referer =extRef).text.substringAfter("var player").substringBefore("client:")
//        Log.d("kerim","document = $document")
        val regexm3  = Regex(pattern = "file: '([^']*)'", options = setOf(RegexOption.IGNORE_CASE))
        val regexMatch = regexm3.find(document)?.groupValues[1].toString()
        val host     = urlAl.substringBefore("/player")

        val videoLinki = "$host$regexMatch"
//        Log.d("kerim","videoLinki = $videoLinki")
        val m3u8     = if (videoLinki.contains("/null")){
//            Log.d("kerim","null geldi")
            null
        } else if (videoLinki.contains("leavemealonedmca")){
            null
        } else {
            videoLinki
        }

        val subtitleText = document.substringAfter("tracks: [")
        val subRegex  = Regex(pattern = "file: '([^']*)'", options = setOf(RegexOption.IGNORE_CASE))
        val subFind   = subRegex.findAll(subtitleText)

        subFind.forEach { subtitle ->
           val altyaziRaw = subtitle.groupValues[1]
           val altyazi    = "$host$altyaziRaw"
//            Log.d("kerim","altyazi = $altyazi")
           val dil        = if (altyaziRaw.contains("_eng")){
               "English"
           } else if (altyaziRaw.contains("_tur_forced")){
               "Turkish Forced"
           } else if (altyaziRaw.contains("_tur")) {
               "Turkish"
           } else {
               "Bilinmiyor"
           }
            subtitleCallback.invoke(SubtitleFile(dil, altyazi))
        }

        if (m3u8.isNullOrBlank()){
//            Log.d("kerim","null geldi siktir et")
        } else {
            val isim = if (url.contains("/armony/")){
                "Armony"
            } else {
                "House"
            }
            callback.invoke(newExtractorLink(
                source = "$name $isim",
                name   = "$name $isim",
                url    = m3u8,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = "${url}/"
            })
        }
    }
}
