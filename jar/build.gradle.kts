import proguard.gradle.ProGuardTask
import java.text.SimpleDateFormat
import java.util.Date

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("gradle.plugin.com.github.johnrengelman:shadow:7.1.2")
        classpath("com.guardsquare:proguard-gradle:7.2.1") {
            exclude(group = "com.android.tools.build")
        }
    }
}
apply(plugin = "com.github.johnrengelman.shadow")

val jarVersion: String by rootProject.extra

val verCode: Int by rootProject.extra
val verName: String by rootProject.extra
val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra

plugins {
    id("java-library")
}

java {
    sourceCompatibility = androidSourceCompatibility
    targetCompatibility = androidTargetCompatibility
}

dependencies {
    implementation(projects.patch)
}

fun Jar.configure(variant: String) {
    archiveBaseName.set("${rootProject.name}-$jarVersion-$variant")
    destinationDirectory.set(file("${rootProject.projectDir}/out/$variant"))
    val buildDate = SimpleDateFormat("dd-MM-yyyy hh:mm:ss aa").format(Date())
    manifest {
        attributes("Main-Class" to "org.lsposed.patch.LSPatch")
        attributes( "Program-Name" to rootProject.name)
        attributes( "Program-Version" to jarVersion)
        attributes( "Program-Build-Time" to buildDate)
    }
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    into("assets") {
        from("src/main/assets")
        from("${rootProject.projectDir}/out/assets")
    }
    from(rootProject.projectDir) { include("NOTICE") }

    exclude(
        "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.MF",
        "META-INF/*.txt", "META-INF/versions/**", "assets/new_keystore"
    )
}

val proguard = task<ProGuardTask>("proguard")  {
    tasks.getByName<Jar>("shadowJar").let { shadow ->
        dependsOn(shadow)
        injars(shadow.archiveFile)
        outjars(file("${rootProject.projectDir}/out/${rootProject.name}-${jarVersion}.jar"))
    }
    val home = System.getProperty("java.home")
    if (!JavaVersion.current().isJava9Compatible) {
        libraryjars("$home/lib/rt.jar")
    } else {
        libraryjars(mapOf(Pair("jarfilter", "!**.jar"), Pair("filter", "!module-info.class")), "$home/jmods")
    }
    dontoptimize()
    repackageclasses("mrvp")
    keepattributes("*Annotation*")
    keep("class org.lsposed.** { *; }")
    keep("class com.beust.jcommander.** { *; }")
    keep("class com.android.apksig.** { *; }")
    keep("class com.android.tools.build.apkzlib.** { *; }")
    dontwarn("com.android.tools.build.apkzlib.**")
    keepclassmembers("enum * { public static **[] values(); public static ** valueOf(java.lang.String); }")
}

tasks.register<Jar>("buildDebug") {
    dependsOn(":meta-loader:copyDebug")
    dependsOn(":patch-loader:copyDebug")
    configure("debug")
}

tasks.register<Jar>("buildRelease") {
    dependsOn(":meta-loader:copyRelease")
    dependsOn(":patch-loader:copyRelease")
    configure("release")
}

tasks.getByName<Jar>("shadowJar") {
    dependsOn(":meta-loader:copyRelease")
    dependsOn(":patch-loader:copyRelease")
    configure("proguard")
}

tasks.register<Jar>("mrvRelease") {
    file("${rootProject.projectDir}/out/assets").let {
        delete(it)
        if (it.exists()) {
            throw GradleException("Failed to delete old assets")
        }
    }
    dependsOn("buildRelease")
    configure("release")
    dependsOn(proguard)
    proguard.mustRunAfter("buildRelease")
    doLast {
        delete("${rootProject.projectDir}/out/proguard")
    }
}
