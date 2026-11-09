/**
 * Procedural water: rivers, streams and waterfalls over any terrain. Channel networks come from the terrain's flow
 * (Priority-Flood, single flow directions, catchments) or from mapped watercourses burned into it; hydraulic geometry
 * gives every point its discharge, width, depth and speed; the surfaces are meshes in chunks carrying the flow per
 * vertex ({@link com.truesim.common.procedural.water.WaterVertex}); the beds and banks are fields a terrain carves and
 * grass thins by ({@link com.truesim.common.procedural.water.WaterField}); stones lie in the beds, and waterfalls are
 * found where a channel drops over steep ground, with their sheets, jets, spray and mist. Engine-agnostic: terrain
 * comes in through {@link com.truesim.common.procedural.ecology.EcologyTerrain}.
 */
package com.truesim.common.procedural.water;
