import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.antidpi.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.antidpi.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        ndk {
            // Python 3.13 on Chaquopy supports 64-bit ABIs (arm64-v8a, x86_64)
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val ks = rootProject.file("antidpi-release.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("ANTIDPI_STORE_PASS") ?: "antidpi123"
                keyAlias = System.getenv("ANTIDPI_KEY_ALIAS") ?: "antidpi"
                keyPassword = System.getenv("ANTIDPI_KEY_PASS") ?: "antidpi123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            val ks = rootProject.file("antidpi-release.jks")
            if (ks.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        val localPy313 = File("C:/Users/pc/AppData/Local/Programs/Python/Python313/python.exe")
        if (localPy313.exists()) {
            buildPython(localPy313.absolutePath)
        }
        pip {
            install("-r", "requirements.txt")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
