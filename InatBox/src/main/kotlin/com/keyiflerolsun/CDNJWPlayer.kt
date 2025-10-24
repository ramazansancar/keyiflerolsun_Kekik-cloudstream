package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class CDNJWPlayer : ExtractorApi() {
    override val name: String = "CDN JWPlayer"
    override val mainUrl: String = "https://cdn.jwplayer.com"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        callback.invoke(
            ExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = url,
                referer = referer ?: "",
                quality = Qualities.Unknown.value,
                type    = ExtractorLinkType.M3U8
            )
        )
    }
}