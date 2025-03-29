version = 2

cloudstream {
    authors     = listOf("patr0n")
    language    = "tr"
    description = "powerDizi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=tr.canlitv.team&sz=%size%"
}
