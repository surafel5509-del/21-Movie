import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * The TorrentClaw API key is never committed. Provide it in one of these places
 * (checked in order):
 *   1. local.properties        ->  torrentclaw.apiKey=tc_live_xxxxxxxx
 *   2. Gradle property         ->  ./gradlew assembleDebug -Ptorrentclaw.apiKey=tc_live_xxxxxxxx
 *   3. Environment variable    ->  TORRENTCLAW_API_KEY=tc_live_xxxxxxxx
 *
 * The key is optional: without it TorrentClaw serves the anonymous tier
 * (30 req/min). With a free key the app gets 120 req/min, matching the
 * search-debounce budget used by SearchViewModel.
 */
val torrentClawApiKey: String = run {
    val local = rootProject.file("local.properties")
    val fromLocal = if (local.exists()) {
        Properties().apply { local.inputStream().use { load(it) } }.getProperty("torrentclaw.apiKey")
    } else {
        null
    }
    fromLocal
        ?: (project.findProperty("torrentclaw.apiKey") as String?)
        ?: System.getenv("TORRENTCLAW_API_KEY")
        ?: ""
}

/**
 * Release signing is optional. Provide a keystore through environment variables
 * (CI) and the release build is signed properly; otherwise it falls back to the
 * debug key so `assembleRelease` still yields an installable APK for testers.
 */
val releaseKeystorePath: String? = System.getenv("KEYSTORE_PATH")
val hasReleaseKeystore = !releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).exists()

android {
    namespace = "com.torrentclaw.movies"
    compileSdk = 35

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.torrentclaw.movies"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "TORRENTCLAW_API_KEY", "\"$torrentClawApiKey\"")
        buildConfigField("String", "TORRENTCLAW_BASE_URL", "\"https://torrentclaw.com/\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            buildConfigField("boolean", "HTTP_LOGGING_ENABLED", "true")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("boolean", "HTTP_LOGGING_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            // The screens annotate their experimental opt-ins locally, so no
            // project-wide -opt-in is needed.
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }
}

ksp {
    // Export Room schemas so migrations can be diffed and tested.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM aligned)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation + DI
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Images
    implementation(libs.coil.compose)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
