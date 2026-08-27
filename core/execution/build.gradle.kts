plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.execution"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.retrofit.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
