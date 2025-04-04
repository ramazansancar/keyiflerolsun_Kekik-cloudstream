version = 1

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

android {
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    authors     = listOf("GitLatte", "patr0nq")
    language    = "tr"
    description = "Oha.to Türkiye televizyon listesi. @keyiflerolsun Canlı TV eklentisinden yararlanılmıştır."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live")
    iconUrl = "https://raw.githubusercontent.com/patr0nq/link/refs/heads/main/tv-logo/oha.png"
}