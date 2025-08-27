// use an integer for version numbers
version = 2

android {
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "Netflix, PrimeVideo, Disney+"
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

    iconUrl = "https://net2025.cc/mobile/img/nf2/icon_x192.png"
}
