plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.symbols"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
}
