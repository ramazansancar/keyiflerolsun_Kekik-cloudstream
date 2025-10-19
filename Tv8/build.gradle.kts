version = 2

cloudstream {
    authors     = listOf("kerimmkirac")
    language    = "tr"
    description = "TV8'in eğlenceli programları, yarışmaları ve dizileri ile keyifle TV izle. Survivor, O Ses Türkiye, Jet Sosyete, Kızım, Yemekteyiz ve Masterchef için TV8 izle."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.tv8.com.tr&sz=%size%"
}