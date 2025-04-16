import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun getLocalProperty(key: String): String {
    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        // Use a try-with-resources equivalent for safety
        localPropertiesFile.inputStream().use { fis ->
            properties.load(fis)
        }
    } else {
        println("Warning: local.properties file not found at ${rootProject.projectDir}")
    }
    return properties.getProperty(key, "").also {
        if (it.isEmpty()) {
            println("Warning: Property '$key' not found in local.properties")
        }
    }
}

android {
    namespace = "com.robertopineda.koekoi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.robertopineda.koekoi"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"${getLocalProperty("DEEPSEEK_API_KEY")}\"")
            resValue("string", "deepseek_api_key", "\"${getLocalProperty("DEEPSEEK_API_KEY")}\"") // Optional for XML
        }
        release {
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"${getLocalProperty("DEEPSEEK_API_KEY")}\"")
            resValue("string", "deepseek_api_key", "\"${getLocalProperty("DEEPSEEK_API_KEY")}\"") // Optional for XML
        }
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10" // Update to latest if needed
    }
    packaging {
        resources.excludes.add("META-INF/CONTRIBUTORS.md")
        resources.excludes.add("META-INF/LICENSE.md")
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

    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material:material:1.6.0")
    implementation("androidx.compose.runtime:runtime-livedata:1.6.0")
    implementation("androidx.navigation:navigation-compose:2.7.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}