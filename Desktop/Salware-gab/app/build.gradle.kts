plugins {
    alias(libs.plugins.androidApplication)   // Alias for Android application plugin from version catalog
    id("com.google.gms.google-services")    // Directly applying Google Services plugin
}

android {
    namespace = "com.test.demibluetoothchatting"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.test.demibluetoothchatting"
        minSdk = 24
        targetSdk = 34
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.database)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Firebase and FirebaseUI dependencies
    implementation("com.firebaseui:firebase-ui-database:8.0.1")
    implementation("com.google.firebase:firebase-database:20.0.5")
}

