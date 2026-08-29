plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.feature.alerts"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:notifications"))
    // The webhook targets, their delivery log and the dispatcher behind «آزمایش». The alert centre
    // is where a webhook is created and where its failures are read, because a webhook is a
    // delivery channel for an alert and nothing else in this app produces one.
    implementation(project(":core:webhook"))
    // The alerts themselves and their audit log both live in stores here; see AlertsController.
    implementation(project(":core:datastore"))
    // Classification and artwork coverage, so the symbol picker can only offer markets that have a
    // logo. A symbol without artwork must never reach a list in this app.
    implementation(project(":core:symbols"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
