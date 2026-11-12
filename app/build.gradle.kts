plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Configure these in GRADLE_USER_HOME/gradle.properties, outside the repository.
// Temporary values can also use ORG_GRADLE_PROJECT_<property name> environment variables.
val releaseStoreFile = providers.gradleProperty("OTOKIT_RELEASE_STORE_FILE")
    .orNull?.let { rootProject.file(it) }
val releaseStorePassword = providers.gradleProperty("OTOKIT_RELEASE_STORE_PASSWORD")
val releaseKeyPassword = providers.gradleProperty("OTOKIT_RELEASE_KEY_PASSWORD")
    .orElse(releaseStorePassword)

android {
    namespace = "com.otokit.noharayh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.otokit.noharayh"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword.orNull
            keyAlias = providers.gradleProperty("OTOKIT_RELEASE_KEY_ALIAS")
                .orElse("otokit-release").get()
            keyPassword = releaseKeyPassword.orNull
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Fail before building release if credentials are missing; never silently emit an unsigned APK.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    val signingFile = releaseStoreFile
    val signingStorePassword = releaseStorePassword
    val signingKeyPassword = releaseKeyPassword
    doFirst {
        check(signingFile?.isFile == true) {
            "Set OTOKIT_RELEASE_STORE_FILE to the release keystore path in your local Gradle properties."
        }
        check(!signingStorePassword.orNull.isNullOrEmpty() && !signingKeyPassword.orNull.isNullOrEmpty()) {
            "Set OTOKIT_RELEASE_STORE_PASSWORD in your local Gradle properties or " +
                "ORG_GRADLE_PROJECT_OTOKIT_RELEASE_STORE_PASSWORD for this build."
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.window)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.dynamicanimation)
    testImplementation(libs.junit)
}

