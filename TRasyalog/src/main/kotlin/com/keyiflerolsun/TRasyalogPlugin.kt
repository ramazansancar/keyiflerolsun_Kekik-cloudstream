package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TRasyalogPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TRasyalog())
    }
}