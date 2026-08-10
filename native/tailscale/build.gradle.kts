plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Responsibility (Phase 1+): contain the Go/Tailscale integration and the
// Android bridge. Keep this boundary very small. This module hosts the
// gomobile build step and the generated AAR from native/tailscale/go.
//
// Note: the module path is `native/tailscale`; the namespace cannot use
// `native` because it is a Java keyword.
android {
    namespace = "com.homelab.poc.tailscale"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Builds the embedded Tailscale AAR from Go via gomobile. Run it standalone
// with: ./native/tailscale/go/build-android.sh
// To build with the isolated patched Go toolchain (netlinkrib experiment):
//   ./gradlew -PgoToolchainRoot=/workspace/go-patched :app:assembleDebug
val goAar = layout.buildDirectory.file("tsembed.aar")

val goBind by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the Tailscale embedded AAR from Go via gomobile."
    workingDir = file("go")
    commandLine("bash", "build-android.sh")
    inputs.dir(layout.projectDirectory.dir("go"))
    outputs.file(goAar)
    providers.gradleProperty("goToolchainRoot").orNull?.let { root ->
        environment("PATCHED_GOROOT", root)
    }
}

tasks.named("preBuild") {
    dependsOn(goBind)
}

dependencies {
    implementation(project(":core:connectivity"))
    implementation(files(goAar))
}
