// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class SibNet : ExtractorApi() {
    override val name            = "SibNet"
    override val mainUrl         = "https://video.sibnet.ru"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef  = referer ?: ""
        val iSource = app.get(url, referer=extRef).text
        var m3uLink = Regex("""player.src\(\[\{src: "([^"]+)""").find(iSource)?.groupValues?.get(1) ?: throw ErrorLoadingException("m3u link not found")

        m3uLink = "${mainUrl}${m3uLink}"
        Log.d("Kekik_${this.name}", "m3uLink » $m3uLink")

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                m3uLink,
                url,
                Qualities.Unknown.value,
                INFER_TYPE
            )
        )
    }
}