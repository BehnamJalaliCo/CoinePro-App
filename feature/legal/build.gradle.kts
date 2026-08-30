plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.feature.legal"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    // BidiText only. A URL or an identifier dropped into a Persian paragraph takes the paragraph's
    // direction and drags its punctuation to the wrong side; the isolates fix it.
    implementation(project(":core:common"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // The parser is plain Kotlin with no Android types in it, which is the whole reason it can be
    // tested this cheaply — no Robolectric, no instrumentation, no fixture device.
    testImplementation(libs.junit)
}
