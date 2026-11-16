import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// Semantic versioning (MAJOR.MINOR.PATCH). Bump this ONE line for every build you hand out:
//   PATCH = bug fix, MINOR = new feature (backwards compatible), MAJOR = breaking change / 1.0 = first public release.
// versionCode is derived from it (M*10000 + m*100 + p) so Android always sees newer builds as upgrades.
val appVersion = "0.24.0"
val (vMajor, vMinor, vPatch) = appVersion.split(".").map { it.toInt() }

base { archivesName.set("onerow-$appVersion") }

android {
    namespace = "com.example.onerow"
    compileSdk = 36
    defaultConfig {
        applicationId = "one.yb.onerow"
        minSdk = 24
        targetSdk = 36
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = appVersion
    }
    // Upload key for Play bundles: ~/.android/onerow-upload.properties (kept out of the repo). Without it the release build is unsigned.
    val uploadProps = Properties().apply {
        val f = File(System.getProperty("user.home"), ".android/onerow-upload.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (uploadProps.containsKey("storeFile")) create("upload") {
            storeFile = File(uploadProps.getProperty("storeFile"))
            storePassword = uploadProps.getProperty("storePassword")
            keyAlias = uploadProps.getProperty("keyAlias")
            keyPassword = uploadProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
        }
        // R8 on the debug build too (it is the one we hand out, debug-signed): shrinks code and resources.
        debug {
            val r8 = (project.findProperty("r8") ?: "true") != "false"   // -Pr8=false for A/B timing
            isMinifyEnabled = r8
            isShrinkResources = r8
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.mlkit:genai-proofreading:1.0.0-beta1")
    implementation("com.android.billingclient:billing:8.0.0")
    testImplementation("junit:junit:4.13.2")
}
