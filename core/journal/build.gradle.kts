plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.journal"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:database"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
