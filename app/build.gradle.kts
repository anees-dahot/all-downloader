import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.anees.alldownloader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anees.alldownloader"
        minSdk = 24
        targetSdk = 35
        versionCode = 11
        versionName = "1.1.0"
        buildConfigField("String", "GITHUB_REPO", "\"anees-dahot/all-downloader\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreProps.isNotEmpty()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:$ytdl")

    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
