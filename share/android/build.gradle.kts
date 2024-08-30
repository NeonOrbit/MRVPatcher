plugins {
    alias(libs.plugins.agp.lib)
}

android {
    namespace = "org.lsposed.lspatch.share"

    buildFeatures {
        androidResources = false
        buildConfig = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(projects.services.daemonService)
}
