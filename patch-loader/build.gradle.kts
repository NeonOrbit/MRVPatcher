plugins {
    alias(libs.plugins.agp.app)
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

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/jni/CMakeLists.txt")
        }
    }
    namespace = "org.lsposed.lspatch.loader"
}

androidComponents.onVariants { variant ->
    val variantCapped = variant.name.replaceFirstChar { it.uppercase() }

    task<Copy>("copyDex$variantCapped") {
        dependsOn("assemble$variantCapped")
        from(if (variant.buildType == "release") {
            project.layout.buildDirectory.file("intermediates/dex/${variant.name}/minify${variantCapped}WithR8")
        } else {
            project.layout.buildDirectory.file("intermediates/dex/${variant.name}/mergeDex$variantCapped")
        })
        rename("classes.dex", "loader")
        into("${rootProject.projectDir}/out/assets/${variant.name}/mrvdata")
    }

    task<Copy>("copySo$variantCapped") {
        dependsOn("assemble$variantCapped")
        from(
            fileTree(
                "dir" to project.layout.buildDirectory.file("intermediates/stripped_native_libs/${variant.name}/strip${variantCapped}DebugSymbols/out/lib"),
                "include" to listOf("**/liblspatch.so")
            )
        )
        rename("liblspatch.so", "liblspatch")
        into("${rootProject.projectDir}/out/assets/${variant.name}/mrvdata/so")
    }

    task("copy$variantCapped") {
        dependsOn("copySo$variantCapped")
        dependsOn("copyDex$variantCapped")

        doLast {
            println("Dex and so files has been copied to ${rootProject.projectDir}${File.separator}out")
        }
    }
}

tasks.getByName("clean").doLast {
    delete(project.layout.projectDirectory.dir(".cxx"))
}

dependencies {
    compileOnly(projects.hiddenapi.stubs)
    implementation(projects.core)
    implementation(projects.hiddenapi.bridge)
    implementation(projects.services.daemonService)
    implementation(projects.share.android)
    implementation(projects.share.java)
    implementation(libs.gson)
}
