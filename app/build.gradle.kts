plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.audiodl.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.audiodl.app"
        minSdk = 29
        targetSdk = 35
        // Cada compilación de GitHub Actions lleva su número (build-N) como versión,
        // así la app puede saber si hay una más nueva en Releases.
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "2.1 (build $buildNumber)"
        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // Firma fija: todas las compilaciones usan la misma clave, así Android acepta
    // instalar una versión nueva encima de la anterior sin desinstalar.
    signingConfigs {
        create("fixed") {
            storeFile = file("audiodl.jks")
            storePassword = "audiodl123"
            keyAlias = "audiodl"
            keyPassword = "audiodl123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    val ytdlVersion = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlVersion")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlVersion")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
