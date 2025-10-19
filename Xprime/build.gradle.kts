version = 4

cloudstream {
    authors     = listOf("kerimmkirac")
    language    = "en"
    description = "Watch XPrime films TV programmes online or stream right to your smart TV, game console, PC, Mac, mobile, tablet and more"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries","Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=xprime.tv&sz=%size%"
}