plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // AGP 9: Kotlin embutido — o plugin org.jetbrains.kotlin.android não é mais aplicado.
}

android {
    namespace = "dev.hunterlab.app"
    // Compose 1.13 (BOM 2026.11.01) exige compilar contra a API 37.1 ou superior.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "dev.hunterlab.app"
        // Android 12+: permissões BLUETOOTH_SCAN/CONNECT sem pedir localização.
        minSdk = 31
        targetSdk = 37
        versionCode = 6
        versionName = "0.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.maplibre.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

// O teste de cache lê este snapshot do módulo core. Mudanças no dado devem invalidar o resultado do app.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.file(rootProject.file("core/src/test/resources/validation/caruaru/urban.json"))
        .withPropertyName("caruaruRadarSnapshot")
}
