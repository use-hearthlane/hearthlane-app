plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Responsibility (Phase 3+): own the Android playback implementation. Keeps
// transport/player experimentation (HLS in this spike; WebRTC/MSE later)
// behind this boundary so alternatives can be swapped without rewriting the
// UI. No Frigate-specific code here.
android {
    namespace = "org.hearthlane.core.playback"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Allow android.util.Log to be a no-op in JVM unit tests.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:connectivity"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.media3.datasource)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
    // The LOCAL end-to-end test drives the real HttpUrlConnectionStreamGetter
    // through StreamingHttpDataSource (core/playback does not depend on
    // core/frigate for its production code).
    testImplementation(project(":core:frigate"))
    // Robolectric provides a working android.net.Uri for the DataSource
    // contract tests (getUri/finalUrl, DataSpec building, MediaItem.fromUri).
    testImplementation(libs.robolectric)
}
