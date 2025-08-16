package com.kerimmkirac

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class DiziAsyaPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(DiziAsya())
        registerExtractorAPI(VidStack())
        registerExtractorAPI(DiziAsyauns())
        registerExtractorAPI(DiziAsyaP2P())
        registerExtractorAPI(DiziAsyarpmplay())
        registerExtractorAPI(LuluuExtractor())
        registerExtractorAPI(VidHidePro())
        registerExtractorAPI(movearnpre())
        
        registerExtractorAPI(mivalyo())
    }
}