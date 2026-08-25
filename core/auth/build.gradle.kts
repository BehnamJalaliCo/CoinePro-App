plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.auth"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.retrofit.core)
    // For @SerializedName: the two backends spell the same profile object differently, and one
    // naming policy cannot read both.
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.gson)
    testImplementation(libs.kotlinx.coroutines.test)
}
