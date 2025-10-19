package com.kerimmkirac

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SelcukFlixPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(SelcukFlix())
        registerExtractorAPI(ContentX())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(RapidVid())
        registerExtractorAPI(TRsTX())
        registerExtractorAPI(VidMoxy())
        registerExtractorAPI(Sobreatsesuyp())
        registerExtractorAPI(TurboImgz())
        registerExtractorAPI(TurkeyPlayer())
        registerExtractorAPI(FourPichiveOnline())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(FourCX())
        registerExtractorAPI(PlayRu())
        registerExtractorAPI(FourPlayRu())
        registerExtractorAPI(FourPichive())
        registerExtractorAPI(Pichive())
        registerExtractorAPI(FourDplayer())
        registerExtractorAPI(SNDplayer())
        registerExtractorAPI(ORGDplayer())
        registerExtractorAPI(SnHotLinger())
        registerExtractorAPI(SNDPlayer74())
        registerExtractorAPI(VidMolyExtractor())
        registerExtractorAPI(VidMolyTo())
    }
}