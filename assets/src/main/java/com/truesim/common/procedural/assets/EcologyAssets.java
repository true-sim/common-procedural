package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.ecology.EcologyRecipe;
import com.truesim.scene.assets.AssetInput;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.assets.AssetType;
import com.truesim.scene.assets.PathAssets;
import com.truesim.scene.render.ModelData;
import com.truesim.scene.render.RenderAssets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ecology recipes ({@code .ecology} files, {@link EcologyRecipe}) through the scene's asset system: a recipe loads as
 * an asset like any other, and gives the ecology over a terrain and the model each species' instances draw.
 *
 * <pre>{@code
 * EcologyAssets.install(paths);
 * AssetRef<EcologyRecipe> ref = paths.ref(EcologyAssets.ECOLOGY, "game://ecology/lauterbrunnen.ecology");
 * cache.acquire(ref);                                         // read in the background, like every asset
 * Ecology ecology = cache.get(ref).ecology(terrain);           // the same as the direct API's
 * Map<String, AssetRef<ModelData>> models = EcologyAssets.models(paths, cache.get(ref));
 * }</pre>
 */
public final class EcologyAssets {

    /** Ecology recipes, read from {@code .ecology} files. */
    public static final AssetType<EcologyRecipe> ECOLOGY = AssetType.of("procedural-ecology", EcologyRecipe.class,
        recipe -> 4096);
    /** The file extension of ecology recipes. */
    public static final String EXTENSION = "ecology";

    private EcologyAssets() {
    }

    /** Registers the importer of {@code .ecology} files. */
    @AllowAllocation("setting up")
    public static void install(PathAssets paths) {
        paths.importers().add(ECOLOGY, EXTENSION, EcologyAssets::read);
    }

    /** The model of each species of {@code recipe} that names one, by species id. */
    @AllowAllocation("at load time")
    public static Map<String, AssetRef<ModelData>> models(PathAssets paths, EcologyRecipe recipe) {
        Map<String, AssetRef<ModelData>> models = new LinkedHashMap<>();
        for (Map.Entry<String, String> model : recipe.models().entrySet()) {
            models.put(model.getKey(), paths.ref(RenderAssets.MODEL, model.getValue()));
        }
        return models;
    }

    @AllowAllocation("at load time")
    private static EcologyRecipe read(AssetInput input) throws IOException {
        try (InputStream in = input.open()) {
            return EcologyRecipe.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
