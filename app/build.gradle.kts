plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "it.jarviswake"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "it.jarviswake"
        minSdk = 31
        targetSdk = 36
        versionCode = 48
        versionName = "0.5.26"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    testImplementation("junit:junit:4.13.2")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
