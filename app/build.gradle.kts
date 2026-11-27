import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

// ./gradlew ktlintFormat fixes the style; ktlintCheck (CI) fails on what it can't fix. rules in .editorconfig
ktlint {
    version.set("1.3.1")
    android.set(true)
}

// release signing: keystore.properties (gitignored) next to settings.gradle.kts
val ks = Properties().apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }

android {
    namespace = "pizza.kulhad.medslave"
    compileSdk = 35
    defaultConfig {
        applicationId = "pizza.kulhad.medslave"
        minSdk = 26
        targetSdk = 35
        versionCode = 106
        versionName = "2.05"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    signingConfigs {
        if (ks.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(ks.getProperty("storeFile"))
                storePassword = ks.getProperty("storePassword")
                keyAlias = ks.getProperty("keyAlias")
                keyPassword = ks.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ks.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = false
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.github.luben:zstd-jni:1.5.6-8@aar")
    // JVM unit tests (app/src/test, ./gradlew testReleaseUnitTest): Android's own org.json is only a stub there
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
