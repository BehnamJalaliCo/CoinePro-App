plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.datastore"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    // api, not implementation: DataStore<Preferences> is in ActivePlatformStore's constructor, so
    // anything that builds one needs the type on its own classpath.
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
