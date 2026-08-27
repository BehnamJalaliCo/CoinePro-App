plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.backtest"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:chart"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
