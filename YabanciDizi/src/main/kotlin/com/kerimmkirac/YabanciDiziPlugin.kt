package com.kerimmkirac

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class YabanciDiziPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(YabanciDizi())
        registerExtractorAPI(YdxMolyStreamExtractor())
        registerExtractorAPI(PopcornVakti())
        registerExtractorAPI(YDSheilaStream())
    }
}