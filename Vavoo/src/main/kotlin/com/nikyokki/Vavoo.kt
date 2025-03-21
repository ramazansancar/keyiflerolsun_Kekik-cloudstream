package com.nikyokki

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class Vavoo() : MainAPI() {
    override var mainUrl = "https://vavoo.to"
    val proxyUrl = "https://wproxy.net"
    override var name = "Vavoo"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val vpnStatus = VPNStatus.MightBeNeeded

    private suspend fun getChannels(): List<Channel> {
        val mainReq = app.get(proxyUrl)
        cookie = mainReq.cookies["PHPSESSID"].toString()
        val document = app.get("https://sv1.wproxy.info/proxy.php/$mainUrl/channels", headers = headers,
            cookies = mapOf(
                "PHPSESSID" to cookie
            )).body.string()
        return parseJson<List<Channel>>(document)
    }

    companion object {
        var channels = emptyList<Channel>()
        var cookie = ""
        val headers = mapOf("Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Connection" to "keep-alive", "Referer" to "https://wproxy.net",
            "Sec-Fetch-Dest" to "document", "Sec-Fetch-Mode" to "navigate", "Sec-Fetch-Site" to "same-origin", "Sec-Fetch-User" to "?1",
            "Upgrade-Insecure-Requests" to "1", "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "sec-ch-ua" to "\"Chromium\";v=\"134\", \"Not:A-Brand\";v=\"24\", \"Google Chrome\";v=\"134\"", "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\"")

        @Suppress("ConstPropertyName")
        const val posterLink =
            "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/master/Huhu/tv.png"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (channels.isEmpty()) {
            channels = getChannels()
        }
        val sections =
            channels.groupBy { it.country }.map {
                HomePageList(
                    it.key,
                    it.value.map { channel -> channel.toSearchResponse(this.name) },
                    false
                )
            }.sortedBy { it.name }

        return newHomePageResponse(
            sections, false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (channels.isEmpty()) {
            channels = getChannels()
        }
        val matches = channels.filter { channel ->
            query.lowercase().replace(" ", "") in
                    channel.name.lowercase().replace(" ", "")
        }
        return matches.map { it.toSearchResponse(this.name) }
    }

    override suspend fun load(url: String): LoadResponse {
        Log.d("TV2", url)
        val channel = parseJson<Channel>(url)
        return newLiveStreamLoadResponse(channel.name, "$mainUrl/play/${channel.id}/index.m3u8", "$mainUrl/play/${channel.id}/index.m3u8") {
            this.posterUrl = posterLink
            this.tags = listOf(channel.country)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        Log.d("TV2", data)
        var url = data
        if (data.startsWith("https://vavoo.to")) {
            url = "https://sv1.wproxy.info/proxy.php/$data"
        }
        callback(
            ExtractorLink(
                this.name,
                this.name,
                url,
                headers = headers,
                referer = proxyUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }

    data class Channel(
        @JsonProperty("country")
        val country: String,
        @JsonProperty("id")
        val id: Long,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("p")
        val p: Int
    ) {
        fun toSearchResponse(apiName: String): LiveSearchResponse {
            return LiveSearchResponse(
                name,
                this.toJson(),
                apiName,
                posterUrl = posterLink
            )
        }
    }
}