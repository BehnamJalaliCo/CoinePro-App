plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.signals"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    implementation(libs.retrofit.core)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
