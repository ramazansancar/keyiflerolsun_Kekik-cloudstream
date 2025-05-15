package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Vtbe : ExtractorApi() {
    override var name = "Vtbe"
    override var mainUrl = "https://vtbe.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url,referer=mainUrl).document
        val extractedpack =response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        JsUnpacker(extractedpack).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = link,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer ?: ""
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
        return null
    }
}

class wishfast : StreamWishExtractor() {
    override var mainUrl = "https://wishfast.top"
    override var name = "StreamWish"
}

class waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}

class FileMoonSx : Filesim() {
    override val mainUrl = "https://filemoon.sx"
    override val name = "FileMoonSx"
}


open class Ultrahd : ExtractorApi() {
    override var name = "Ultrahd Streamplay"
    override var mainUrl = "https://play.streamplay.co.in"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
            val response = app.get(url,referer=mainUrl).document
            val extractedpack =response.toString()
            val script = response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
            val unpacked = getAndUnpack(script ?: return)
            Log.d("DHS", "unpacked » $unpacked")
            // Regex to match href attributes in <a> tags within the servers list
            Regex("""(?i)(?:window\.|var\s+)?downloadURL\s*=\s*["']([^"']+)["']""").findAll(extractedpack).forEach { match ->
            val link = match.groupValues[1]
                Log.d("DHS", "Extracted link: $link")
                app.get(link).parsedSafe<Root>()?.sources?.map {
                    val m3u8= httpsify( it.file)
                    Log.d("DHS", "m3u8 » $m3u8")
                    Log.d("DHS", "Mapped source: $it")
                    if (m3u8.contains(".mpegurl"))
                    {
                        callback.invoke(
                            newExtractorLink(
                                "Ultrahd Streamplay",
                                "Ultrahd Streamplay",
                                url = m3u8,
                                INFER_TYPE
                            ) {
                                this.referer = ""
                                this.quality = getQualityFromName("")
                            }
                        )
                    }
                    else
                    {
                        M3u8Helper.generateM3u8(
                            this.name,
                            m3u8,
                            "$referer",
                        ).forEach(callback)
                    }
                }
                app.get(link).parsedSafe<Root>()?.tracks?.map {
                    val langurl=it.file
                    val lang=it.label
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang,  // Use label for the name
                            langurl     // Use extracted URL
                        )
                    )
                }
            }
    }
}

class Rumble : ExtractorApi() {
    override var name = "Rumble"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(
            url, referer = referer ?: "$mainUrl/"
        )
        Log.d("DHS", "response » $response")
        val playerScript =
            response.document.selectFirst("script:containsData(mp4)")?.data()
                ?.substringAfter("\"url\": \"")?.substringBefore("\"")?.replace("\\/", "/") ?:""
        val regex = """"url":"((?:[^\\"]|\\\/)*?)"|\"h\":(\d+)""".toRegex()
        val matches = regex.findAll(playerScript)
        for (match in matches) {
            val href = match.groupValues[1].replace("\\/", "/")
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = href,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getQualityFromName("")
                }
            )

        }
    }
}