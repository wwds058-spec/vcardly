import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing is read from an optional, git-ignored keystore.properties.
// If it is absent the release variant is simply unsigned: nothing is faked.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Ad IDs: Google's official TEST IDs unless secrets.properties (git-ignored) provides real ones.
// Debug builds ALWAYS use the test IDs (clicking your own live ads violates AdMob policy); release builds use real IDs only
// when both are present, otherwise ads stay OFF in release (test ads must never ship).
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val releaseAppId = secrets.getProperty("admob.appId") ?: testAppId
val releaseBannerId = secrets.getProperty("admob.bannerUnitId") ?: testBannerId
val adsConfiguredForRelease = secrets.containsKey("admob.appId") && secrets.containsKey("admob.bannerUnitId")
// VCardly Pro is only sold once its products exist in Play Console. Until secrets.properties says pro.forSale=true, every
// feature is free: no scan limit, no locked exports, no upgrade prompts (a paywall nobody can pay would be broken).
val proForSale = secrets.getProperty("pro.forSale")?.trim() == "true"

// Optional single-ABI build for sideloading, e.g. ./gradlew :app:assembleDebug -Pabi=arm64-v8a
// (nearly all phones are arm64; x86 emulators are not). Also stores native libs compressed, which makes the APK much smaller.
// Without the property every ABI is packaged, which the x86_64 emulator tests in CI need.
val abiFilter: String? = providers.gradleProperty("abi").orNull

android {
    namespace = "com.yasin.vcardly"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yasin.vcardly"
        minSdk = 26
        // Google Play requires API 36 (Android 16) for new apps and updates since 31 August 2026.
        targetSdk = 36
        // Every upload to Play needs a higher versionCode. CI passes its run number (VERSION_CODE); local builds use 1.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
        buildConfigField("boolean", "PRO_FOR_SALE", "$proForSale")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
        if (abiFilter != null) ndk { abiFilters += abiFilter.split(",").map { it.trim() } }
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$testBannerId\"")
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("boolean", "ADS_USE_TEST_IDS", "true")
        }
        release {
            manifestPlaceholders["admobAppId"] = releaseAppId
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$releaseBannerId\"")
            buildConfigField("boolean", "ADS_ENABLED", "$adsConfiguredForRelease")
            buildConfigField("boolean", "ADS_USE_TEST_IDS", "${!adsConfiguredForRelease}")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        if (abiFilter != null) jniLibs.useLegacyPackaging = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Exported Room schemas are used by MigrationTestHelper in androidTest.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    // A release without AdMob IDs never shows ads: drop the ads library's advertising-ID permission and start-up provider.
    if (!adsConfiguredForRelease) sourceSets["release"].manifest.srcFile("src/releaseNoAds/AndroidManifest.xml")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
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
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // ListenableFuture.await() for CameraX (also puts Guava's ListenableFuture on the compile classpath).
    implementation(libs.androidx.concurrent.futures.ktx)
    // AndroidX ExifInterface carries security fixes the framework class lacks on older Android versions.
    implementation(libs.androidx.exifinterface)
    // Other Google libraries pull in an EMPTY placeholder for ListenableFuture; real Guava makes CameraX's types resolvable.
    implementation(libs.guava)
    // Bundled Latin-script model: works offline, no Play Services download at runtime.
    implementation(libs.mlkit.text.recognition)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    // QR generation: pure Java, offline.
    implementation(libs.zxing.core)
    // App lock: BiometricPrompt needs a FragmentActivity.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.zxing.core)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Google's Accessibility Test Framework: the checks behind Android's Accessibility Scanner (test only).
    androidTestImplementation(libs.accessibility.test.framework)
    // Screenshots written through TestStorage are pulled by AGP into build/outputs/connected_android_test_additional_output.
    androidTestImplementation(libs.androidx.test.services.storage)
    androidTestUtil(libs.androidx.test.services)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
