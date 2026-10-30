import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("org.jetbrains.kotlinx.kover")
}

android {
    namespace = "com.patryksliwinski.quicknotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.patryksliwinski.quicknotes"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.6.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

android {
    lint {
        disable += "StartActivityAndCollapseDeprecated"
    }
}

kover {
    reports {
        filters {
            excludes {
                androidGeneratedClasses()
                classes(
                    "com.patryksliwinski.quicknotes.ui.MainActivity*",
                    "com.patryksliwinski.quicknotes.ui.*ScreenKt*",
                    "com.patryksliwinski.quicknotes.receiver.*Layout*",
                    "com.patryksliwinski.quicknotes.receiver.*Provider*",
                    "com.patryksliwinski.quicknotes.tile.*",
                )
            }
        }
        verify {
            rule("Min 80% line coverage") {
                minBound(80)
            }
            rule("Min 80% branch coverage") {
                minBound(80, CoverageUnit.BRANCH)
            }
        }
    }
}
