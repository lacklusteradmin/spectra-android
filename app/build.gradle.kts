plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The core package spectra's `make android` builds into its
// target/SpectraCoreAndroid/, copied to this repository's root: core as a
// shared library per ABI and the Kotlin bindings generated from it. Gradle
// compiles no Rust.
val spectraCore: Directory = rootProject.layout.projectDirectory.dir("SpectraCoreAndroid")

android {
    namespace = "dev.spectra"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.spectra"
        // The package's libraries are linked for this API level: spectra's
        // `make android` takes it as ANDROID_MIN_SDK.
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            kotlin.directories += spectraCore.dir("kotlin").asFile.path
            jniLibs.directories += spectraCore.dir("jniLibs").asFile.path
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// A build without the package, or a release build with a dev one, stops here
// with what to do rather than failing later on a missing class or library.
val coreBuildInputs = spectraCore.file("build-inputs").asFile
listOf("debug", "release").forEach { buildType ->
    val name = "check${buildType.replaceFirstChar(Char::uppercase)}SpectraCore"
    val check = tasks.register(name) {
        val inputs = coreBuildInputs
        val release = buildType == "release"
        doLast {
            if (!inputs.exists()) {
                throw GradleException(
                    "SpectraCoreAndroid/ is missing: run `make android` in a spectra checkout " +
                        "and copy its target/SpectraCoreAndroid/ to this repository's root",
                )
            }
            if (release && !inputs.readText().contains("profile=release")) {
                throw GradleException(
                    "a release build needs the release core package: run `make android PROFILE=release` " +
                        "in spectra and copy it here",
                )
            }
        }
    }
    tasks.matching { it.name == "pre${buildType.replaceFirstChar(Char::uppercase)}Build" }
        .configureEach { dependsOn(check) }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    // The bindings call core through JNA; the AAR carries its native part.
    implementation("${libs.jna.get()}@aar")
    implementation(libs.zxing.core)
    implementation(libs.androidsvg)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
