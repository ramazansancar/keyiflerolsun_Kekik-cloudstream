// ! https://codeberg.org/coxju/cs-ext-coxju/src/branch/master/FullPorner/src/main/kotlin/com/coxju/FullPorner.kt
// ! https://github.com/SaurabhKaperwan/CSX/commits/master/FullPorner/src/main/kotlin/com/megix/FullPorner.kt

package com.keyiflerolsun

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class FullPorner : MainAPI() {
    override var mainUrl              = "https://fullporner.com"
    override var name                 = "FullPorner"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "${mainUrl}/home/"                       to "Featured",
        "${mainUrl}/category/hd-porn/"           to "HD",
        "${mainUrl}/category/amateur/"           to "Amateur",
        "${mainUrl}/category/teen/"              to "Teen",
        "${mainUrl}/category/cumshot/"           to "CumShot",
        "${mainUrl}/category/deepthroat/"        to "DeepThroat",
        "${mainUrl}/category/orgasm/"            to "Orgasm",
        "${mainUrl}/category/threesome/"         to "ThreeSome",
        "${mainUrl}/category/group-sex/"         to "Group Sex",

        "${mainUrl}/category/anal"               to "Anal",
        "${mainUrl}/category/arab"               to "Arab",
        "${mainUrl}/category/ass-licking"        to "Ass Licking",
        "${mainUrl}/category/atm"                to "ATM",
        "${mainUrl}/category/babe"               to "Babe",
        "${mainUrl}/category/babysitter"         to "Baby Sitter",
        "${mainUrl}/category/bbw"                to "BBW",
        "${mainUrl}/category/big-ass"            to "Big Ass",
        "${mainUrl}/category/big-tits"           to "Big Tits",
        "${mainUrl}/category/black"              to "Black",
        "${mainUrl}/category/blonde"             to "Blonde",
        "${mainUrl}/category/blowjob"            to "Blowjob",
        "${mainUrl}/category/bondage"            to "Bondage",
        "${mainUrl}/category/brunette"           to "Brunette",
        "${mainUrl}/category/bukkake"            to "Bukkake",
        "${mainUrl}/category/busty"              to "Busty",
        "${mainUrl}/category/casting"            to "Casting",
        "${mainUrl}/category/celebrities"        to "Celebrities",
        "${mainUrl}/category/college"            to "College",
        "${mainUrl}/category/compilation"        to "Compilation",
        "${mainUrl}/category/creampie"           to "Creampie",
        "${mainUrl}/category/cuckold"            to "CuckOld",
        "${mainUrl}/category/cum-swap"           to "Cum Swap",
        "${mainUrl}/category/czech"              to "Czech",
        "${mainUrl}/category/czech-massage"      to "Czech Massage",
        "${mainUrl}/category/doggystyle"         to "DoggyStyle",
        "${mainUrl}/category/dp"                 to "DP",
        "${mainUrl}/category/ebony"              to "Ebony",
        "${mainUrl}/category/fantasy"            to "Fantasy",
        "${mainUrl}/category/fetish"             to "Fetish",
        "${mainUrl}/category/fingering"          to "Fingering",
        "${mainUrl}/category/fisting"            to "Fisting",
        "${mainUrl}/category/footjob"            to "FootJob",
        "${mainUrl}/category/foursome"           to "FourSome",
        "${mainUrl}/category/gangbang"           to "GangBang",
        "${mainUrl}/category/gangbang-creampie"  to "GangBang Creampie",
        "${mainUrl}/category/gaping"             to "Gaping",
        "${mainUrl}/category/gay"                to "Gay",
        "${mainUrl}/category/german"             to "German",
        "${mainUrl}/category/gloryhole"          to "GloryHole",
        "${mainUrl}/category/hairy"              to "Hairy",
        "${mainUrl}/category/handjob"            to "HandJob",
        "${mainUrl}/category/hardcore"           to "HardCore",
        "${mainUrl}/category/hentai"             to "Hentai",
        "${mainUrl}/category/homemade"           to "HandMade",
        "${mainUrl}/category/hungarian"          to "Hungarian",
        "${mainUrl}/category/indian"             to "Indian",
        "${mainUrl}/category/interracial"        to "Interracial",
        "${mainUrl}/category/latina"             to "Latina",
        "${mainUrl}/category/lesbian"            to "Lesbian",
        "${mainUrl}/category/lingerie"           to "Lingerie",
        "${mainUrl}/category/massage"            to "Massage",
        "${mainUrl}/category/masturbation"       to "Masturbation",
        "${mainUrl}/category/mature"             to "Mature",
        "${mainUrl}/category/milf"               to "Milf",
        "${mainUrl}/category/office"             to "Office",
        "${mainUrl}/category/old-and-young"      to "Old and Young",
        "${mainUrl}/category/orgy"               to "Orgy",
        "${mainUrl}/category/outdoor"            to "OutDoor",
        "${mainUrl}/category/petite"             to "Petite",
        "${mainUrl}/category/pov"                to "Pov",
        "${mainUrl}/category/public"             to "Public",
        "${mainUrl}/category/pussy-licking"      to "Pussy Licking",
        "${mainUrl}/category/red-head"           to "Red Head",
        "${mainUrl}/category/riding"             to "Riding",
        "${mainUrl}/category/russian"            to "Russian",
        "${mainUrl}/category/school-girl"        to "School Girl",
        "${mainUrl}/category/skinny"             to "Skinny",
        "${mainUrl}/category/small-tits"         to "Small Tits",
        "${mainUrl}/category/solo"               to "Solo",
        "${mainUrl}/category/squirt"             to "Squirt",
        "${mainUrl}/category/strap-on"           to "Strap On",
        "${mainUrl}/category/swallow"            to "Swallow",
        "${mainUrl}/category/titfuck"            to "TitFuck",
        "${mainUrl}/category/toys"               to "Toys",
        "${mainUrl}/category/uniform"            to "Uniform",
        "${mainUrl}/category/vintage"            to "Vintage",
        "${mainUrl}/category/vr"                 to "VR",
        "${mainUrl}/category/webcam"             to "WebCam",
        "${mainUrl}/category/wife"               to "Wife",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home     = document.select("div.video-block div.video-card").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.video-card div.video-card-body div.video-title a")?.text() ?: return null
        val href      = fixUrl(this.selectFirst("div.video-card div.video-card-body div.video-title a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.select("div.video-card div.video-card-image a img").attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..15) {
            val document = app.get("${mainUrl}/search?q=${query.replace(" ", "+")}&p=$i").document

            val results = document.select("div.video-block div.video-card").mapNotNull { it.toSearchResult() }

            searchResponse.addAll(results)

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title     = document.selectFirst("div.video-block div.single-video-left div.single-video-title h2")?.text()?.trim().toString()
        val iframeUrl = fixUrlNull(document.selectFirst("div.video-block div.single-video-left div.single-video iframe")?.attr("src")) ?: ""

        val iframeDocument = app.get(iframeUrl).document

        val videoID          = Regex("""var id = \"(.+?)\"""").find(iframeDocument.html())?.groupValues?.get(1)
        val pornTrexDocument = app.get("https://www.porntrex.com/embed/${videoID}").document
        val matchResult      = Regex("""preview_url:\s*'([^']+)'""").find(pornTrexDocument.html())
        val poster           = matchResult?.groupValues?.get(1)
        val posterUrl        = fixUrlNull("https:$poster")

        val tags            = document.select("div.video-block div.single-video-left div.single-video-title p.tag-link span a").map { it.text() }
        val description     = document.selectFirst("div.video-block div.single-video-left div.single-video-title h2")?.text()?.trim().toString()
        val actors          = document.select("div.video-block div.single-video-left div.single-video-info-content p a").map { it.text() }
        val recommendations = document.select("div.video-block div.video-recommendation div.video-card").mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl       = posterUrl
            this.plot            = description
            this.tags            = tags
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document       = app.get(data).document
        val iframeUrl      = fixUrlNull(document.selectFirst("div.video-block div.single-video-left div.single-video iframe")?.attr("src")) ?: ""
        val extlinkList    = mutableListOf<ExtractorLink>()
        val iframeDocument = app.get(iframeUrl).document
        val videoID        = Regex("""var id = \"(.+?)\"""").find(iframeDocument.html())?.groupValues?.getOrNull(1)

        if (videoID != null) {
            val pornTrexDocument = app.get("https://www.porntrex.com/embed/$videoID").document
            val videoUrlsRegex   = Regex("""(?:video_url|video_alt_url2|video_alt_url3): \'(.+?)\',""")
            val matchResults     = videoUrlsRegex.findAll(pornTrexDocument.html())

            val videoUrls = matchResults.map { it.groupValues[1] }.toList()

            videoUrls.forEach { videoUrl ->
                extlinkList.add(
                    ExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                        referer = "",
                        quality = Regex("""_(1080|720|480|360)p\.mp4""").find(videoUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Qualities.Unknown.value
                    )
                )
            }
        }
        extlinkList.forEach(callback)

        return true
    }
}
