import java.util.Properties

val mapTilerProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}
val mapTilerApiKey = mapTilerProperties.getProperty("MAPTILER_API_KEY", "")
val firebaseDbUrl = mapTilerProperties.getProperty("FIREBASE_DB_URL", "")
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.ble"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ble"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        resValue(
            "string",
            "maptiler_api_key",
            mapTilerApiKey
        )
        resValue(
            "string",
            "firebase_db_url",
            firebaseDbUrl
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose.android)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation(libs.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-database")
    // Map dependency
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    implementation("ch.hsr:geohash:1.4.0")

    implementation("ch.hsr:geohash:1.4.0")
    // Location dependency
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("com.google.firebase:firebase-auth")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
