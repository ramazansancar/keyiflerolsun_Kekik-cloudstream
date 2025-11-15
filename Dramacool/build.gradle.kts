version = 8

cloudstream {
    authors     = listOf("kerimmkirac")
    language    = "en"
    description = "Watch drama asian Online for free releases in Korean, Taiwanese, Thailand, Japanese and Chinese with English subtitles on Dramacool."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("AsianDrama")
    iconUrl = "https://www.google.com/s2/favicons?domain=https://dramacool.com.tr&sz=%size%"
}