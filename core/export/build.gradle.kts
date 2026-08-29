// The one export writer, and nothing else.
//
// No dependencies on purpose — not on `core:common`, not on Compose, not on a spreadsheet library.
// This module exists because three screens were each writing their own CSV and two of them had
// copied the same three rules out of the third; a module that depended on anything of the app's own
// would be a module one of those three could not reach, which is exactly how the duplication
// started. It knows about strings, numbers and bytes, and it must stay that way.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.export"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
