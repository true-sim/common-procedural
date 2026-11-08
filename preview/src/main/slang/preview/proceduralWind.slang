// The preview's wind (a geometry hook: a record the scene's hook block carries, no function), as a weather library's
// would be, for proceduralTree.slang's displace().
module proceduralWind;

import hooks;

[ShadingHookParameters]
public struct ProceduralWindParameters
{
    public float3 direction = float3(1, 0, 0); // where it blows, in the world
    public float speed = 0;                    // metres a second: 0 is a calm
    public float gustScale = 25;               // metres between gust fronts
    public float time = 0;                     // seconds
};

[ShadingHook("procedural.wind")]
public struct ProceduralWind : IGeometryHook
{
    public typealias Parameters = ProceduralWindParameters;
};
