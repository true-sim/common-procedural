package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.scene.assets.AssetPath;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.render.ModelData;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A recipe file followed for hot reload ({@link ProceduralAssets#live}): its current recipe and the generated
 * references made from it. When {@link #poll()} finds the file's text changed, the recipe is read again and listeners
 * hear of it; the new recipe's references are new assets (equal recipes are one asset, so text that changes back
 * finds its old asset if still cached). A game acquires the new model, swaps it in once loaded, and releases the old:
 *
 * <pre>{@code
 * LiveRecipe spruce = procedural.live("game://forest/spruce-a.tree");
 * spruce.listen((recipe, before, after) -> pending.add(recipe.model()));   // acquire, swap when loaded, release
 * procedural.poll();                                                         // e.g. once a second, or on a file event
 * }</pre>
 *
 * A text that does not parse keeps the recipe as it was; the error goes to the listeners' {@link Listener#failed}.
 */
public final class LiveRecipe {

    /** Hears a live recipe change. */
    public interface Listener {
        /** The recipe of {@code live} changed from {@code before} to {@code after}. */
        void changed(LiveRecipe live, Recipe before, Recipe after);

        /** The file changed but could not be read as a recipe: the old recipe stays. */
        default void failed(LiveRecipe live, Exception error) {
        }
    }

    private final ProceduralAssets assets;
    private final AssetPath path;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private String text;
    private Recipe recipe;

    LiveRecipe(ProceduralAssets assets, AssetPath path) throws IOException {
        this.assets = assets;
        this.path = path;
        this.text = assets.read(path);
        this.recipe = Recipe.parse(text);
    }

    public AssetPath path() {
        return path;
    }

    /** The recipe as last read. */
    public Recipe recipe() {
        return recipe;
    }

    /** The generated model of the current recipe. */
    public AssetRef<ModelData> model() {
        return assets.model(recipe);
    }

    /** The generated mesh {@code index} of the current recipe. */
    public AssetRef<MeshData> mesh(int index) {
        return assets.mesh(recipe, index);
    }

    @AllowAllocation("setting up")
    public void listen(Listener listener) {
        listeners.add(listener);
    }

    /** Reads the file again; returns whether the recipe changed (and listeners were told). */
    @AllowAllocation("on change")
    public boolean poll() throws IOException {
        String now = assets.read(path);
        if (now.equals(text)) {
            return false;
        }
        text = now;
        Recipe next;
        try {
            next = Recipe.parse(now);
        } catch (RuntimeException e) {
            for (Listener listener : listeners) {
                listener.failed(this, e);
            }
            return false;
        }
        if (next.equals(recipe)) {
            return false; // comments or spacing only
        }
        Recipe before = recipe;
        recipe = next;
        for (Listener listener : listeners) {
            listener.changed(this, before, next);
        }
        return true;
    }
}
