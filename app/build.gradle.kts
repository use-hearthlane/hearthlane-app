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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
