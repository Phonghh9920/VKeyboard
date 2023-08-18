import java.util.*
import java.io.*

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val calendar = Calendar.getInstance()
val year = calendar.get(Calendar.YEAR) - 2022
val month = calendar.get(Calendar.MONTH) + 1
val day = calendar.get(Calendar.DAY_OF_MONTH)
val versCode = year * 1200 + month * 100 + day
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
keystoreProperties.load(FileInputStream(keystorePropertiesFile))

android {
    namespace = "com.vladgba.keyb"
    compileSdk = 33

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    defaultConfig {
        minSdk = 21
        targetSdk = 33
        applicationId = "com.vladgba.keyb"
        versionCode = versCode
        versionName = "2.$versCode"
        resValue("string", "app_name", "VKeyboard 2")
        resValue("string", "version", "2.$versCode")
        signingConfig = signingConfigs.getByName("release")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "d"
            resValue("string", "app_name", "@VKeyb$versCode")
            resValue("string", "version", "2.$versCode")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
