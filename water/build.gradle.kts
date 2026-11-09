plugins {
    id("truesim.java-library-conventions")
}

description = "Procedural water for true-sim: channel networks from the terrain's flow (or mapped watercourses), " +
    "width and depth from discharge, river surfaces with flow per vertex, carved beds and banks, bed stones, " +
    "waterfalls with their sheets, jets, spray and mist, deterministic for a seed."

dependencies {
    api(project(":common-procedural-ecology")) // EcologyTerrain (any terrain), and common-procedural: meshes, Seeds
    api("com.truesim.engine:engine-settings:0.198.0") // WaterSettings: declared settings, no GPU
    testImplementation("com.truesim.effects:effects-terrain-data:0.5.0") // Lauterbrunnen's surface, for pictures
}

// Networks are built in the background (asset lanes, bake steps), never per frame: no allocation-free check.
// The Lauterbrunnen pictures and film (TRUESIM_PICTURES=true) read effects-terrain's cache
// (-Ptruesim.terrain.cache=<dir>, else ../effects-terrain/terrain-cache) and measure the code, not coverage.
tasks.test {
    maxHeapSize = "4g"
    if (providers.environmentVariable("TRUESIM_PICTURES").orNull == "true") {
        maxHeapSize = "12g"
        extensions.configure<JacocoTaskExtension> { isEnabled = false }
    }
    providers.gradleProperty("truesim.terrain.cache").orNull?.let { systemProperty("truesim.terrain.cache", it) }
    providers.gradleProperty("pictures").orNull?.let { systemProperty("truesim.pictures", it) }
}
