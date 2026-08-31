plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.community"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    // The academy-scoped token. `/academy/community` sits behind exactly the same gate as the rest
    // of the academy, so this module borrows `AcademyTokenStore` rather than minting a second
    // credential of its own — see NetworkCommunityGateway for why that matters.
    implementation(project(":core:marketdata"))
    implementation(project(":core:network"))
    implementation(libs.retrofit.core)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.retrofit.gson)
    testImplementation(libs.okhttp.core)
}
