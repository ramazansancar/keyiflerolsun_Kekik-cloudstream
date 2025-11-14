package com.kerimmkirac

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.CryptoJS
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

class PopcornVakti : YdxMolyStreamExtractor() {
    override var name    = "PopcornVakti"
    override var mainUrl = "https://ydf.popcornvakti.net"
}

class YDSheilaStream : YdxMolyStreamExtractor() {
    override var name    = "YDSheilaStream"
    override var mainUrl = "https://yd.sheila.stream"
}

open class YdxMolyStreamExtractor : ExtractorApi() {
    override val name            = "YdxMolyStream"
    override val mainUrl         = "https://ydx.molystream.org"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val link = if (url.contains("|")){
            url.split("|").first()
        } else {
            url
        }

        val dil =  if (url.contains("|")) {
            url.split("|").last()
        } else {
            ""
        }

        val extRef   = referer ?: ""
        val videoReq = app.get(link, referer=extRef, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0")).text
        val cryptData = Regex("""CryptoJS\.AES\.decrypt\("(.*)","""").find(videoReq)?.groupValues?.get(1) ?: ""
        val cryptPass = Regex("""","(.*)"\);""").find(videoReq)?.groupValues?.get(1) ?: ""
        val decryptedData = CryptoJS.decrypt(cryptPass, cryptData)
        val decryptedDoc = Jsoup.parse(decryptedData)
        val source = decryptedDoc.selectFirst("source")?.attr("src").toString()
        Log.d("kraptor_$name","source = $source")

        val m3uContent = app.get(
            source,
            referer = extRef
        ).text

        val m3u8Url = m3uContent.lineSequence()
            .firstOrNull { it.startsWith("http") }.toString()

        Log.d("kraptor_$name","m3u8Url = $m3u8Url")

        callback.invoke(
            newExtractorLink(
                source  = "${this.name} $dil",
                name    = "${this.name} $dil",
                url     = m3u8Url,
                type = ExtractorLinkType.M3U8
            ) {
                headers = mapOf("Referer" to source) // "Referer" ayarı burada yapılabilir
                quality = getQualityFromName(Qualities.Unknown.value.toString())
            }
        )
    }
}