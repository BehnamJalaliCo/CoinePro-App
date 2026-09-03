plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.feature.community"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))
    // The board itself. One module, one platform: `core:community` has no TradeYar implementation
    // because TradeYar has no community routes, and this screen is never reached there — see
    // CommunityScreen and the `absent` set in the shell.
    implementation(project(":core:community"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(libs.kotlinx.coroutines.core)
    // The system photo picker, for the composer's «عکس». `PickVisualMedia` is the contract Android
    // added precisely so an app can ask for one image without asking for permission to read every
    // file on the phone — this app declares no storage permission and does not need one.
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
}
