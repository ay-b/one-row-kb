plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "blank.host"
    compileSdk = 34
    defaultConfig { applicationId = "blank.host"; minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
