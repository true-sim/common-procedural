package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.layout.ElementBuffer;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generated meshes kept on disk, keyed by what they depend on: the generator's id and version and the recipe's
 * {@link Recipe#generationKey() generation key} (SHA-256 of them names the file). Generation is deterministic, so a
 * file is valid for as long as its key: a recipe or generator version that changes makes a new key, so stale output
 * is never read, and regeneration happens only on a change. Thread-safe: files are written whole under a temporary
 * name and moved into place, so readers never see half a file; two threads making the same key write the same bytes.
 */
public final class GenerationCache {

    private static final int MAGIC = 0x54535047; // "TSPG"
    private static final int FORMAT = 1;

    private final Path directory;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger generations = new AtomicInteger();

    /** A cache in {@code directory} (made when first written), or none at all with {@code null}: always generate. */
    public GenerationCache(Path directory) {
        this.directory = directory;
    }

    /** The meshes of {@code recipe} by {@code generator}: read from the cache, else generated and written to it. */
    @AllowAllocation("at load time")
    public List<ProceduralMesh> get(ProceduralGenerator generator, Recipe recipe) throws Exception {
        return get(generator.id(), generator.version(), recipe.generationKey(), () -> generator.generate(recipe));
    }

    /**
     * The meshes generator {@code id} of {@code version} makes of what {@code generationKey} describes: read from the
     * cache, else made by {@code generate} and written to it. For generators with recipes of their own (fences).
     */
    @AllowAllocation("at load time")
    public List<ProceduralMesh> get(String id, int version, String generationKey,
        Callable<List<ProceduralMesh>> generate) throws Exception {
        if (directory == null) {
            generations.incrementAndGet();
            return generate.call();
        }
        Path file = directory.resolve(key(id, version, generationKey) + ".bin");
        try {
            List<ProceduralMesh> cached = read(file);
            hits.incrementAndGet();
            return cached;
        } catch (NoSuchFileException missing) {
            List<ProceduralMesh> meshes = generate.call();
            generations.incrementAndGet();
            write(file, meshes);
            return meshes;
        }
    }

    /** How many requests the cache answered from disk. */
    public int hits() {
        return hits.get();
    }

    /** How many requests generated their meshes. */
    public int generations() {
        return generations.get();
    }

    /** The file name of what {@code generator} makes of {@code recipe}: SHA-256 of the id, version and key. */
    @AllowAllocation("at load time")
    static String key(ProceduralGenerator generator, Recipe recipe) {
        return key(generator.id(), generator.version(), recipe.generationKey());
    }

    @AllowAllocation("at load time")
    static String key(String id, int version, String generationKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String text = id + "\nversion=" + version + "\n" + generationKey;
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JDK has SHA-256", e);
        }
    }

    @AllowAllocation("at load time")
    private void write(Path file, List<ProceduralMesh> meshes) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "generating", ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeInt(meshes.size());
            for (ProceduralMesh mesh : meshes) {
                out.writeInt(mesh.vertexCount());
                out.writeInt(mesh.indices().length);
                out.writeInt(mesh.submeshes().length);
                for (int offset : mesh.submeshes()) {
                    out.writeInt(offset);
                }
                out.writeDouble(mesh.error());
                Aabb box = mesh.bounds();
                for (double v : new double[] {box.min().x(), box.min().y(), box.min().z(), box.max().x(),
                    box.max().y(), box.max().z()}) {
                    out.writeDouble(v);
                }
                out.write(mesh.vertices().segment().toArray(ValueLayout.JAVA_BYTE));
                for (int index : mesh.indices()) {
                    out.writeInt(index);
                }
            }
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @AllowAllocation("at load time")
    private static List<ProceduralMesh> read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT) {
                throw new IOException("Not a generation cache file of this format: " + file);
            }
            int count = in.readInt();
            List<ProceduralMesh> meshes = new ArrayList<>(count);
            for (int m = 0; m < count; m++) {
                int vertices = in.readInt();
                int indexCount = in.readInt();
                int[] submeshes = new int[in.readInt()];
                for (int s = 0; s < submeshes.length; s++) {
                    submeshes[s] = in.readInt();
                }
                double error = in.readDouble();
                Aabb box = new Aabb(new Vec3(in.readDouble(), in.readDouble(), in.readDouble()),
                    new Vec3(in.readDouble(), in.readDouble(), in.readDouble()));
                byte[] bytes = new byte[Math.toIntExact(vertices * ProceduralVertex.LAYOUT.stride())];
                in.readFully(bytes);
                ElementBuffer buffer = ElementBuffer.onHeap(ProceduralVertex.LAYOUT, vertices);
                buffer.segment().copyFrom(MemorySegment.ofArray(bytes));
                int[] indices = new int[indexCount];
                for (int i = 0; i < indexCount; i++) {
                    indices[i] = in.readInt();
                }
                meshes.add(new ProceduralMesh(buffer, indices, submeshes, box, error));
            }
            return meshes;
        }
    }
}
