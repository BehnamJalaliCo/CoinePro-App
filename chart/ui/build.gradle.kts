plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.core.chart"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Robolectric with native graphics, for the one test that has to draw: the static layer's
    // bitmap cache is only provably a cache with real pixels behind it.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    // The engine, re-exported: every consumer of the Compose chart also speaks its types.
    api(project(":chart-core"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // The stylus's next point, predicted from its recent path — see the freehand tool.
    implementation(libs.androidx.input.motionprediction)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
