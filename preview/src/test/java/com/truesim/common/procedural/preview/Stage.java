package com.truesim.common.procedural.preview;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16B16A16_SFLOAT;

import com.truesim.common.math.color.Color;
import com.truesim.common.procedural.assets.SceneMeshes;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.preview.materials.ProceduralTreeParameters;
import com.truesim.common.procedural.preview.materials.ProceduralWindParameters;
import com.truesim.engine.graph.FrameGraph;
import com.truesim.engine.graph.Image;
import com.truesim.engine.graph.Key;
import com.truesim.engine.graph.ViewPort;
import com.truesim.engine.graph.testing.TestTarget;
import com.truesim.engine.render.Camera;
import com.truesim.engine.render.ClusteredLights;
import com.truesim.engine.render.DisplayTransform;
import com.truesim.engine.render.Environment;
import com.truesim.engine.render.EnvironmentLighting;
import com.truesim.engine.render.GeometryPool;
import com.truesim.engine.render.GpuScene;
import com.truesim.engine.render.Keys;
import com.truesim.engine.render.Lights;
import com.truesim.engine.render.Material;
import com.truesim.engine.render.Materials;
import com.truesim.engine.render.MeshletRenderer;
import com.truesim.engine.render.SceneGeometry;
import com.truesim.engine.render.SceneView;
import com.truesim.engine.render.SkyBackground;
import com.truesim.engine.render.TextureFormat;
import com.truesim.engine.render.Textures;
import com.truesim.engine.render.ViewOptions;
import com.truesim.engine.render.VisibilityBufferShading;
import com.truesim.engine.render.materials.MetallicRoughnessParameters;
import com.truesim.engine.render.shadows.Shadows;
import com.truesim.engine.vulkan.DisplayEncoding;
import com.truesim.engine.vulkan.Format;
import com.truesim.engine.vulkan.VulkanDevice;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.assets.mesh.VertexAttribute;
import com.truesim.scene.assets.mesh.VertexFormat;
import com.truesim.scene.core.ShadowSpec;
import com.truesim.scene.meshbuild.MeshBuilder;
import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * A stage for pictures and films of generated assets, drawn by the engine's renderer straight (a test's fixture, as
 * effects-vegetation's meadow is): level ground, the sun with cascaded shadows in a sky, generated meshes with the
 * preview's tree material (swaying by the wind format) or plain metallic-roughness, shaded by the visibility buffer
 * into half floats and shown through the display transform in an sRGB target.
 */
final class Stage implements AutoCloseable {

    private static final Key<Image> SHOWN = Key.image("test.procedural.shown");

    final VulkanDevice device;
    final int width;
    final int height;
    final GeometryPool pool;
    final GpuScene scene;
    final MeshletRenderer renderer;
    final Materials materials;
    final Textures textures;
    final Lights lights;
    final FrameGraph graph;
    final TestTarget target;
    final Camera camera = new Camera();
    private float[] sun = normalize(-0.45f, 0.6f, -0.65f);
    private int nextId;
    private final boolean large;
    private SceneView view;
    private Shadows shadows;
    private double time;
    private ProceduralWindParameters wind = ProceduralWindParameters.DEFAULTS;

    Stage(VulkanDevice device, int width, int height, double groundSize) {
        this(device, width, height, groundSize, GeometryPool.Capacity.of(1 << 22, 1 << 18, 64), 1 << 12);
    }

    /** A stage with room for {@code capacity} in its geometry pool and {@code instances} instances to start with. */
    Stage(VulkanDevice device, int width, int height, double groundSize, GeometryPool.Capacity capacity,
        int instances) {
        this.device = device;
        this.width = width;
        this.height = height;
        pool = new GeometryPool(device, capacity);
        large = instances >= 1 << 19;
        scene = new GpuScene(device, pool, instances);
        // a valley of plants: chunks of 64 meshlets for every instance in every view from the first frame
        renderer = instances >= 1 << 19 ? new MeshletRenderer(device, scene, VK_FORMAT_R16G16B16A16_SFLOAT,
            VK_FORMAT_D32_SFLOAT, scene.frameSlots(), 1 << 22) : new MeshletRenderer(device, scene,
            VK_FORMAT_R16G16B16A16_SFLOAT, VK_FORMAT_D32_SFLOAT);
        materials = new Materials(device, 128);
        materials.register(com.truesim.common.procedural.preview.materials.ProceduralTree.TYPE);
        textures = new Textures(device);
        lights = new Lights(device, 4, 4);
        graph = new FrameGraph(device, 2);
        target = new TestTarget(device, width, height, Format.R8G8B8A8_SRGB);
        scene.setMaterials(materials);
        if (groundSize > 0) {
            int ground = pool.add(ground(groundSize, 64));
            int soil = materials.add(new Material(MetallicRoughnessParameters.DEFAULTS
                .withBaseColor(new Color(0.06f, 0.08f, 0.03f, 1)).withRoughness(0.95f).withMetallic(0)));
            scene.set(nextId++, ground, soil, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1);
        }
        lights.directional(0, -sun[0], -sun[1], -sun[2], 4.2f, 3.9f, 3.4f);
        lights.shadow(0, ShadowSpec.cascades(4, 2048, 150));
        lights.setCount(1);
    }

    /** The sun from direction ({@code x}, {@code y}, {@code z}) (toward it), its shadows {@code shadow}: before shade. */
    Stage sun(float x, float y, float z, ShadowSpec shadow) {
        sun = normalize(x, y, z);
        lights.directional(0, -sun[0], -sun[1], -sun[2], 4.2f, 3.9f, 3.4f);
        lights.shadow(0, shadow);
        return this;
    }

    /** Any scene mesh in the geometry pool. */
    int addMesh(MeshData mesh) {
        return pool.add(mesh);
    }

    /** A plain material tinted by the vertex colour (white base), {@code roughness}. */
    int vertexColorMaterial(float roughness) {
        return materials.add(new Material(MetallicRoughnessParameters.DEFAULTS.withRoughness(roughness)
            .withMetallic(0)));
    }

    /** A new instance id. */
    int id() {
        return nextId++;
    }

    /** Sets instance {@code id}: {@code mesh} at (x, y, z), turned by {@code yaw}, scaled (sx, sy, sz). */
    void set(int id, int mesh, int material, double x, double y, double z, double yaw, double sx, double sy,
        double sz) {
        set(id, mesh, material, true, x, y, z, yaw, sx, sy, sz);
    }

    /** {@link #set}, casting shadows or not. */
    void set(int id, int mesh, int material, boolean shadows, double x, double y, double z, double yaw, double sx,
        double sy, double sz) {
        scene.set(id, mesh, material, 1, shadows, x, y, z, 0, (float) Math.sin(yaw / 2), 0,
            (float) Math.cos(yaw / 2), (float) sx, (float) sy, (float) sz);
    }

    /** {@code mesh} in the geometry pool, with its wind data. */
    int add(ProceduralMesh mesh) {
        return pool.add(SceneMeshes.build(mesh));
    }

    /** {@code mesh} in the geometry pool without wind data (rocks: plain materials tint by the vertex color). */
    int addStill(ProceduralMesh mesh) {
        return pool.add(SceneMeshes.build(mesh, false));
    }

    /** A texture of sRGB colors with alpha. */
    int texture(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        MemorySegment texels = Arena.ofAuto().allocate(4L * w * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = image.getRGB(x, y);
                long at = 4L * (y * (long) w + x);
                texels.set(ValueLayout.JAVA_BYTE, at, (byte) (p >> 16));
                texels.set(ValueLayout.JAVA_BYTE, at + 1, (byte) (p >> 8));
                texels.set(ValueLayout.JAVA_BYTE, at + 2, (byte) p);
                texels.set(ValueLayout.JAVA_BYTE, at + 3, (byte) (p >>> 24));
            }
        }
        return textures.add(TextureFormat.RGBA8_SRGB, w, h, Textures.GENERATE, texels);
    }

    /**
     * A tree material: textured, masked by the texture's alpha when {@code masked}, two-sided leaves, swaying within
     * {@code reach} metres.
     */
    int treeMaterial(int texture, boolean masked, float roughness, float reach) {
        ProceduralTreeParameters p = ProceduralTreeParameters.DEFAULTS.withBranchSway(0.02f)
            .withBase(MetallicRoughnessParameters.DEFAULTS.withBaseColorTexture(texture).withRoughness(roughness)
                .withMetallic(0));
        Material material = new Material(p).displacement(reach);
        if (masked) {
            material = material.doubleSided(true).alphaMask(0.5f);
        }
        return materials.add(material);
    }

    /** A plain material, no wind: rocks. */
    int plainMaterial(int texture, float roughness) {
        return materials.add(new Material(MetallicRoughnessParameters.DEFAULTS.withBaseColorTexture(texture)
            .withRoughness(roughness).withMetallic(0)));
    }

    /** Places {@code mesh} (its submeshes using {@code material}, {@code material + 1}, ...); returns its id. */
    int place(int mesh, int material, double x, double z, double yaw, double scale) {
        int id = nextId++;
        move(id, mesh, material, x, z, yaw, scale);
        return id;
    }

    void move(int id, int mesh, int material, double x, double z, double yaw, double scale) {
        scene.set(id, mesh, material, x, 0, z, 0, (float) Math.sin(yaw / 2), 0, (float) Math.cos(yaw / 2),
            (float) scale, (float) scale, (float) scale);
    }

    /** Shades by the visibility buffer. */
    Stage shade() {
        int sky = textures.add(TextureFormat.RGBA16F, 1024, 512, Textures.GENERATE, sky(1024, 512, sun));
        Environment environment = Environment.panorama("stage sky", sky, 1024);
        SceneGeometry geometry = SceneGeometry.meshlets(renderer);
        ViewPort port = graph.addView("main").size(width, height);
        // a valley's forest: room for many meshlets, masked leaves among them, and occlusion culling
        view = geometry.createView(port, camera, large ? ViewOptions.of(1 << 22, 1 << 24).withBins(1 << 21, 1 << 23) : ViewOptions.of(1 << 21, 1 << 22));
        view.set(1, MeshletRenderer.MODE_LIT);
        shadows = new Shadows(geometry, lights);
        graph.settings().set(DisplayTransform.CURVE, "agx");
        port.add(new VisibilityBufferShading(geometry, view, VK_FORMAT_R16G16B16A16_SFLOAT, VK_FORMAT_D32_SFLOAT))
            .add(new EnvironmentLighting(environment, view)).add(new SkyBackground(environment, view))
            .add(new ClusteredLights(lights, view)).add(shadows.view(view))
            .add(new DisplayTransform().transform(Keys.SCENE_COLOR, SHOWN, DisplayEncoding.SDR_SRGB))
            .output(SHOWN, target.output());
        return this;
    }

    /** The wind from now on (its time is the stage's). */
    Stage wind(ProceduralWindParameters parameters) {
        wind = parameters;
        return this;
    }

    /** The camera at (x, y, z) looking at (tx, ty, tz), {@code fov} degrees high. */
    Stage look(double x, double y, double z, double tx, double ty, double tz, double fov) {
        double dx = tx - x;
        double dy = ty - y;
        double dz = tz - z;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        camera.position(x, y, z).set(dx / l, dy / l, dz / l, 0, 1, 0, Math.toRadians(fov), width, height, 0.05);
        return this;
    }

    /** {@code count} frames, {@code seconds} apart, the last read back. */
    void frames(int count, double seconds) {
        for (int i = 0; i < count; i++) {
            time += seconds;
            scene.geometryHooks().set("procedural.wind", wind.withTime((float) time));
            view.set(1, MeshletRenderer.MODE_LIT);
            target.frame(graph);
        }
    }

    BufferedImage shot() {
        return target.shot();
    }

    private static MeshData ground(double size, int n) {
        int count = (n + 1) * (n + 1);
        float[] positions = new float[count * 3];
        float[] normals = new float[count * 3];
        for (int z = 0; z <= n; z++) {
            for (int x = 0; x <= n; x++) {
                int v = z * (n + 1) + x;
                positions[3 * v] = (float) ((x / (double) n - 0.5) * size);
                positions[3 * v + 2] = (float) ((z / (double) n - 0.5) * size);
                normals[3 * v + 1] = 1;
            }
        }
        int[] indices = new int[n * n * 6];
        int at = 0;
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int a = z * (n + 1) + x;
                int[] quad = {a, a + n + 1, a + 1, a + 1, a + n + 1, a + n + 2};
                System.arraycopy(quad, 0, indices, at, 6);
                at += 6;
            }
        }
        return new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL), count).positions(positions)
            .attribute(VertexAttribute.NORMAL, normals).submesh(indices).buildFlat();
    }

    private static float[] normalize(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / l, y / l, z / l};
    }

    /** A clear day's panorama: a blue zenith to a pale horizon, a dim ground, the sun's disc and glow. */
    private static MemorySegment sky(int width, int height, float[] sun) {
        MemorySegment texels = Arena.ofAuto().allocate(8L * width * height);
        ValueLayout.OfShort half = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
        for (int y = 0; y < height; y++) {
            double theta = Math.PI * (y + 0.5) / height;
            for (int x = 0; x < width; x++) {
                double phi = 2 * Math.PI * ((x + 0.5) / width - 0.5);
                double dx = Math.sin(theta) * Math.cos(phi);
                double dy = Math.cos(theta);
                double dz = Math.sin(theta) * Math.sin(phi);
                double up = Math.max(dy, 0);
                double horizon = Math.pow(1 - up, 3);
                double[] c = dy >= 0
                    ? new double[] {0.11 + 0.4 * horizon, 0.2 + 0.38 * horizon, 0.5 + 0.22 * horizon}
                    : new double[] {0.09, 0.1, 0.07};
                double cos = dx * sun[0] + dy * sun[1] + dz * sun[2];
                double angle = Math.acos(Math.min(1, cos));
                double glow = 1.5 * Math.exp(-angle * angle / (2 * 0.08 * 0.08));
                double disc = angle < Math.toRadians(0.6) ? 2000 : 0;
                long at = 8L * (y * (long) width + x);
                for (int k = 0; k < 3; k++) {
                    texels.set(half, at + 2L * k, Float.floatToFloat16((float) (c[k] + glow + disc)));
                }
                texels.set(half, at + 6, Float.floatToFloat16(1));
            }
        }
        return texels;
    }

    @Override
    public void close() {
        device.waitIdle();
        graph.close();
        if (shadows != null) {
            shadows.close();
        }
        target.close();
        lights.close();
        textures.close();
        scene.setMaterials(null);
        materials.close();
        renderer.close();
        scene.close();
        pool.close();
    }
}
