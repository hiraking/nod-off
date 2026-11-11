plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.hiraking.nodoff"
    compileSdk = 36
    // この PC の SDK に入っているバージョン（AGP に別のバージョンを取らせない）
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.hiraking.nodoff"
        // 対象は Galaxy S26（Android 16・API 36）だけ
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            // デーモンは OS の文字コードで動かしている（gradle.properties）。テストの中は UTF-8 にそろえる
            it.defaultCharacterEncoding = "UTF-8"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")

    testImplementation("junit:junit:4.13.2")
}
