plugins {
    id("truesim.java-library-conventions")
}

description = "A settlement's roads in effects-terrain's surface, as its terrain modifier: the terrain graded to the " +
    "road meshes (meeting their edges, cut and filled into the natural ground) and their verges laid into its " +
    "material. Optional: the settlement itself needs no terrain library."

dependencies {
    api(project(":common-procedural-settlement"))
    api("com.truesim.effects:effects-terrain-data:0.5.0") // TerrainModifier, TerrainSurface: CPU only, no GPU
    testImplementation(project(":common-procedural-ecology-terrain"))
}

tasks.test {
    maxHeapSize = "4g"
}
