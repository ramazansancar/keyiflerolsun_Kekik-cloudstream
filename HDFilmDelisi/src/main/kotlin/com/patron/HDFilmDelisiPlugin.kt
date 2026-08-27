package com.patron

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HDFilmDelisiPlugin : Plugin() {
    override fun load(context: Context) {
        val prefs = context.getSharedPreferences("DomainListesi", Context.MODE_PRIVATE)
        HDFilmDelisiHelper.setup(context)
        registerMainAPI(HDFilmDelisi(prefs))
        registerExtractorAPI(VidMody())
    }
}
