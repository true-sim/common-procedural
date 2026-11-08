plugins {
    id("truesim.java-library-conventions")
    id("truesim.allocation-free")
}

description = "Procedural assets for true-sim: trees, rocks and more, generated deterministically from a seed."

dependencies {
    api("com.truesim.common:common-math:0.7.0") // PolylineFrames, Octahedral, Meshes, ridged and cellular noise, PoissonDisc
}

// The benchmark (TRUESIM_BENCHMARK=true) measures the code, not its coverage probes; a generator per core needs room.
tasks.test {
    if (providers.environmentVariable("TRUESIM_BENCHMARK").orNull == "true") {
        maxHeapSize = "6g"
        extensions.configure<JacocoTaskExtension> { isEnabled = false }
    }
}
