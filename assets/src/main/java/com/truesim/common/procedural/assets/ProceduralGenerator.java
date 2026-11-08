package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.rock.RockGenerator;
import com.truesim.common.procedural.rock.RockParams;
import com.truesim.common.procedural.rock.Rocks;
import com.truesim.common.procedural.tree.Tree;
import com.truesim.common.procedural.tree.TreeGenerator;
import com.truesim.common.procedural.tree.TreeOptions;
import com.truesim.common.procedural.tree.TreeSpecies;
import com.truesim.common.procedural.tree.Trees;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns recipes of one generator id into meshes: its levels of detail, most detailed first, then any extra meshes
 * (a tree's billboard last). Its version goes into the generation cache's key, so output from an older version is
 * never reused. Must be thread-safe: assets generate on the asset lane's threads.
 */
public interface ProceduralGenerator {

    /** The id recipes name in {@code generator}. */
    String id();

    /** Changes whenever the same recipe would give different meshes. */
    int version();

    /** The meshes of {@code recipe}. */
    List<ProceduralMesh> generate(Recipe recipe) throws Exception;

    /** Trees ({@link Trees}): the levels of {@link TreeOptions#DEFAULTS}, then the billboard. */
    ProceduralGenerator TREES = new ProceduralGenerator() {
        @Override
        public String id() {
            return "tree";
        }

        @Override
        public int version() {
            return TreeGenerator.VERSION;
        }

        @Override
        @AllowAllocation("at load time")
        public List<ProceduralMesh> generate(Recipe recipe) {
            TreeSpecies species = null;
            for (TreeSpecies preset : TreeSpecies.PRESETS) {
                if (preset.name().equals(recipe.preset())) {
                    species = preset;
                }
            }
            if (species == null) {
                throw new IllegalArgumentException("No tree species '" + recipe.preset() + "'");
            }
            TreeOptions options = TreeOptions.DEFAULTS.withSeason(recipe.season()).withSnowLoad(recipe.snow());
            Tree tree = Trees.generate(species, options, recipe.seed());
            List<ProceduralMesh> meshes = new ArrayList<>(tree.lods());
            meshes.add(tree.billboard());
            return meshes;
        }
    };

    /** Rocks ({@link Rocks}): their levels of detail. */
    ProceduralGenerator ROCKS = new ProceduralGenerator() {
        @Override
        public String id() {
            return "rock";
        }

        @Override
        public int version() {
            return RockGenerator.VERSION;
        }

        @Override
        @AllowAllocation("at load time")
        public List<ProceduralMesh> generate(Recipe recipe) {
            for (RockParams preset : RockParams.PRESETS) {
                if (preset.name().equals(recipe.preset())) {
                    return Rocks.generate(preset, recipe.seed()).lods();
                }
            }
            throw new IllegalArgumentException("No rock preset '" + recipe.preset() + "'");
        }
    };
}
