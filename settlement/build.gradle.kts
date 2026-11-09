plugins {
    id("truesim.java-library-conventions")
}

description = "Procedural settlements for true-sim: a village's plan on any terrain (roads by least-cost paths over " +
    "slope, a square and its church, plots along the roads, buildings as specs, props, lamps and fences), clearings " +
    "and grading for the terrain, and road meshes, deterministic for a seed."

dependencies {
    api(project(":common-procedural-ecology")) // the terrain, its hydrology (HAND, wetness, water) and land use
    testImplementation(project(":common-procedural-ecology-terrain")) // Lauterbrunnen, where effects-terrain's cache holds it
}

// Plans are made in the background (asset lanes, bake steps), never per frame: no allocation-free check.
tasks.test {
    maxHeapSize = "4g"
    if (providers.environmentVariable("TRUESIM_PICTURES").orNull == "true") {
        maxHeapSize = "12g"
        extensions.configure<JacocoTaskExtension> { isEnabled = false }
    }
    providers.gradleProperty("truesim.terrain.cache").orNull?.let { systemProperty("truesim.terrain.cache", it) }
    providers.gradleProperty("pictures").orNull?.let { systemProperty("truesim.pictures", it) }
}
