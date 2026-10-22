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

    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { properties.load(it) }
    }

    defaultConfig {
        applicationId = "com.stutool.stutool"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        val geminiKey = properties.getProperty("GEMINI_API_KEY")
        buildConfigField("String", "GEMINI_API_KEY", if (geminiKey != null) "\"$geminiKey\"" else "\"PLACEHOLDER\"")
        
        val groqKey = properties.getProperty("GROQ_API_KEY")
        buildConfigField("String", "GROQ_API_KEY", if (groqKey != null) "\"$groqKey\"" else "\"PLACEHOLDER\"")
        
        val openRouterKey = properties.getProperty("OPENROUTER_API_KEY")
        buildConfigField("String", "OPENROUTER_API_KEY", if (openRouterKey != null) "\"$openRouterKey\"" else "\"PLACEHOLDER\"")

        val ocrSpaceKey = properties.getProperty("OCR_SPACE_API_KEY")
        buildConfigField("String", "OCR_SPACE_API_KEY", if (!ocrSpaceKey.isNullOrBlank()) "\"$ocrSpaceKey\"" else "\"\"")

        val web3FormsKey = properties.getProperty("WEB3FORMS_ACCESS_KEY")
        buildConfigField("String", "WEB3FORMS_ACCESS_KEY", if (!web3FormsKey.isNullOrBlank()) "\"$web3FormsKey\"" else "\"PLACEHOLDER\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = properties.getProperty("RELEASE_STORE_FILE")
                ?: properties.getProperty("storeFile")
                ?: System.getenv("RELEASE_STORE_FILE")
            val keystorePassword = properties.getProperty("RELEASE_STORE_PASSWORD")
                ?: properties.getProperty("storePassword")
                ?: System.getenv("RELEASE_STORE_PASSWORD")
            val keyAliasName = properties.getProperty("RELEASE_KEY_ALIAS")
                ?: properties.getProperty("keyAlias")
                ?: System.getenv("RELEASE_KEY_ALIAS")
            val keyPass = properties.getProperty("RELEASE_KEY_PASSWORD")
                ?: properties.getProperty("keyPassword")
                ?: System.getenv("RELEASE_KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank()) {
                val candidateFile = file(keystorePath)
                val resolvedFile = if (candidateFile.exists()) candidateFile else rootProject.file(keystorePath)
                if (resolvedFile.exists()) {
                    storeFile = resolvedFile
                    storePassword = keystorePassword
                    keyAlias = keyAliasName
                    keyPassword = keyPass
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
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
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
}

kotlin {
    jvmToolchain(17)
}
