// proceduralTree.slang's parameters, apart from it: the build reads material records before it writes the hooks'
// dispatch, so a record's module must not import `indirect`.
module proceduralTreeParameters;

import materials;
import materials.metallicRoughness;

/** A common-procedural tree: shaded as metallic-roughness, swaying by the wind data of its vertices. */
[MaterialParameters(GEOMETRY_DISPLACE)]
public struct ProceduralTreeParameters
{
    public MetallicRoughnessParameters base;
    public float trunkBend = 0.0005;   // radians of lean at the top per (m/s) squared
    public float maxLean = 0.12;       // radians: the most the trunk leans
    public float trunkFrequency = 0.25; // cycles a second
    public float branchSway = 0.04;    // radians a branch swings per m/s at weight 1
    public float branchFrequency = 0.7; // cycles a second of a branch 4 m long
    public float leafFlutter = 0.006;  // metres a leaf flutters per m/s at weight 1
    public float leafFrequency = 3;    // cycles a second
};
