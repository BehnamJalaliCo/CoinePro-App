plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val debugApiBaseUrl = providers.gradleProperty("COINEPRO_DEBUG_API_BASE_URL")
    .orElse("https://example.invalid/")
    .get()
val debugFirebaseProjectId = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_PROJECT_ID").orElse("").get()
val debugFirebaseApplicationId = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_APPLICATION_ID").orElse("").get()
val debugFirebaseApiKey = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_API_KEY").orElse("").get()
val debugFirebaseSenderId = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_SENDER_ID").orElse("").get()

val releaseApiBaseUrl = providers.gradleProperty("COINEPRO_API_BASE_URL")
    .orElse("https://example.invalid/")
    .get()
val releaseFirebaseProjectId = providers.gradleProperty("COINEPRO_FIREBASE_PROJECT_ID").orElse("").get()
val releaseFirebaseApplicationId = providers.gradleProperty("COINEPRO_FIREBASE_APPLICATION_ID").orElse("").get()
val releaseFirebaseApiKey = providers.gradleProperty("COINEPRO_FIREBASE_API_KEY").orElse("").get()
val releaseFirebaseSenderId = providers.gradleProperty("COINEPRO_FIREBASE_SENDER_ID").orElse("").get()

fun escapedBuildConfig(value: String): String = "\"${value.replace("\"", "\\\"")}\""

android {
    namespace = "com.coinepro.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.coinepro.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", escapedBuildConfig(debugApiBaseUrl))
            buildConfigField("String", "FIREBASE_PROJECT_ID", escapedBuildConfig(debugFirebaseProjectId))
            buildConfigField("String", "FIREBASE_APPLICATION_ID", escapedBuildConfig(debugFirebaseApplicationId))
            buildConfigField("String", "FIREBASE_API_KEY", escapedBuildConfig(debugFirebaseApiKey))
            buildConfigField("String", "FIREBASE_SENDER_ID", escapedBuildConfig(debugFirebaseSenderId))
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "API_BASE_URL", escapedBuildConfig(releaseApiBaseUrl))
            buildConfigField("String", "FIREBASE_PROJECT_ID", escapedBuildConfig(releaseFirebaseProjectId))
            buildConfigField("String", "FIREBASE_APPLICATION_ID", escapedBuildConfig(releaseFirebaseApplicationId))
            buildConfigField("String", "FIREBASE_API_KEY", escapedBuildConfig(releaseFirebaseApiKey))
            buildConfigField("String", "FIREBASE_SENDER_ID", escapedBuildConfig(releaseFirebaseSenderId))
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:security"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:marketdata"))
    implementation(project(":core:signals"))
    implementation(project(":core:notifications"))
    implementation(project(":core:execution"))
    implementation(project(":core:aisignal"))
    implementation(project(":core:aivision"))
    implementation(project(":core:aiassistant"))
    implementation(project(":core:marketintel"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:signals"))
    implementation(project(":feature:signal-detail"))
    implementation(project(":feature:connections"))
    implementation(project(":feature:execution"))
    implementation(project(":feature:ai"))
    implementation(project(":feature:ai-vision"))
    implementation(project(":feature:ai-assistant"))
    implementation(project(":feature:news"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:tools"))
    implementation(project(":feature:activity"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
