plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "it.jarviswake"
    sourceSets.getByName("main").java.srcDir(rootProject.file("watch-common"))
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "it.jarviswake"
        minSdk = 31
        targetSdk = 36
        versionCode = 145
        versionName = "0.5.123"
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            val robolectricHome = gradle.gradleUserHomeDir.resolve("caches/robolectric-home")
            it.systemProperty("user.home", robolectricHome.absolutePath)
            it.doFirst { robolectricHome.mkdirs() }
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("maven.repo.local", gradle.gradleUserHomeDir.resolve("caches/robolectric-maven").absolutePath)
            it.systemProperty("robolectric.enabledSdks", providers.gradleProperty("jarvisTestSdks").orNull ?: "34")
            providers.gradleProperty("jarvisTestJava").orNull?.let { runtime -> it.executable = runtime }
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
        }
    }
}
val whatsappNative by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("bash", rootProject.file("scripts/build-whatsapp-native.sh").absolutePath)
    inputs.dir(rootProject.file("whatsapp-native"))
    inputs.file(rootProject.file("scripts/build-whatsapp-native.sh"))
    outputs.file(layout.buildDirectory.file("whatsapp-native/wabridge.aar"))
}
val cloudBuildDiagnostics by tasks.registering(Exec::class) {
    onlyIf { rootProject.file("cloud-runner/diagnostic-request.json").exists() }
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("cloud-runner/collect_diagnostic.py").absolutePath)
}
tasks.named("preBuild").configure { dependsOn(cloudBuildDiagnostics) }
tasks.named("preBuild").configure { dependsOn(whatsappNative) }

dependencies {
    implementation("io.grpc:grpc-okhttp:1.76.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation(files("build/whatsapp-native/wabridge.aar").builtBy(whatsappNative))
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.car.app:app:1.7.0")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// One release group verifies and assembles both phone apps in the Jarvis cloud.
tasks.matching { it.name == "testDebugUnitTest" }.configureEach { dependsOn(":car-game:testDebugUnitTest") }
tasks.matching { it.name == "lintDebug" }.configureEach { dependsOn(":car-game:lintDebug") }
tasks.matching { it.name == "assembleDebug" }.configureEach { dependsOn(":car-game:assembleDebug") }
