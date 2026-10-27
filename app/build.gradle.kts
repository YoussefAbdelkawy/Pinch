import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from keystore.properties (git-ignored) or CI env vars.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "dev.pinch.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.pinch.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.2.0"
    }

    signingConfigs {
        create("release") {
            val store = signingValue("storeFile", "CLAWD_KEYSTORE")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingValue("storePassword", "CLAWD_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "CLAWD_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "CLAWD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingValue("storeFile", "CLAWD_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
