plugins {
    alias(libs.plugins.agp.app)
}

android {
    namespace = "com.sagor.universalforcerotation.testapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sagor.universalforcerotation.testapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 110
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
}
