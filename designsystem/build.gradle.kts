plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

ktlint {
    baseline.set(file("ktlint-baseline.xml"))
}

detekt {
    baseline = file("detekt-baseline.xml")
}

android {
    namespace = "io.github.radiqalo.picaris.designsystem"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { minSdk = 30 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.material3)
    implementation(libs.compose.preview)
    implementation(libs.materialkolor)
    testImplementation(libs.junit)
}
