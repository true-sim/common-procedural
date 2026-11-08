// A minimal tree material for previewing common-procedural's trees: metallic-roughness shading, and displace() reading
// the wind format of ProceduralVertex. COLOR: r trunk weight, g branch weight, b the leaf weight (high nibble, /15)
// and the phase (low nibble, /16), a 1. UV1: the main branch's pivot height (it sways about (0, pivot, 0)) and reach.
// The trunk leans about its foot by its weight, kept to its length; branches turn about their pivot by theirs, kept to
// theirs; leaves flutter along their normals.
module proceduralTree;

import materials;
import materials.metallicRoughness;
import preview.proceduralWind;
__exported import preview.proceduralTreeParameters;

/** procedural.wind's index in the shading hooks' dispatch, resolved when a shader links. */
extern static const uint RUN_HOOK_PROCEDURAL_WIND_INDEX;

static const float TAU = 6.28318530718;

uint previewHash(uint x)
{
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

[MaterialType("proceduralTree")]
public struct ProceduralTree : IMaterial
{
    public typealias Parameters = ProceduralTreeParameters;

    public static Surface surface(ProceduralTreeParameters p, MaterialRef m, SurfaceInput in)
    {
        SurfaceInput input = in;
        input.color = float4(1, 1, 1, 1); // the color carries the wind, not a tint
        return evaluate(p.base, input);
    }

    public override static Displaced displace(ProceduralTreeParameters p, MaterialRef m, DisplacePoint in)
    {
        Displaced d = Displaced.of(in);
        if (!in.hookOn(RUN_HOOK_PROCEDURAL_WIND_INDEX))
        {
            return d;
        }
        ProceduralWindParameters* wind = in.hook<ProceduralWindParameters>(RUN_HOOK_PROCEDURAL_WIND_INDEX);
        float speed = wind[0].speed;
        if (!(speed > 0))
        {
            return d;
        }
        float time = wind[0].time;
        // The format
        float trunk = in.color.r;
        float branch = in.color.g;
        uint packed = uint(round(saturate(in.color.b) * 255));
        float leaf = float(packed >> 4) / 15;
        float phase = float(packed & 15u) / 16;
        float pivot = in.uv1.x;
        float reach = max(in.uv1.y, 1e-3);
        // The wind across the mesh, and this tree's own phase and gust
        float2 level = wind[0].direction.xz;
        level = dot(level, level) > 1e-12 ? normalize(level) : float2(1, 0);
        float3 along = normalize(in.toMesh(float3(level.x, 0, level.y)));
        float3 across = normalize(in.toMesh(float3(-level.y, 0, level.x)));
        float own = TAU * float(previewHash(in.instance * 0x9E3779B1u + 7u) & 0xFFFFu) / 65536;
        float3 origin = in.origin + in.originLow;
        float front = dot(origin.xz, level) / max(wind[0].gustScale, 1e-3) - 0.35 * speed * time / max(wind[0].gustScale, 1e-3) * 4;
        float gust = 0.65 + 0.35 * sin(TAU * front) * sin(0.37 * TAU * front + 1.3);
        float3 position = in.position;
        float3 move = float3(0, 0, 0);
        // Branches: turned about (0, pivot, 0) toward the wind (and a little across it), swinging, length kept
        float swing = 0.55 + 0.45 * sin(TAU * p.branchFrequency * sqrt(4 / reach) * time + own + TAU * phase);
        float angle = p.branchSway * speed * gust * branch * swing * (4 / (4 + reach));
        float3 arm = position - float3(0, pivot, 0);
        float3 axis = cross(float3(0, 1, 0), along + 0.3 * across * sin(TAU * phase + time));
        axis = dot(axis, axis) > 1e-12 ? normalize(axis) : float3(0, 0, 1);
        float3 turned = arm * cos(angle) + cross(axis, arm) * sin(angle) + axis * (dot(axis, arm) * (1 - cos(angle)));
        move += turned - arm;
        // The trunk: a lean about the foot by its weight, swaying, kept to its length
        float lean = p.trunkBend * speed * speed * gust * (1 + 0.3 * sin(TAU * p.trunkFrequency * time + own));
        lean = p.maxLean * tanh(lean / max(p.maxLean, 1e-4));
        float bend = lean * trunk;
        float y = max(position.y, 0.0);
        move += along * (sin(bend) * y) + float3(0, (cos(bend) - 1) * y, 0);
        // Leaves: flutter along their normals
        float flutter = sin(TAU * p.leafFrequency * time + TAU * 7.3 * phase + own + 3.1 * dot(position, float3(1, 1, 1)));
        move += in.normal * (p.leafFlutter * speed * leaf * flutter);
        d.position = position + move;
        float slope = bend;
        d.normal = normalize(in.normal - along * (slope * in.normal.y) + float3(0, 1, 0) * (slope * dot(in.normal, along)));
        return d;
    }
};
