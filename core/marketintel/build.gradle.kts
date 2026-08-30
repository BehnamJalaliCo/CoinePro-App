plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.marketintel"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.retrofit.core)
    // `@SerializedName` on the two news fields the servers have not settled the spelling of. Gson is
    // already the converter this Retrofit instance is built with — see `NetworkFactory` — so this
    // puts the annotation on the compile classpath rather than adding a library to the app.
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
