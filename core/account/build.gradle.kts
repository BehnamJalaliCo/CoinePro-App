plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.account"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.retrofit.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
