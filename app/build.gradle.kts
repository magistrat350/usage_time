plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.checkerschreck"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.checkerschreck"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        // Fester Schlüssel im Repo, damit jede neue APK als Update über die alte
        // installiert werden kann. Über Umgebungsvariablen austauschbar.
        create("shared") {
            storeFile = file(System.getenv("CS_KEYSTORE") ?: "signing.keystore")
            storePassword = System.getenv("CS_KEYSTORE_PASSWORD") ?: "checkerschreck"
            keyAlias = System.getenv("CS_KEY_ALIAS") ?: "checkerschreck"
            keyPassword = System.getenv("CS_KEY_PASSWORD") ?: "checkerschreck"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
