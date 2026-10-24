plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "tw.sleeptimer"; compileSdk = 34
  defaultConfig { applicationId = "tw.sleeptimer"; minSdk = 28; targetSdk = 34; versionCode = 1; versionName = "1.0" }
  buildTypes { getByName("debug") { } }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}
