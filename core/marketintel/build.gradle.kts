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
    // The public-feed client. `core:network` builds the app's OkHttp instances and this module now
    // makes one request of its own — to a third party, with the plain client, deliberately without
    // the auth interceptor. See `OkHttpPublicFeedClient`.
    implementation(libs.okhttp.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
