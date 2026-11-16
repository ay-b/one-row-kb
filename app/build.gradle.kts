plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// Semantic versioning (MAJOR.MINOR.PATCH). Bump this ONE line for every build you hand out:
//   PATCH = bug fix, MINOR = new feature (backwards compatible), MAJOR = breaking change / 1.0 = first public release.
// versionCode is derived from it (M*10000 + m*100 + p) so Android always sees newer builds as upgrades.
val appVersion = "0.17.1"
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
    buildTypes {
        // R8 on the debug build too (it is the one we hand out, debug-signed): shrinks code and resources.
        debug {
            val r8 = (project.findProperty("r8") ?: "true") != "false"   // -Pr8=false for A/B timing
            isMinifyEnabled = r8
            isShrinkResources = r8
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.mlkit:genai-proofreading:1.0.0-beta1")
    testImplementation("junit:junit:4.13.2")
}
