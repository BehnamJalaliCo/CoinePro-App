plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.webhook"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // api, not implementation: `DataStore<Preferences>` is in WebhookStore's constructor, so
    // anything that builds one needs the type on its own classpath. The same reasoning
    // `core:datastore` writes down for its own dependency.
    api(libs.androidx.datastore.preferences)
    api(libs.kotlinx.coroutines.core)
    // The poster owns its own client rather than sharing the app's: its timeouts are three seconds
    // and its calls carry no bearer token. See WebhookPoster.
    implementation(libs.okhttp.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
