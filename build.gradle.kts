plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)

}

android {
    namespace = "com.example.myapplication"
    // FIX: Updated to 36 to match your dependency requirements
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 36 // Updated to 36
        versionCode = 1
        versionName = "1.0"

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

    // CRITICAL: This prevents the app from crashing when loading the TFLite model
    androidResources {
        noCompress += "tflite"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}
dependencies {
    // --- Core AndroidX Libraries ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.runtime)

    implementation(libs.material)

    // --- Firebase (ONLY FIRESTORE IS NEEDED) ---

    // I removed the database line here to fix your error!

    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.androidx.cardview)
    implementation(libs.firebase.firestore)

    // --- CameraX ---
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    val camerax_version = "1.3.0"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // --- Google Maps & Location ---
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation(libs.play.services.location)
    implementation("com.google.guava:guava:31.1-android")


    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // --- ML Kit & TensorFlow ---
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // --- Networking ---
    implementation("com.squareup.okhttp3:okhttp:4.10.0")


    implementation("androidx.biometric:biometric:1.2.0-alpha05")


    // --- THE CALENDAR LIBRARY ---
    implementation("com.github.prolificinteractive:material-calendarview:2.0.1") {
        exclude(group = "com.android.support")
    }

    // --- Graph Library ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}