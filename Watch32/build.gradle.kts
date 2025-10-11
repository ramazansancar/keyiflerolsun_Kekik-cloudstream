version = 5

android {
    buildFeatures {
        buildConfig = true
    }
}


cloudstream {
    

    description = "Watch Your Favorite Movies TV Shows Online - Streaming For Free. With Movies TV Shows Full HD. Find Your Movies Watch NOW!"
    language = "en"
    authors = listOf("kerimmkirac")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1

    tvTypes = listOf("Movie","TvSeries")


    
    iconUrl = "https://img.watch32.sx/xxrz/400x400/100/a9/5e/a95e15a880a9df3c045f6a5224daf576/a95e15a880a9df3c045f6a5224daf576.png"
}
