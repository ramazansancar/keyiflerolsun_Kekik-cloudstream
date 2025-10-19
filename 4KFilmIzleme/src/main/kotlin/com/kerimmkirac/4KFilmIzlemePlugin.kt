
package com.kerimmkirac


import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class `4KFilmIzlemePlugin`: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(`4KFilmIzleme`())
        registerExtractorAPI(ContentX())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(RapidVid())
        registerExtractorAPI(TRsTX())
        registerExtractorAPI(VidMoxy())
        registerExtractorAPI(Sobreatsesuyp())
        registerExtractorAPI(TurboImgz())
        registerExtractorAPI(TurkeyPlayer())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(FourCX())
        registerExtractorAPI(PlayRu())
        registerExtractorAPI(FourPlayRu())
        registerExtractorAPI(FourPichive())
        registerExtractorAPI(Pichive())
        registerExtractorAPI(FourDplayer())
        registerExtractorAPI(SNDplayer())
        registerExtractorAPI(FourPichiveOnline())
        registerExtractorAPI(ORGDplayer())
        registerExtractorAPI(GoogleDriveExtractor())
        registerExtractorAPI(VidMolyExtractor())
        registerExtractorAPI(VidMolyTo())

    }
}
