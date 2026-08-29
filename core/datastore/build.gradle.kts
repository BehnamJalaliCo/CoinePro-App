plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.datastore"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    // api: NotificationSettings and LocalPriceAlert are in these stores' signatures.
    api(project(":core:notifications"))
    // api, not implementation: DataStore<Preferences> is in ActivePlatformStore's constructor, so
    // anything that builds one needs the type on its own classpath.
    api(libs.androidx.datastore.preferences)
    // api: PaperLedgerPrefStore implements `PaperLedgerStore`, so anything binding one in Hilt
    // needs the interface on its own classpath. No cycle — papertrade does not reach datastore.
    api(project(":core:papertrade"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
