plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.core.designsystem"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:common"))
    // api, not implementation: AvatarSpec is in CoineProAvatar's signature, so every screen
    // that draws one needs the type on its own classpath.
    api(project(":core:model"))
    // The index-to-country table. `core:symbols` is plain Kotlin with no Compose and no Android
    // resources — it depends on `core:model` and nothing else — so this direction introduces no
    // cycle, and the alternative was a second copy of the table living next to the drawables.
    implementation(project(":core:symbols"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    // Remote imagery — a logo the vendored artwork does not cover, an avatar. Disk-cached.
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
