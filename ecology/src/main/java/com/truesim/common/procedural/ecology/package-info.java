/**
 * Procedural ecology: which plants grow where. Climate from altitude and aspect, hydrology from the heightfield, snow,
 * wind exposure and substrate give every location its conditions; species' envelopes over them give suitabilities;
 * competition within and between layers gives abundances, sizes, ground cover and plant instances, in tiles that
 * stream deterministically. Engine-agnostic: terrain comes in through {@link
 * com.truesim.common.procedural.ecology.EcologyTerrain}.
 */
package com.truesim.common.procedural.ecology;
