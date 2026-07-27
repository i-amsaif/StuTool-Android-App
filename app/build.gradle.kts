import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.stutool.stutool"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stutool.stutool"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }
        
        val geminiKey = properties.getProperty("GEMINI_API_KEY")
        buildConfigField("String", "GEMINI_API_KEY", if (geminiKey != null) "\"$geminiKey\"" else "\"PLACEHOLDER\"")
        
        val groqKey = properties.getProperty("GROQ_API_KEY")
        buildConfigField("String", "GROQ_API_KEY", if (groqKey != null) "\"$groqKey\"" else "\"PLACEHOLDER\"")
        
        val openRouterKey = properties.getProperty("OPENROUTER_API_KEY")
        buildConfigField("String", "OPENROUTER_API_KEY", if (openRouterKey != null) "\"$openRouterKey\"" else "\"PLACEHOLDER\"")
    }

    // TODO: Configure signing for release builds by uncommenting below and providing properties
    // signingConfigs {
    //     create("release") {
    //         storeFile = file(properties.getProperty("storeFile", "release.jks"))
    //         storePassword = properties.getProperty("storePassword")
    //         keyAlias = properties.getProperty("keyAlias")
    //         keyPassword = properties.getProperty("keyPassword")
    //     }
    // }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.pdfbox.android)
    implementation("com.valentinilk.shimmer:compose-shimmer:1.2.0")
    implementation(libs.okhttp)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.github.yalantis:ucrop:2.2.8")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kotlin {
    jvmToolchain(17)
}
