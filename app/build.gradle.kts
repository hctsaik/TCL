plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "tw.sleeptimer"; compileSdk = 34
  defaultConfig { applicationId = "tw.sleeptimer"; minSdk = 28; targetSdk = 34; versionCode = 4; versionName = "1.3" }
  // Fixed project keystore so every rebuild can be installed over the previous one (adb install -r).
  // Debug-only key, passwords are intentionally public.
  signingConfigs {
    create("project") {
      storeFile = file("sleeptimer-debug.keystore"); storePassword = "sleeptimer"
      keyAlias = "sleeptimer"; keyPassword = "sleeptimer"
    }
  }
  buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("project") } }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  lint { abortOnError = false }
}
