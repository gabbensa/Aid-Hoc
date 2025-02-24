// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
}

buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.3.15")
    }
}

allprojects {
    // No repository declarations here
}
