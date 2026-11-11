import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(prop: String, env: String): String? = ksProps.getProperty(prop) ?: System.getenv(env)
val ksPath = signing("storeFile", "SIGNING_KEYSTORE_PATH")
val ksStorePw = signing("storePassword", "SIGNING_STORE_PASSWORD")
val ksAlias = signing("keyAlias", "SIGNING_KEY_ALIAS")
val ksKeyPw = signing("keyPassword", "SIGNING_KEY_PASSWORD")
val haveSigning = ksPath != null && ksStorePw != null && ksAlias != null && ksKeyPw != null &&
    rootProject.file(ksPath).exists()

android {
    namespace = "dk.datestuff.hunt"
    compileSdk = 37

    defaultConfig {
        applicationId = "dk.datestuff.hunt"
        minSdk = 28
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
    }

    signingConfigs {
        if (haveSigning) {
            create("release") {
                storeFile = rootProject.file(ksPath!!)
                storePassword = ksStorePw
                keyAlias = ksAlias
                keyPassword = ksKeyPw
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (haveSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint { abortOnError = true; checkReleaseBuilds = false }
    packaging { jniLibs.useLegacyPackaging = false }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.navigation.compose)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.datastore)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.play.location)
    implementation(libs.mlkit.barcode)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.osmdroid)
    implementation(libs.coroutines.android)
    implementation(libs.arsceneview)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
}

// A release artifact must always carry the durable update signing identity.
tasks.matching { it.name == "validateSigningRelease" || it.name == "packageRelease" }.configureEach {
    doFirst { check(haveSigning) { "Release signing is required: configure keystore.properties or SIGNING_* environment variables." } }
}
