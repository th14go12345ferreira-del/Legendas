plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.thiago.legendaoffline"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.thiago.legendaoffline"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    ndkVersion = "25.2.9519653"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
            
        }
    }

    buildTypes {
        debug {
            externalNativeBuild {
                cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" }
            }
        }
        release { isMinifyEnabled = false }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
