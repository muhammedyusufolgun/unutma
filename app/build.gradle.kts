plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.olgun.unutma"; compileSdk = 35
 defaultConfig { applicationId = "com.olgun.unutma"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
 implementation("androidx.work:work-runtime-ktx:2.9.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
