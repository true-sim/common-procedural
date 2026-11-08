plugins {
    id("truesim.java-conventions")
    id("truesim.slang")
}

description = "Pictures and films of common-procedural's assets through the engine, with a minimal tree material " +
    "swaying by the wind format. Not published: the generators never depend on the engine."

dependencies {
    implementation("com.truesim.engine:engine-render:0.195.0") // material types and geometry hooks
    implementation(project(":common-procedural"))
    implementation(project(":common-procedural-assets"))
    testImplementation(testFixtures("com.truesim.engine:engine-render:0.195.0")) // the headless renderer
    testImplementation("com.truesim.scene:scene-meshbuild:0.70.0")
}

// The wind hook's and the tree material's records as Java
slang {
    javaPackage = "com.truesim.common.procedural.preview.materials"
}

coverage {
    minimum = 0.0 // pictures and films, not library code
}

tasks.test {
    maxHeapSize = "6g"
    extensions.configure<JacocoTaskExtension> { isEnabled = false }
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    providers.gradleProperty("films").orNull?.let { systemProperty("truesim.films", it) }
}
