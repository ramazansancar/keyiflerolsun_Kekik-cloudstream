version = 2

cloudstream {
    authors     = listOf("kerimmkirac")
    language    = "tr"
    description = "Kore, Çin ve Japon dizilerini tek tıkla HD ve Türkçe altyazılı izle. En güncel Asya dizileri, unutulmaz Asya filmleri ve web dramalar seni bekliyor."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("AsianDrama","Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=asyawatch.com&sz=%size%"
}