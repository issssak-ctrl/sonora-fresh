plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.sonora.fresh"; compileSdk = 35
    defaultConfig { applicationId = "com.sonora.fresh"; minSdk = 26; targetSdk = 35; versionCode = 9; versionName = "1.8-metallic-layout" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
}
