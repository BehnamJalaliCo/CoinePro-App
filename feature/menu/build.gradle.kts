plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.feature.menu"
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
    implementation(project(":core:common"))
    // api rather than implementation: `MenuAccess` carries a `MarketPlatform` and the header takes
    // an `AvatarSpec`, and both are parameters of `MenuScreen` — whoever builds one needs the types.
    api(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
}
