import java.util.Properties
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}


val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val apiBaseUrl = (providers.gradleProperty("WINE_API_BASE_URL").orNull
    ?: providers.environmentVariable("WINE_API_BASE_URL").orNull
    ?: localProperties.getProperty("WINE_API_BASE_URL", "http://10.0.2.2:8000/"))
    .trim().trimEnd('/') + "/"
val apiUri = URI(apiBaseUrl)
require(apiUri.scheme in listOf("http", "https") && apiUri.host != null &&
        apiUri.rawUserInfo == null && apiUri.rawQuery == null && apiUri.rawFragment == null) {
    "WINE_API_BASE_URL must be an http(s) URL without credentials, query or fragment"
}
fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Keep HTTP permission scoped to the configured API host, without committing its address.
val apiNetworkResources = layout.buildDirectory.dir("generated/res/apiNetwork")
val generateApiNetworkConfig = tasks.register("generateApiNetworkConfig") {
    inputs.property("apiHost", apiUri.host)
    inputs.property("allowHttp", apiUri.scheme == "http")
    outputs.dir(apiNetworkResources)
    doLast {
        val output = apiNetworkResources.get().file("xml/network_security_config.xml").asFile
        output.parentFile.mkdirs()
        output.writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <network-security-config>
                <base-config cleartextTrafficPermitted="false">
                    <trust-anchors><certificates src="system" /></trust-anchors>
                </base-config>
                <domain-config cleartextTrafficPermitted="${apiUri.scheme == "http"}">
                    <domain includeSubdomains="false">${apiUri.host}</domain>
                </domain-config>
            </network-security-config>
        """.trimIndent() + "\n")
    }
}

android {
    namespace = "com.wineapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wineapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "BASE_URL", buildConfigString(apiBaseUrl))
        buildConfigField("String", "GIGACHAT_AUTH_KEY", "\"${localProperties.getProperty("GIGACHAT_AUTH_KEY", "")}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").res.srcDir(apiNetworkResources)
    androidResources { noCompress += "tflite" }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

tasks.named("preBuild").configure { dependsOn(generateApiNetworkConfig) }

dependencies {
    implementation("com.google.ai.edge.litert:litert:2.2.0")
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Material3 Compose
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material3:material3-window-size-class:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.compose.ui:ui-graphics:1.6.7")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.7")
    implementation("androidx.compose.foundation:foundation:1.6.7")
    implementation("androidx.compose.runtime:runtime-livedata:1.6.7")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-android-compiler:2.58")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // Retrofit + Kotlinx Serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // CameraX
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("androidx.camera:camera-video:1.4.0")

    // Photo Picker
    implementation("androidx.activity:activity:1.9.0")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // EXIF orientation normalization (camera/gallery photos before Base64 upload)
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Home screen widget (recent wines + scanner shortcut)
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // Coroutines & Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.7")
    debugImplementation("androidx.compose.ui:ui-tooling-preview:1.6.7")
}
