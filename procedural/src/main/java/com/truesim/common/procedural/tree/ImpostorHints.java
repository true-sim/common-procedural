package com.truesim.common.procedural.tree;

import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Octahedral;
import com.truesim.common.math.geometry.Sphere;
import com.truesim.common.procedural.flora.Albedo;

/**
 * How to capture a tree as an octahedral impostor (Ryan Brucks' octahedral impostors): {@code frames x frames} views
 * on an upper-hemisphere octahedral grid ({@link Octahedral#decodeHemisphere}; trees are not seen from below), each an
 * orthographic view of the bounding sphere toward its center, {@code resolution} pixels square, stored at cell (i, j)
 * of the atlas. At run time the view direction's {@link Octahedral#encodeHemisphere} cell, and its neighbours, pick
 * and blend the frames.
 *
 * <p>Flowers and fruits are captured with the tree (they are in its levels); {@code bloom} and {@code bloomCoverage}
 * tell a far representation captured on another day (or none) what to tint toward: a tree in blossom white, a rowan
 * in fruit red.
 *
 * @param bounds the sphere every frame covers: center and radius in mesh space
 * @param frames views per side of the grid
 * @param resolution pixels per side of each view
 * @param bloom the flowers' and fruits' mean albedo on the tree's day (black for none)
 * @param bloomCoverage the share of the tree's side view they cover, 0 to 1
 */
public record ImpostorHints(Sphere bounds, int frames, int resolution, Albedo bloom, double bloomCoverage) {

    /** Hints for a tree without flowers or fruits. */
    public ImpostorHints(Sphere bounds, int frames, int resolution) {
        this(bounds, frames, resolution, new Albedo(0, 0, 0), 0);
    }

    /** The direction toward the camera of view (i, j), unit length, y at least 0. */
    public Vec3 direction(int i, int j) {
        return Octahedral.decodeHemisphere((i + 0.5) / frames, (j + 0.5) / frames);
    }

    /** Where the camera of view (i, j) stands: twice the radius out from the center. */
    public Vec3 cameraPosition(int i, int j) {
        return bounds.center().addScaled(direction(i, j), 2 * bounds.radius());
    }

    /** The atlas's pixels per side. */
    public int atlasSize() {
        return frames * resolution;
    }
}
