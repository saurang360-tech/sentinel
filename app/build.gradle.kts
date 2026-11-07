import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val uploadSigningFile = rootProject.file("release-signing.properties")
val uploadSigning = Properties().apply {
    if (uploadSigningFile.exists()) uploadSigningFile.inputStream().use { load(it) }
}
android {
    namespace = "dev.titus.sentinel"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.titus.sentinel"
        minSdk = 29
        targetSdk = 37
        versionCode = 13
        versionName = "0.10.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if (uploadSigningFile.exists()) create("upload") {
            storeFile = rootProject.file(uploadSigning.getProperty("storeFile"))
            storePassword = uploadSigning.getProperty("storePassword")
            keyAlias = uploadSigning.getProperty("keyAlias")
            keyPassword = uploadSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".preview" }
        release {
            isDebuggable = false
            if (uploadSigningFile.exists()) signingConfig = signingConfigs.getByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.github.mwiede:jsch:2.28.8")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.12.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
