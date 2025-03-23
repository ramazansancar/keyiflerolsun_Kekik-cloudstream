version = 1

cloudstream {
    authors     = listOf("kerimmkirac")
    language    = "tr"
    description = "Kore dizisi izle, çin dizisi izle, japon dizisi izle, kore dizileri izle, kore filmleri, asya dizileri, çin filmleri, japon filmleri, bl dizi izle, gl dizi izle!"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie","TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=webdramaturkey.net&sz=%size%"
}