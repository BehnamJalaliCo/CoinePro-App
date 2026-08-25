plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.aivision"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:aisignal"))
    implementation(libs.retrofit.core)
    implementation(libs.gson)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
