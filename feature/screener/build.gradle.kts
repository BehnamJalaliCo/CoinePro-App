plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.feature.screener"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:marketdata"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:symbols"))
    // The indicator library, for [109]. Nothing of `core:chart`'s Compose surface is used and none
    // of its types appear in this module's own API — the screener asks it for arithmetic over a
    // list of bars and hands back plain doubles, so a caller never has to know it is here.
    implementation(project(":core:chart"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
