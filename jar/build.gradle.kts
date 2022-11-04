import java.text.SimpleDateFormat
import java.util.Date

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
        attributes( "Program-Name" to archiveBaseName)
        attributes( "Program-Version" to jarVersion)
        attributes( "Program-Build-Time" to buildDate)
    }
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    into("assets") {
        from("src/main/assets")
        from("${rootProject.projectDir}/out/assets")
    }

    exclude(
        "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.MF",
        "META-INF/*.txt", "META-INF/versions/**", "assets/new_keystore"
    )
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
