plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.pixivnext.core"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { minSdk = 37 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    api(libs.coroutines)
    api(libs.serialization)
    api(libs.paging.runtime)
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    api(libs.okhttp)
    api(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    api(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
