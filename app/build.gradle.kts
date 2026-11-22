plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "hu.jarvis.localhud"
  compileSdk = 35
  defaultConfig {
    applicationId = "hu.jarvis.reactor.online"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.1-online-handoff"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("org.nanohttpd:nanohttpd:2.3.1") }
