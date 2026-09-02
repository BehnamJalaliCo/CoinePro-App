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
    implementation(project(":core:network"))
    // The board's own identity — a key this app mints and a name the reader chose — lives in the
    // app's preferences. No platform token: the community belongs to neither platform, and
    // borrowing either's credential would tie it to that platform's account. See
    // CommunityIdentityStore.
    api(libs.androidx.datastore.preferences)
    implementation(libs.retrofit.core)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.retrofit.gson)
    testImplementation(libs.okhttp.core)
}
