plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// Semantic versioning (MAJOR.MINOR.PATCH). Bump this ONE line for every build you hand out:
//   PATCH = bug fix, MINOR = new feature (backwards compatible), MAJOR = breaking change / 1.0 = first public release.
// versionCode is derived from it (M*10000 + m*100 + p) so Android always sees newer builds as upgrades.
val appVersion = "0.11.0"
val (vMajor, vMinor, vPatch) = appVersion.split(".").map { it.toInt() }

base { archivesName.set("onerow-$appVersion") }

android {
    namespace = "com.example.onerow"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.onerow"
        minSdk = 24
        targetSdk = 34
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = appVersion
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
