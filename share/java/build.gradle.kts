val apiCode: Int by rootProject.extra
val verCode: Int by rootProject.extra
val verName: String by rootProject.extra
val coreVerCode: Int by rootProject.extra
val coreVerName: String by rootProject.extra
val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra

plugins {
    id("java-library")
}

java {
    sourceCompatibility = androidSourceCompatibility
    targetCompatibility = androidTargetCompatibility
}

val generateTask = task<Copy>("generateJava") {
    val template = mapOf(
        "apiCode" to apiCode,
        "verCode" to verCode,
        "verName" to verName,
        "coreVerCode" to coreVerCode,
        "coreVerName" to coreVerName
    )
    inputs.properties(template)
    from("src/template/java")
    into(project.layout.buildDirectory.file("generated/java"))
    expand(template)
}

sourceSets["main"].java.srcDir(project.layout.buildDirectory.file("generated/java"))
tasks["compileJava"].dependsOn(generateTask)
