plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.network"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:common"))
    api(libs.okhttp.core)
    api(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    testImplementation(libs.junit)
}
