package com.patron

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WebdramaTurkey2Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(WebdramaTurkey2())
        registerExtractorAPI(WebDramaTurkeyExtractor())
        registerExtractorAPI(VkExtractor())
        registerExtractorAPI(VkCom())
        registerExtractorAPI(Abstream())
    }
}
