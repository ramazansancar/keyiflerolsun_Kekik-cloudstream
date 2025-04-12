version = 1

cloudstream {
    authors     = listOf("keyiflerolsun")
	language    = "tr"
    description = "DDizi.im için Cloudstream eklentisi"
    
    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
	iconUrl = "https://www.google.com/s2/favicons?domain=www.ddizi.im&sz=%size%"
} 