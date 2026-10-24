plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "it.jarviswake"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "it.jarviswake"
        minSdk = 31
        targetSdk = 36
        versionCode = 73
        versionName = "0.5.51"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    val ciKeystore = System.getenv("JARVIS_KEYSTORE_PATH")
    signingConfigs {
        create("persistent") {
            storeFile = file(ciKeystore ?: "signing-keystore-not-configured.jks")
            storePassword = System.getenv("JARVIS_SIGNING_PASSWORD") ?: ""
            keyAlias = "jarvis"
            keyPassword = System.getenv("JARVIS_SIGNING_PASSWORD") ?: ""
        }
    }
    buildTypes {
        getByName("debug") {
            if (providers.gradleProperty("jarvisUnsigned").orNull == "true") {
                signingConfig = null
            } else if (!ciKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("persistent")
            }
        }
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
