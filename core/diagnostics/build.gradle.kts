plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.diagnostics"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
