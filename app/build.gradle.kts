plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Frigate destination for the transparent connection strategy. The same URL is
// used for the home-LAN probe and for the Tailscale probe; on the Tailscale
// path the hostname resolves through the tailnet DNS (MagicDNS / homelab DNS
// configured in the Tailscale admin console). Overridable at build time:
//   ./gradlew -Pfrigate.baseUrl=http://site.omni.corp :app:assembleDebug
val frigateBaseUrl = (project.findProperty("frigate.baseUrl") as String?) ?: "http://site.omni.corp"

android {
    // Namespace is intentionally kept as the original com.homelab.poc package
    // tree. Only applicationId (the Android OS / Play Store identity) changes
    // for V1; refactoring every Kotlin source package and the gomobile Java
    // package would be a large, risky change with no functional benefit.
    namespace = "com.homelab.poc"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.homelab.hearthlane"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "1.2.0"

        buildConfigField("String", "FRIGATE_BASE_URL", "\"$frigateBaseUrl\"")
    }

    signingConfigs {
        create("release") {
            // Signing credentials are provided externally; they are never
            // committed. Supports Gradle project properties (-P) and environment
            // variables so CI and local release builds can use the mechanism
            // that fits the environment. For Play Store, this key is the upload
            // key; Google re-signs the AAB with the app signing key.
            val storeFilePath = providers.gradleProperty("RELEASE_STORE_FILE")
                .orElse(providers.environmentVariable("RELEASE_STORE_FILE"))
                .orNull
            val storePwd = providers.gradleProperty("RELEASE_STORE_PASSWORD")
                .orElse(providers.environmentVariable("RELEASE_STORE_PASSWORD"))
                .orNull
            val alias = providers.gradleProperty("RELEASE_KEY_ALIAS")
                .orElse(providers.environmentVariable("RELEASE_KEY_ALIAS"))
                .orNull
            val keyPwd = providers.gradleProperty("RELEASE_KEY_PASSWORD")
                .orElse(providers.environmentVariable("RELEASE_KEY_PASSWORD"))
                .orNull
            storeFile = storeFilePath?.let { file(it) }
            storePassword = storePwd
            keyAlias = alias
            keyPassword = keyPwd
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            // R8/ProGuard is intentionally disabled for V1. The gomobile native
            // bridge and the Tailscale Go code use reflection/JNI patterns that
            // have not been validated against obfuscation; a broken release
            // build is a worse outcome than a larger APK/AAB. Revisit only after
            // testing a full ProGuard/R8 configuration on a physical device.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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

// Release tasks validate signing credentials early so the failure is explicit
// and close to the command that triggered it. Debug builds are unaffected when
// the release properties are absent.
tasks.configureEach {
    val releaseTaskNames = setOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
        "signReleaseBundle",
        "signReleaseUniversalApk",
    )
    if (name in releaseTaskNames || name.startsWith("signRelease")) {
        doFirst {
            val cfg = android.signingConfigs.getByName("release")
            require(cfg.storeFile != null && cfg.storeFile!!.exists()) {
                "RELEASE_STORE_FILE must point to an existing keystore file for release builds. " +
                    "See docs/RELEASE.md for the signing setup."
            }
            require(!cfg.storePassword.isNullOrBlank()) {
                "RELEASE_STORE_PASSWORD must be set for release builds. " +
                    "See docs/RELEASE.md for the signing setup."
            }
            require(!cfg.keyAlias.isNullOrBlank()) {
                "RELEASE_KEY_ALIAS must be set for release builds. " +
                    "See docs/RELEASE.md for the signing setup."
            }
            require(!cfg.keyPassword.isNullOrBlank()) {
                "RELEASE_KEY_PASSWORD must be set for release builds. " +
                    "See docs/RELEASE.md for the signing setup."
            }
        }
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
        (this as org.gradle.api.tasks.testing.Test).filter { excludeTestsMatching("com.homelab.poc.ui.LiveViewTest") }
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
    // testReleaseUnitTest exclusion above).
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
