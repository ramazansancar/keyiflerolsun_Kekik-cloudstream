package com.nikyokki

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class Vavoo() : MainAPI() {
    override var mainUrl = "https://vavoo.to"
    override var name = "Vavoo"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val vpnStatus = VPNStatus.MightBeNeeded

    private suspend fun getChannels(): List<Channel> {
        val document = app.get("$mainUrl/channels", headers = headers).body.string()
        return parseJson<List<Channel>>(document)
    }

    companion object {
        var channels = emptyList<Channel>()
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Connection" to "keep-alive",
            "Referer" to "https://vavoo.to",
            "User-Agent" to "VAVOO/1.0"
        )

        const val posterLink = "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/master/Huhu/tv.png"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (channels.isEmpty()) {
            channels = getChannels()
        }
        val sections = channels.groupBy { it.country }.map {
            HomePageList(it.key, it.value.map { channel -> channel.toSearchResponse(this.name) }, false)
        }.sortedBy { it.name }

        return newHomePageResponse(sections, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (channels.isEmpty()) {
            channels = getChannels()
        }
        val matches = channels.filter { channel ->
            query.lowercase().replace(" ", "") in channel.name.lowercase().replace(" ", "")
        }
        return matches.map { it.toSearchResponse(this.name) }
    }

    override suspend fun load(url: String): LoadResponse {
        Log.d("TV2", url)
        val channel = parseJson<Channel>(url)
        return newLiveStreamLoadResponse(channel.name, "$mainUrl/play/${channel.id}/index.m3u8", "$mainUrl/play/${channel.id}/index.m3u8") {
            this.posterUrl = posterLink
            this.tags = listOf(channel.country)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        Log.d("TV2", data)
        callback(
            ExtractorLink(
                this.name,
                this.name,
                data,
                headers = headers,
                referer = mainUrl,
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
