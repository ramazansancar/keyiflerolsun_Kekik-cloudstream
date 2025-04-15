// use an integer for version numbers
version = 2

cloudstream {
    //language = "en"
    // All of these properties are optional, you can safely remove them

    description = "Netflix, Primevideo extensions"
    authors = listOf("kerimmkirac")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )

    iconUrl = "https://netfree.cc/mobile/img/nf2/C192.png"
}
