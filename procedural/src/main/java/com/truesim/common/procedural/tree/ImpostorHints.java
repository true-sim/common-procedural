package com.truesim.common.procedural.tree;

import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Octahedral;
import com.truesim.common.math.geometry.Sphere;

/**
 * How to capture a tree as an octahedral impostor (Ryan Brucks' octahedral impostors): {@code frames x frames} views
 * on an upper-hemisphere octahedral grid ({@link Octahedral#decodeHemisphere}; trees are not seen from below), each an
 * orthographic view of the bounding sphere toward its center, {@code resolution} pixels square, stored at cell (i, j)
 * of the atlas. At run time the view direction's {@link Octahedral#encodeHemisphere} cell, and its neighbours, pick
 * and blend the frames.
 *
 * @param bounds the sphere every frame covers: center and radius in mesh space
 * @param frames views per side of the grid
 * @param resolution pixels per side of each view
 */
public record ImpostorHints(Sphere bounds, int frames, int resolution) {

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
