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
    description = "TO Grubu spor kanallarını içerir: Vavoo.to, Huhu.to, Kool.to, Oha.to"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live")
    iconUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTGJ7I3L6IGdd4RBNdkdVFMIX0Br366si4Q6w&s"
}