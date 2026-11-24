plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt") // Add this for Room
}

val auraAdb = androidComponents.sdkComponents.adb
val auraDeviceSerial = providers.gradleProperty("android.injected.device.serial")
    .orElse(providers.environmentVariable("ANDROID_SERIAL"))

// A shared IDE Run configuration uses this task, independently of a cached build variant.
tasks.register<Exec>("runPerformance") {
    group = "install"
    description = "Install the optimized app and its profile, then open AuraPlay."
    dependsOn("installPerformance")
    doFirst {
        val device = auraDeviceSerial.orNull?.let { listOf("-s", it) }.orEmpty()
        commandLine(listOf(auraAdb.get().asFile.absolutePath) + device +
            listOf("shell", "am", "start", "-n", "com.example.auraplay/com.example.auraplay.MainActivity"))
    }
}

android {
    namespace = "com.example.auraplay"
    compileSdk = 36 // Updated SDK version

    defaultConfig {
        applicationId = "com.example.auraplay"
        minSdk = 26
        targetSdk = 36 // Updated SDK version
        versionCode = 7
        versionName = "1.1.5"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Development runs must never replace the optimized, everyday app.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            isDefault = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

        }
        // Installable local build for measuring first-launch scrolling with release optimizations.
        create("performance") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDefault = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Add this line to resolve the theme errors
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.31.5-beta")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.4.0")

    // Room for Database (Playlists)
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Media3 for Background Playback and
    // Notifications
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // --- ADD THIS LINE FOR PREFERENCES DATASTORE ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Palette for dynamic album art color extraction
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
