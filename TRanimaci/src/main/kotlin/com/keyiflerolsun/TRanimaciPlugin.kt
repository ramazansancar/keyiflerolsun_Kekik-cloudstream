package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TRanimaciPlugin: Plugin() {
    companion object {
        var pluginContext: Context? = null
    }
    override fun load(context: Context) {
        pluginContext = context
        registerMainAPI(TRanimaci())
    }
}