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
    authors     = listOf("kerimmkirac")
    language    = "tr"
    description = "Vavoo Türkiye kanalları."

    status  = 1 
    tvTypes = listOf("Live")
    iconUrl = "https://raw.githubusercontent.com/patr0nq/link/refs/heads/main/tv-logo/vavoo.png"
}