val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceSets {
        main {
            java.srcDirs("libs/manifest-editor/lib/src/main/java")
            resources.srcDirs("libs/manifest-editor/lib/src/main")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

dependencies {
    implementation(projects.apkzlib)
    implementation(projects.share.java)
    implementation(lspatch.commons.io)
    implementation(lspatch.beust.jcommander)
    implementation(lspatch.google.gson)
    implementation(lspatch.neonorbit.dexplore)
}
