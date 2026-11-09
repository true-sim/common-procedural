plugins {
    id("truesim.java-library-conventions")
}

description = "The procedural ecology over effects-terrain's surface: heights and normals from its CPU queries, " +
    "substrate from its layers. Optional: the ecology itself takes any terrain."

dependencies {
    api(project(":common-procedural-ecology"))
    api("com.truesim.effects:effects-terrain-data:0.5.0") // TerrainSurface, TerrainSite, TerrainModifier: CPU only
}

// The Lauterbrunnen benchmark and maps read effects-terrain's cache (-Ptruesim.terrain.cache=<dir>, else
// ../effects-terrain/terrain-cache); they are skipped where it does not hold the site.
tasks.test {
    maxHeapSize = "4g"
    if (providers.environmentVariable("TRUESIM_BENCHMARK").orNull == "true"
        || providers.environmentVariable("TRUESIM_PICTURES").orNull == "true") {
        maxHeapSize = "12g"
        extensions.configure<JacocoTaskExtension> { isEnabled = false }
    }
    providers.gradleProperty("truesim.terrain.cache").orNull?.let { systemProperty("truesim.terrain.cache", it) }
    providers.gradleProperty("pictures").orNull?.let { systemProperty("truesim.pictures", it) }
}
