plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.papertrade"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:database"))
    // The record's arithmetic is `PortfolioMath`'s, not a second copy of it — see `PaperRecord`.
    // `api` rather than `implementation` because a screen showing these statistics has to be able
    // to name `PortfolioStats`, and a feature module should not have to depend on the portfolio to
    // read a paper account.
    api(project(":core:portfolio"))
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
