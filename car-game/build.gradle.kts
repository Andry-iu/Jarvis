plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "it.jarviswake.cube"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "it.jarviswake.cube"
        minSdk = 31
        targetSdk = 36
        versionCode = 145
        versionName = "0.5.123"
    }
    buildTypes { getByName("debug") { signingConfig = null } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
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
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
