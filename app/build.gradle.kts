plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val apiBaseUrl = providers.gradleProperty("COINEPRO_API_BASE_URL")
    .orElse("https://example.invalid/")
    .get()
val firebaseProjectId = providers.gradleProperty("COINEPRO_FIREBASE_PROJECT_ID").orElse("").get()
val firebaseApplicationId = providers.gradleProperty("COINEPRO_FIREBASE_APPLICATION_ID").orElse("").get()
val firebaseApiKey = providers.gradleProperty("COINEPRO_FIREBASE_API_KEY").orElse("").get()
val firebaseSenderId = providers.gradleProperty("COINEPRO_FIREBASE_SENDER_ID").orElse("").get()

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
        buildConfigField("String", "API_BASE_URL", escapedBuildConfig(apiBaseUrl))
        buildConfigField("String", "FIREBASE_PROJECT_ID", escapedBuildConfig(firebaseProjectId))
        buildConfigField("String", "FIREBASE_APPLICATION_ID", escapedBuildConfig(firebaseApplicationId))
        buildConfigField("String", "FIREBASE_API_KEY", escapedBuildConfig(firebaseApiKey))
        buildConfigField("String", "FIREBASE_SENDER_ID", escapedBuildConfig(firebaseSenderId))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    implementation(project(":core:marketdata"))
    implementation(project(":core:signals"))
    implementation(project(":core:notifications"))
    implementation(project(":core:execution"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:signals"))
    implementation(project(":feature:signal-detail"))
    implementation(project(":feature:connections"))
    implementation(project(":feature:execution"))
    implementation(project(":feature:ai"))
    implementation(project(":feature:tools"))
    implementation(project(":feature:activity"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
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
