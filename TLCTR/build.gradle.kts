version = 1

cloudstream {
    authors     = listOf("nikyokki")
    language    = "tr"
    description = "TLC TV dizi ve programlarını takip etmek için ziyaret edin. TLC TV dizi ve programlarını tek parça ve hd kalitesiyle izleyin."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.tlctv.com.tr/favicon.ico"
}