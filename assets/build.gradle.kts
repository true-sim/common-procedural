plugins {
    id("truesim.java-library-conventions")
    id("truesim.allocation-free")
}

description = "Procedural assets through true-sim's scene asset system: recipe files imported into meshes and " +
    "models, generated in the background and cached by recipe and generator version."

dependencies {
    api(project(":common-procedural"))
    api(project(":common-procedural-ecology")) // ecology recipes
    api(project(":common-procedural-water")) // water recipes, river and waterfall meshes
    api(project(":common-procedural-settlement")) // settlement recipes, plans and road meshes
    api("com.truesim.scene:scene-assets:0.70.0") // the asset cache, paths, importers
    api("com.truesim.scene:scene-render:0.70.0") // meshes, models and materials as render assets
    implementation("com.truesim.scene:scene-meshbuild:0.70.0") // meshlets
    api("com.truesim.engine:engine-settings:0.198.0") // ProceduralSettings: declared settings, no GPU
}

// meshoptimizer through LWJGL
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
