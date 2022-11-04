plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        multiDexEnabled = false
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_OBJECT_PATH_MAX=1024"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/jni/CMakeLists.txt")
        }
    }
}

androidComponents.onVariants { variant ->
    val variantCapped = variant.name.capitalize()

    task<Copy>("copyDex$variantCapped") {
        dependsOn("assemble$variantCapped")
        from("$buildDir/intermediates/dex/${variant.name}/mergeDex$variantCapped/classes.dex")
        rename("classes.dex", "loader")
        into("${rootProject.projectDir}/out/assets/mrvdata")
    }

    task<Copy>("copySo$variantCapped") {
        dependsOn("assemble$variantCapped")
        from(
            fileTree(
                "dir" to "$buildDir/intermediates/stripped_native_libs/${variant.name}/out/lib",
                "include" to listOf("**/liblspatch.so")
            )
        )
        rename("liblspatch.so", "liblspatch")
        into("${rootProject.projectDir}/out/assets/mrvdata/so")
    }

    task("copy$variantCapped") {
        dependsOn("copySo$variantCapped")
        dependsOn("copyDex$variantCapped")

        doLast {
            println("Dex and so files has been copied to ${rootProject.projectDir}${File.separator}out")
        }
    }
}

dependencies {
    compileOnly(projects.hiddenapi.stubs)
    implementation(projects.core)
    implementation(projects.hiddenapi.bridge)
    implementation(projects.services.daemonService)
    implementation(projects.share.android)
    implementation(projects.share.java)

    implementation("com.google.code.gson:gson:2.9.1")
}
