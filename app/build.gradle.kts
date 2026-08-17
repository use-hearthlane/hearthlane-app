plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Frigate destination for the Phase 2 transparent connection strategy. The same
// URL is used for the home-LAN probe and for the Tailscale probe; on the
// Tailscale path the hostname resolves through the tailnet DNS (MagicDNS /
// homelab DNS configured in the Tailscale admin console). Overridable at build
// time:
//   ./gradlew -Pfrigate.baseUrl=http://site.omni.corp :app:assembleDebug
val frigateBaseUrl = (project.findProperty("frigate.baseUrl") as String?) ?: "http://site.omni.corp"

android {
    namespace = "com.homelab.poc"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.homelab.poc"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "FRIGATE_BASE_URL", "\"$frigateBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Allow android.util.Log calls to be no-ops in JVM unit tests.
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

// LiveViewTest drives Compose through ActivityScenario, which resolves
// androidx.activity.ComponentActivity against the variant's app manifest. The
// compose test manifest (debugImplementation of ui-test-manifest) injects that
// activity into the debug manifest, but the release manifest never contains
// it, so the Robolectric launch cannot resolve it on release. The Compose
// contract is fully covered by the debug unit tests; release runs the
// non-UI suites.
tasks.configureEach {
    if (name == "testReleaseUnitTest") {
        (this as Test).filter { excludeTestsMatching("com.homelab.poc.ui.LiveViewTest") }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:connectivity"))
    implementation(project(":core:frigate"))
    implementation(project(":core:playback"))
    implementation(project(":native:tailscale"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // debugImplementation (not testImplementation) so the merged DEBUG app
    // manifest declares androidx.activity.ComponentActivity for Robolectric's
    // ActivityScenario; the release manifest never carries it (see the
    // testReleaseUnitTest exclusion below).
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
