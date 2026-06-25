plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.codexue.pixelread"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.codexue.pixelread"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.pdf.viewer.fragment)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

@org.gradle.work.DisableCachingByDefault(because = "Copies the already-built debug APK to its release asset name.")
abstract class CopyVersionedApkTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val inputApk: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputApk: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun copyApk() {
        inputApk.get().asFile.copyTo(outputApk.get().asFile, overwrite = true)
    }
}

val versionedDebugApkName = "PixelRead-${android.defaultConfig.versionName}-debug.apk"
val debugApkOutputDir = layout.buildDirectory.dir("outputs/apk/debug")
val defaultDebugApk = debugApkOutputDir.map { it.file("app-debug.apk") }
val versionedDebugApk = debugApkOutputDir.map { it.file(versionedDebugApkName) }

val copyVersionedDebugApk by tasks.registering(CopyVersionedApkTask::class) {
    dependsOn("assembleDebug")
    inputApk.set(defaultDebugApk)
    outputApk.set(versionedDebugApk)
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyVersionedDebugApk)
}
