pluginManagement {
    // Use ../build-logic when present; override with -Ptruesim.build-logic=<path> (or false).
    providers.gradleProperty("truesim.build-logic").orElse("../build-logic").get()
        .takeIf { file(it).isDirectory }?.let { includeBuild(it) }
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.github.com/true-sim/build-logic") {
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
}

plugins {
    id("truesim.settings") version "0.19.1"
}

rootProject.name = "common-procedural"

// The generators: the direct Java API, on common-math only (engine-agnostic: tools, servers, tests).
include("common-procedural")
project(":common-procedural").projectDir = file("procedural")

// Ecology: which plants grow where (climate by altitude and aspect, hydrology, competition), as fields and instances.
include("common-procedural-ecology")
project(":common-procedural-ecology").projectDir = file("ecology")

// The ecology over effects-terrain's surface (its CPU queries): optional, so the ecology needs no terrain library.
include("common-procedural-ecology-terrain")
project(":common-procedural-ecology-terrain").projectDir = file("ecology-terrain")

// Water: channel networks from the terrain's flow (or mapped watercourses), river surfaces, beds, stones, waterfalls.
include("common-procedural-water")
project(":common-procedural-water").projectDir = file("water")

// Settlements: a village's plan on the terrain (roads, square, plots, buildings as specs, props, fences), clearings,
// grading and road meshes.
include("common-procedural-settlement")
project(":common-procedural-settlement").projectDir = file("settlement")

// Procedural assets through the scene's asset system: recipe files imported into meshes and models, cached.
include("common-procedural-assets")
project(":common-procedural-assets").projectDir = file("assets")

// Pictures and films through the engine, only with -Ptruesim.preview=true: the engine stays out of the library's build
// (engine 0.197.0 has per-material displacement: no branch needed).
val preview = providers.gradleProperty("truesim.preview").orNull == "true"
if (preview) {
    include("preview")
}

// Sibling checkouts are used as included builds when present (override with -Ptruesim.<repo>=<path>, or false).
val truesimRepos = listOf("build-logic", "common-math", "common-jobs", "scene") +
    (if (preview) listOf("common-state", "common-graph", "engine") else listOf())
// Libraries only fetched, never included from a sibling (effects-terrain's own build includes the engine's)
val fetchedRepos = listOf("effects-terrain") + (if (preview) listOf() else listOf("engine")) // engine-settings
for (repo in truesimRepos) {
    providers.gradleProperty("truesim.$repo").orElse("../$repo").get()
        .takeIf { file(it).isDirectory }?.let { includeBuild(it) }
}

dependencyResolutionManagement {
    repositories {
        for (repo in truesimRepos + fetchedRepos) {
            maven("https://maven.pkg.github.com/true-sim/$repo") {
                credentials {
                    username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                    password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
                }
            }
        }
    }
}
