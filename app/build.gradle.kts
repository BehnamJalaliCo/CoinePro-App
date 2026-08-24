plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val configuredVersionCode = providers.gradleProperty("COINEPRO_VERSION_CODE")
    .orElse("1")
    .get()
    .toIntOrNull()
    ?: error("COINEPRO_VERSION_CODE must be an integer.")
require(configuredVersionCode > 0) { "COINEPRO_VERSION_CODE must be positive." }

val configuredVersionName = providers.gradleProperty("COINEPRO_VERSION_NAME")
    .orElse("0.1.0")
    .get()
require(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$").matches(configuredVersionName)) {
    "COINEPRO_VERSION_NAME must use semantic version form, for example 1.2.3 or 1.2.3-rc.1."
}

val debugApiBaseUrl = providers.gradleProperty("COINEPRO_DEBUG_API_BASE_URL")
    .orElse("https://debug.example.invalid/")
    .get()
val debugFirebaseProjectId = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_PROJECT_ID").orElse("").get()
val debugFirebaseApplicationId = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_APPLICATION_ID").orElse("").get()
val debugFirebaseApiKey = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_API_KEY").orElse("").get()
val debugFirebaseSenderId = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_SENDER_ID").orElse("").get()

val stagingApiBaseUrl = providers.gradleProperty("COINEPRO_STAGING_API_BASE_URL")
    .orElse("https://staging.example.invalid/")
    .get()
val stagingFirebaseProjectId = providers.gradleProperty("COINEPRO_STAGING_FIREBASE_PROJECT_ID").orElse("").get()
val stagingFirebaseApplicationId = providers.gradleProperty("COINEPRO_STAGING_FIREBASE_APPLICATION_ID").orElse("").get()
val stagingFirebaseApiKey = providers.gradleProperty("COINEPRO_STAGING_FIREBASE_API_KEY").orElse("").get()
val stagingFirebaseSenderId = providers.gradleProperty("COINEPRO_STAGING_FIREBASE_SENDER_ID").orElse("").get()

val productionApiBaseUrl = providers.gradleProperty("COINEPRO_PRODUCTION_API_BASE_URL")
    .orElse("https://production.example.invalid/")
    .get()
val productionFirebaseProjectId = providers.gradleProperty("COINEPRO_PRODUCTION_FIREBASE_PROJECT_ID").orElse("").get()
val productionFirebaseApplicationId = providers.gradleProperty("COINEPRO_PRODUCTION_FIREBASE_APPLICATION_ID").orElse("").get()
val productionFirebaseApiKey = providers.gradleProperty("COINEPRO_PRODUCTION_FIREBASE_API_KEY").orElse("").get()
val productionFirebaseSenderId = providers.gradleProperty("COINEPRO_PRODUCTION_FIREBASE_SENDER_ID").orElse("").get()

require(stagingApiBaseUrl != productionApiBaseUrl) {
    "Staging and production API base URLs must be different."
}

val releaseStoreFile = providers.gradleProperty("COINEPRO_RELEASE_STORE_FILE").orNull?.takeIf { it.isNotBlank() }
val releaseStorePassword = providers.gradleProperty("COINEPRO_RELEASE_STORE_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val releaseKeyAlias = providers.gradleProperty("COINEPRO_RELEASE_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() }
val releaseKeyPassword = providers.gradleProperty("COINEPRO_RELEASE_KEY_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningConfigured = releaseSigningValues.all { it != null }
require(releaseSigningValues.none { it != null } || releaseSigningConfigured) {
    "Release signing is partially configured. Supply all COINEPRO_RELEASE_* signing properties or none."
}

fun escapedBuildConfig(value: String): String = "\"${value.replace("\"", "\\\"")}\""

android {
    namespace = "com.coinepro.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.coinepro.app"
        minSdk = 26
        targetSdk = 36
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(checkNotNull(releaseStoreFile))
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BUILD_ENVIRONMENT", escapedBuildConfig("debug"))
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
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "BUILD_ENVIRONMENT", escapedBuildConfig("production"))
            buildConfigField("String", "API_BASE_URL", escapedBuildConfig(productionApiBaseUrl))
            buildConfigField("String", "FIREBASE_PROJECT_ID", escapedBuildConfig(productionFirebaseProjectId))
            buildConfigField("String", "FIREBASE_APPLICATION_ID", escapedBuildConfig(productionFirebaseApplicationId))
            buildConfigField("String", "FIREBASE_API_KEY", escapedBuildConfig(productionFirebaseApiKey))
            buildConfigField("String", "FIREBASE_SENDER_ID", escapedBuildConfig(productionFirebaseSenderId))
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += listOf("release")
            signingConfig = if (releaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            buildConfigField("String", "BUILD_ENVIRONMENT", escapedBuildConfig("staging"))
            buildConfigField("String", "API_BASE_URL", escapedBuildConfig(stagingApiBaseUrl))
            buildConfigField("String", "FIREBASE_PROJECT_ID", escapedBuildConfig(stagingFirebaseProjectId))
            buildConfigField("String", "FIREBASE_APPLICATION_ID", escapedBuildConfig(stagingFirebaseApplicationId))
            buildConfigField("String", "FIREBASE_API_KEY", escapedBuildConfig(stagingFirebaseApiKey))
            buildConfigField("String", "FIREBASE_SENDER_ID", escapedBuildConfig(stagingFirebaseSenderId))
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            buildConfigField("String", "BUILD_ENVIRONMENT", escapedBuildConfig("benchmark"))
            buildConfigField("String", "API_BASE_URL", escapedBuildConfig("https://benchmark.example.invalid/"))
            buildConfigField("String", "FIREBASE_PROJECT_ID", escapedBuildConfig(""))
            buildConfigField("String", "FIREBASE_APPLICATION_ID", escapedBuildConfig(""))
            buildConfigField("String", "FIREBASE_API_KEY", escapedBuildConfig(""))
            buildConfigField("String", "FIREBASE_SENDER_ID", escapedBuildConfig(""))
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
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
    implementation(libs.androidx.profileinstaller)
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
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
