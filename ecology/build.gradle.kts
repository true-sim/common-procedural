plugins {
    id("truesim.java-library-conventions")
}

description = "Procedural ecology for true-sim: which plants grow where, from altitude, aspect, hydrology, snow, " +
    "wind exposure and substrate, with competition and streaming tiles: density and size fields, ground cover and " +
    "plant instances, deterministic for a seed."

dependencies {
    api(project(":common-procedural")) // Seeds, and common-math: Maths, Noise
}

// Ecology tiles are generated in the background (asset lanes, bake steps), never per frame: no allocation-free check.
// The benchmark (TRUESIM_BENCHMARK=true) and map pictures (TRUESIM_PICTURES=true) measure the code, not coverage.
tasks.test {
    maxHeapSize = "4g"
    if (providers.environmentVariable("TRUESIM_BENCHMARK").orNull == "true"
        || providers.environmentVariable("TRUESIM_PICTURES").orNull == "true") {
        maxHeapSize = "8g"
        extensions.configure<JacocoTaskExtension> { isEnabled = false }
    }
    providers.gradleProperty("truesim.terrain.cache").orNull?.let { systemProperty("truesim.terrain.cache", it) }
    providers.gradleProperty("pictures").orNull?.let { systemProperty("truesim.pictures", it) }
}
