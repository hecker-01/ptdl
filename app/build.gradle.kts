plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

android {
    namespace = "dev.heckr.ptdl"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.heckr.ptdl"
        minSdk = 31
        targetSdk = 36
        versionCode = gitCommitCount
        versionName = "26.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("cordium-dev.keystore")
            storePassword = "CordiumPassw"
            keyAlias = "cordium"
            keyPassword = "CordiumPassw"
        }
        create("release") {
            storeFile = file("cordium-release.keystore")
            storePassword = "CordiumPassw"
            keyAlias = "cordium"
            keyPassword = "CordiumPassw"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output?.outputFileName = when {
                buildType.name == "debug" -> "ptdl-dev-${versionName.replace(Regex("-.*"), "").replace(".", "-")}.apk"
                buildType.name == "release" -> "ptdl-release-${versionName.replace(".", "-")}.apk"
                else -> output?.outputFileName ?: ""
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.json)
    implementation(libs.okhttp)
    implementation(libs.coil)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
