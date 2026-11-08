plugins {
    id("truesim.java-library-conventions")
    id("truesim.allocation-free")
}

description = "Procedural assets for true-sim: trees, rocks and more, generated deterministically from a seed."

dependencies {
    api("com.truesim.common:common-math:0.6.1")
}
