plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.orbit.clock.phone"
    compileSdk = 35
    defaultConfig { applicationId = "dev.orbit.clock"; minSdk = 30; targetSdk = 35; versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()?.plus(1) ?: 1; versionName = "0.1." + (providers.environmentVariable("GITHUB_RUN_NUMBER").orNull ?: "local") }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":core")) }
