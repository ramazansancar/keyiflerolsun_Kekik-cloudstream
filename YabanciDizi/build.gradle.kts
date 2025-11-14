version = 3

cloudstream {
    authors     = listOf("kraptor")
    language    = "tr"
    description = "Son çıkan yabancı dizi ve filmleri yabancidizi' de izle. En yeni yabancı film ve diziler, türkçe altyazılı yada dublaj olarak 1080p kalitesinde hd izle."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://t0.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://yabancidizi.so/&size=128"
}