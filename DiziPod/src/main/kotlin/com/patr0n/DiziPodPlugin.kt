package com.patr0n

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DiziPodPlugin : Plugin() {
    override fun load(context: Context) {
        DiziPodHelper.mContext = context
        registerMainAPI(DiziPod())
    }
}
