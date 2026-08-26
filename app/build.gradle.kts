import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

/**
 * Firebase, only where it is actually configured.
 *
 * `google-services.json` carries a project's own identifiers and is deliberately not in the
 * repository — `.gitignore` and `scripts/security/scan-secrets.sh` both refuse it. But the Google
 * Services plugin fails the build outright when the file is missing, which would mean nobody could
 * build this app without first being handed a Firebase project.
 *
 * So the plugin is applied only when the file is there. A build without it compiles, runs, and
 * reports push as unconfigured — which is exactly what `PushCoordinator` already does, and is a
 * truthful state rather than a broken one.
 */
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

/**
 * The version, from `version.properties` — with `versionCode` derived rather than declared.
 *
 * Android asks `versionCode` one question: is it higher than the code already installed? If it is
 * not, the install is refused with "app not installed" and nothing says why. Keeping that integer
 * by hand next to a separate `versionName` fails in exactly one direction — the name gets bumped,
 * the code does not, and every device in the field quietly stops updating. So only the name is
 * written down, in `version.properties`, and the code is computed from it:
 *
 *     versionCode = MAJOR*10_000_000 + MINOR*100_000 + PATCH*1_000 + BUILD
 *
 * Each field has room for the ones below it, so a bump anywhere is strictly larger than anything
 * reachable underneath. `docs/VERSIONING.md` sets out the widths and why they are those widths;
 * `scripts/release/version.py` is the same arithmetic for CI and for the command line.
 *
 * BUILD is deliberately 0 here. It counts commits since the last version bump, which means asking
 * git, and a build that shells out to git is a build that behaves differently in a source tarball
 * than in a checkout. CI computes it and passes the whole code in with `-P`; a local build gets the
 * base code, which is correct, because a local build is not something anybody installs over.
 *
 * `-PCOINEPRO_VERSION_CODE` / `-PCOINEPRO_VERSION_NAME` still win when set. That is how CI supplies
 * the build-numbered code, and how `internal-release.yml` pins an exact version for a Play upload.
 */
val versionProperties = Properties().apply {
    val file = rootProject.file("version.properties")
    require(file.exists()) { "version.properties is missing; it is the source of truth for the app version." }
    file.inputStream().use { load(it) }
}

fun versionField(key: String, maximum: Int): Int {
    val raw = (versionProperties.getProperty(key) ?: error("version.properties is missing $key.")).trim()
    val number = raw.toIntOrNull() ?: error("version.properties $key must be an integer, not '$raw'.")
    require(number in 0..maximum) { "version.properties $key is $number; the scheme reserves $maximum for it." }
    return number
}

val versionMajor = versionField("MAJOR", 200)
val versionMinor = versionField("MINOR", 99)
val versionPatch = versionField("PATCH", 99)
val versionPreRelease = versionProperties.getProperty("PRE_RELEASE").orEmpty().trim()

val declaredVersionName = buildString {
    append("$versionMajor.$versionMinor.$versionPatch")
    if (versionPreRelease.isNotEmpty()) append("-$versionPreRelease")
}
val declaredVersionCode = versionMajor * 10_000_000 + versionMinor * 100_000 + versionPatch * 1_000

val configuredVersionCode = providers.gradleProperty("COINEPRO_VERSION_CODE")
    .orElse(declaredVersionCode.toString())
    .get()
    .toIntOrNull()
    ?: error("COINEPRO_VERSION_CODE must be an integer.")
require(configuredVersionCode > 0) { "COINEPRO_VERSION_CODE must be positive." }
require(configuredVersionCode <= 2_100_000_000) {
    "COINEPRO_VERSION_CODE exceeds the Android/Play upper bound of 2100000000."
}

val configuredVersionName = providers.gradleProperty("COINEPRO_VERSION_NAME")
    .orElse(declaredVersionName)
    .get()
require(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$").matches(configuredVersionName)) {
    "COINEPRO_VERSION_NAME must use semantic version form, for example 1.2.3 or 1.2.3-rc.1."
}

val debugTradeYarBaseUrl = providers.gradleProperty("COINEPRO_DEBUG_TRADEYAR_API_BASE_URL")
    .orElse("https://debug-tradeyar.example.invalid/")
    .get()
val debugApiBaseUrl = providers.gradleProperty("COINEPRO_DEBUG_API_BASE_URL")
    .orElse("https://debug.example.invalid/")
    .get()
val debugFirebaseProjectId = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_PROJECT_ID").orElse("").get()
val debugFirebaseApplicationId = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_APPLICATION_ID").orElse("").get()
val debugFirebaseApiKey = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_API_KEY").orElse("").get()
val debugFirebaseSenderId = providers.gradleProperty("COINEPRO_DEBUG_FIREBASE_SENDER_ID").orElse("").get()

val stagingTradeYarBaseUrl = providers.gradleProperty("COINEPRO_STAGING_TRADEYAR_API_BASE_URL")
    .orElse("https://staging-tradeyar.example.invalid/")
    .get()
val stagingApiBaseUrl = providers.gradleProperty("COINEPRO_STAGING_API_BASE_URL")
    .orElse("https://staging.example.invalid/")
    .get()
val stagingFirebaseProjectId = providers.gradleProperty("COINEPRO_STAGING_FIREBASE_PROJECT_ID").orElse("").get()
val stagingFirebaseApplicationId = providers.gradleProperty("COINEPRO_STAGING_FIREBASE_APPLICATION_ID").orElse("").get()
val stagingFirebaseApiKey = providers.gradleProperty("COINEPRO_STAGING_FIREBASE_API_KEY").orElse("").get()
val stagingFirebaseSenderId = providers.gradleProperty("COINEPRO_STAGING_FIREBASE_SENDER_ID").orElse("").get()

// The two production hosts, as their own teams published them. Both are public addresses rather
// than anything secret, and naming them here is what makes a release build reach a real server —
// the `.invalid` placeholders below are deliberate dead ends for environments nobody has stood up.
//
// The `/api` on CoinePro-FX is part of its base address, not a path the app adds: its routes are
// documented as `user/…` and are served under that prefix. TradeYar carries its own prefix inside
// each route instead (`api/mobile/v1/…`), so its base is the bare host.
val productionTradeYarBaseUrl = providers.gradleProperty("COINEPRO_PRODUCTION_TRADEYAR_API_BASE_URL")
    .orElse("https://tradeyar.trade-future.ir/")
    .get()
val productionApiBaseUrl = providers.gradleProperty("COINEPRO_PRODUCTION_API_BASE_URL")
    .orElse("https://coineprofx.com/api/")
    .get()
val productionFirebaseProjectId = providers.gradleProperty("COINEPRO_PRODUCTION_FIREBASE_PROJECT_ID").orElse("").get()
val productionFirebaseApplicationId = providers.gradleProperty("COINEPRO_PRODUCTION_FIREBASE_APPLICATION_ID").orElse("").get()
val productionFirebaseApiKey = providers.gradleProperty("COINEPRO_PRODUCTION_FIREBASE_API_KEY").orElse("").get()
val productionFirebaseSenderId = providers.gradleProperty("COINEPRO_PRODUCTION_FIREBASE_SENDER_ID").orElse("").get()

// Where the full web terminal lives — the React app whose engine `core:chart` was ported from.
//
// Empty by default and empty everywhere until somebody sets it, which hides the terminal entry
// rather than pointing it at a guess. Two things have to be true of whatever address goes here and
// neither can be checked from this repository: it must serve the terminal, and its API must be the
// same CoinePro-FX deployment the app signs into — the terminal authenticates by reading an
// academy token out of browser storage, and a token minted by one deployment means nothing to
// another's `JWT_SECRET_KEY`.
val debugTerminalUrl = providers.gradleProperty("COINEPRO_DEBUG_TERMINAL_URL").orElse("").get()
val stagingTerminalUrl = providers.gradleProperty("COINEPRO_STAGING_TERMINAL_URL").orElse("").get()
val productionTerminalUrl = providers.gradleProperty("COINEPRO_PRODUCTION_TERMINAL_URL").orElse("").get()

require(stagingApiBaseUrl != productionApiBaseUrl) {
    "Staging and production API base URLs must be different."
}
require(stagingTradeYarBaseUrl != productionTradeYarBaseUrl) {
    "Staging and production TradeYar API base URLs must be different."
}
// The two platforms are separate systems with separate user tables. Pointing both at one host
// would silently send a TradeYar token to CoinePro-FX.
listOf(
    "debug" to (debugApiBaseUrl to debugTradeYarBaseUrl),
    "staging" to (stagingApiBaseUrl to stagingTradeYarBaseUrl),
    "production" to (productionApiBaseUrl to productionTradeYarBaseUrl),
).forEach { (name, urls) ->
    require(urls.first != urls.second) {
        "CoinePro-FX and TradeYar base URLs must differ for the $name environment."
    }
    // Retrofit resolves a relative path against the base as a URL, not by joining strings: without
    // the trailing slash the last segment is replaced rather than appended, so a base of
    // `https://host/api` turns `user/auth/methods` into `https://host/user/auth/methods` and every
    // request 404s in a way that reads like the server is down.
    listOf(urls.first, urls.second).forEach { url ->
        require(url.endsWith("/")) {
            "The $name base URL must end with a slash, otherwise Retrofit drops its last path segment: $url"
        }
    }
}

/**
 * Signing credentials, from a Gradle property or from `local.properties`.
 *
 * CI passes them with `-P`; a developer machine keeps them in `local.properties`, which Android
 * Studio creates, `.gitignore` already refuses, and `scan-secrets.sh` checks for. Gradle does not
 * read that file on its own, which is why this fallback exists — without it a correctly configured
 * machine silently produces an *unsigned* release APK and the build still says SUCCESSFUL.
 *
 * A Gradle property wins where both are set, so CI is never overridden by a stray local file.
 */
val localProperties: Properties? = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

fun signingProperty(name: String): String? =
    (providers.gradleProperty(name).orNull ?: localProperties?.getProperty(name))
        ?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingProperty("COINEPRO_RELEASE_STORE_FILE")
val releaseStorePassword = signingProperty("COINEPRO_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingProperty("COINEPRO_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingProperty("COINEPRO_RELEASE_KEY_PASSWORD")
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
            buildConfigField("String", "TRADEYAR_API_BASE_URL", escapedBuildConfig(debugTradeYarBaseUrl))
            buildConfigField("String", "FIREBASE_PROJECT_ID", escapedBuildConfig(debugFirebaseProjectId))
            buildConfigField("String", "FIREBASE_APPLICATION_ID", escapedBuildConfig(debugFirebaseApplicationId))
            buildConfigField("String", "FIREBASE_API_KEY", escapedBuildConfig(debugFirebaseApiKey))
            buildConfigField("String", "FIREBASE_SENDER_ID", escapedBuildConfig(debugFirebaseSenderId))
            buildConfigField("String", "TERMINAL_URL", escapedBuildConfig(debugTerminalUrl))
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
            buildConfigField("String", "TRADEYAR_API_BASE_URL", escapedBuildConfig(productionTradeYarBaseUrl))
            buildConfigField("String", "FIREBASE_PROJECT_ID", escapedBuildConfig(productionFirebaseProjectId))
            buildConfigField("String", "FIREBASE_APPLICATION_ID", escapedBuildConfig(productionFirebaseApplicationId))
            buildConfigField("String", "FIREBASE_API_KEY", escapedBuildConfig(productionFirebaseApiKey))
            buildConfigField("String", "FIREBASE_SENDER_ID", escapedBuildConfig(productionFirebaseSenderId))
            buildConfigField("String", "TERMINAL_URL", escapedBuildConfig(productionTerminalUrl))
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
            buildConfigField("String", "TRADEYAR_API_BASE_URL", escapedBuildConfig(stagingTradeYarBaseUrl))
            buildConfigField("String", "FIREBASE_PROJECT_ID", escapedBuildConfig(stagingFirebaseProjectId))
            buildConfigField("String", "FIREBASE_APPLICATION_ID", escapedBuildConfig(stagingFirebaseApplicationId))
            buildConfigField("String", "FIREBASE_API_KEY", escapedBuildConfig(stagingFirebaseApiKey))
            buildConfigField("String", "FIREBASE_SENDER_ID", escapedBuildConfig(stagingFirebaseSenderId))
            buildConfigField("String", "TERMINAL_URL", escapedBuildConfig(stagingTerminalUrl))
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            buildConfigField("String", "BUILD_ENVIRONMENT", escapedBuildConfig("benchmark"))
            buildConfigField("String", "API_BASE_URL", escapedBuildConfig("https://benchmark.example.invalid/"))
            buildConfigField("String", "TRADEYAR_API_BASE_URL", escapedBuildConfig("https://benchmark-tradeyar.example.invalid/"))
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
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)
    implementation(project(":core:security"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:chart"))
    implementation(project(":core:help"))
    implementation(project(":core:marketdata"))
    implementation(project(":core:signals"))
    implementation(project(":core:notifications"))
    implementation(project(":core:execution"))
    implementation(project(":core:copytrade"))
    implementation(project(":core:academy"))
    implementation(project(":core:portfolio"))
    implementation(project(":core:aisignal"))
    implementation(project(":core:aivision"))
    implementation(project(":core:account"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:aiassistant"))
    implementation(project(":core:marketintel"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:admin"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:chart"))
    implementation(project(":feature:academy"))
    implementation(project(":feature:portfolio"))
    implementation(project(":feature:terminal"))
    implementation(project(":feature:signals"))
    implementation(project(":feature:signal-detail"))
    implementation(project(":feature:connections"))
    implementation(project(":feature:execution"))
    implementation(project(":feature:kyc"))
    implementation(project(":feature:account"))
    implementation(project(":feature:copytrade"))
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
