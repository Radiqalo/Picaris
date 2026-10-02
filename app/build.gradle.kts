plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.radiqalo.picaris"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = "io.github.radiqalo.picaris"
        minSdk = 30
        targetSdk = 37
        versionCode = 5
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        create("qa") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            ndk { abiFilters += "arm64-v8a" }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.adaptive.nav3)
    implementation(libs.nav3.runtime)
    implementation(libs.nav3.ui)
    implementation(libs.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.telephoto)
    implementation(libs.browser)
    implementation(libs.documentfile)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.serialization)
    implementation(libs.work)
    implementation(libs.profileinstaller)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.compose.test)
}
