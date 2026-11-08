package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.plant.Plant;
import com.truesim.common.procedural.plant.PlantGenerator;
import com.truesim.common.procedural.plant.PlantOptions;
import com.truesim.common.procedural.plant.PlantSpecies;
import com.truesim.common.procedural.plant.Plants;
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

    /** Whether its meshes sway: carry the wind data into the scene mesh ({@link SceneMeshes#FORMAT}). */
    default boolean sways() {
        return true;
    }

    /**
     * The colors of {@code recipe}'s flowers, fruits and small parts (the {@code #palette} texture), or {@code null}
     * for a generator without.
     */
    default Palette palette(Recipe recipe) {
        return null;
    }

    /** The tree species preset named {@code name}. */
    static TreeSpecies treeSpecies(String name) {
        for (TreeSpecies preset : TreeSpecies.PRESETS) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("No tree species '" + name + "'");
    }

    /** The plant species preset named {@code name}. */
    static PlantSpecies plantSpecies(String name) {
        for (PlantSpecies preset : PlantSpecies.PRESETS) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("No plant species '" + name + "'");
    }

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
            TreeSpecies species = treeSpecies(recipe.preset());
            TreeOptions options = TreeOptions.DEFAULTS.withSeason(recipe.season()).withSnowLoad(recipe.snow());
            if (!Double.isNaN(recipe.day())) {
                options = options.withDay(recipe.day());
            }
            Tree tree = Trees.generate(species, options, recipe.seed());
            List<ProceduralMesh> meshes = new ArrayList<>(tree.lods());
            meshes.add(tree.billboard());
            return meshes;
        }

        @Override
        public Palette palette(Recipe recipe) {
            TreeSpecies species = treeSpecies(recipe.preset());
            return species.fruiting() == null ? Palette.DEFAULT : species.fruiting().palette();
        }
    };

    /** Small plants and clumps ({@link Plants}): their levels of detail, then the billboard. */
    ProceduralGenerator PLANTS = new ProceduralGenerator() {
        @Override
        public String id() {
            return "plant";
        }

        @Override
        public int version() {
            return PlantGenerator.VERSION;
        }

        @Override
        @AllowAllocation("at load time")
        public List<ProceduralMesh> generate(Recipe recipe) {
            PlantSpecies species = plantSpecies(recipe.preset());
            PlantOptions options = PlantOptions.DEFAULTS.withDay(recipe.dayOfYear()).withSnowLoad(recipe.snow());
            Plant plant = Plants.generate(species, options, recipe.seed());
            List<ProceduralMesh> meshes = new ArrayList<>(plant.lods());
            meshes.add(plant.billboard());
            return meshes;
        }

        @Override
        public Palette palette(Recipe recipe) {
            return plantSpecies(recipe.preset()).palette();
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
        public boolean sways() {
            return false;
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
