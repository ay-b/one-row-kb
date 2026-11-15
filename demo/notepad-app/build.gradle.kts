plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "demo.notepad"
    compileSdk = 34
    defaultConfig { applicationId = "demo.notepad"; minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
