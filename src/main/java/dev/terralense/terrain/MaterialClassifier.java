package dev.terralense.terrain;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;

/** Maps a {@link Material} to a {@link TerrainCategory}. */
public final class MaterialClassifier {
    private final Map<Material, TerrainCategory> cache = new EnumMap<>(Material.class);

    public MaterialClassifier() {
        for (Material material : Material.values()) {
            // Legacy constants are duplicates of modern ones and never appear in a modern chunk
            // snapshot; skip them so the map stays meaningful.
            if (material.isLegacy()) {
                continue;
            }
            cache.put(material, classifyUncached(material));
        }
    }

    /** Returns the terrain category for a material. */
    public TerrainCategory classify(Material material) {
        if (material == null) {
            return TerrainCategory.UNKNOWN;
        }
        TerrainCategory cached = cache.get(material);
        return cached != null ? cached : TerrainCategory.UNKNOWN;
    }

    private static TerrainCategory classifyUncached(Material material) {
        if (material.isAir()) {
            return TerrainCategory.UNKNOWN;
        }
        String name = material.name();

        // Liquids first: they must never be shaded as solid terrain.
        if (name.equals("WATER") || name.equals("BUBBLE_COLUMN")) {
            return TerrainCategory.WATER;
        }
        if (name.equals("LAVA")) {
            return TerrainCategory.LAVA;
        }

        // Nether and End are checked before generic stone/brick rules because several of their
        // blocks end in _BRICKS or contain STONE.
        if (name.startsWith("NETHER") || name.contains("NETHERRACK") || name.contains("BLACKSTONE")
                || name.contains("BASALT") || name.contains("SOUL_") || name.equals("SOUL_SAND")
                || name.equals("MAGMA_BLOCK") || name.contains("QUARTZ")
                || name.contains("CRIMSON") || name.contains("WARPED")
                || name.contains("SHROOMLIGHT") || name.contains("WEEPING_VINES")
                || name.contains("TWISTING_VINES") || name.equals("GLOWSTONE")
                || name.equals("ANCIENT_DEBRIS")) {
            return TerrainCategory.NETHER;
        }
        if (name.startsWith("END_") || name.equals("PURPUR_BLOCK") || name.startsWith("PURPUR")
                || name.equals("CHORUS_PLANT") || name.equals("CHORUS_FLOWER")
                || name.equals("OBSIDIAN") || name.equals("CRYING_OBSIDIAN")) {
            return TerrainCategory.END;
        }

        if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS")) {
            return TerrainCategory.ORE;
        }
        if (name.contains("DEEPSLATE") || name.equals("TUFF") || name.contains("REINFORCED_DEEPSLATE")) {
            return TerrainCategory.DEEPSLATE;
        }

        if (name.endsWith("_LEAVES")) {
            return TerrainCategory.LEAVES;
        }
        if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_PLANKS")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE") || name.contains("STRIPPED_")
                || name.endsWith("_FENCE") || name.endsWith("_SLAB") && name.contains("WOOD")) {
            return TerrainCategory.WOOD;
        }

        if (name.equals("GRASS_BLOCK") || name.equals("MOSS_BLOCK") || name.equals("MOSS_CARPET")
                || name.equals("MYCELIUM") || name.equals("PODZOL")) {
            return TerrainCategory.GRASS;
        }
        if (name.equals("DIRT_PATH")) {
            return TerrainCategory.PATH;
        }
        if (name.equals("FARMLAND")) {
            return TerrainCategory.FARMLAND;
        }
        if (name.equals("DIRT") || name.equals("COARSE_DIRT") || name.equals("ROOTED_DIRT")
                || name.equals("MUD") || name.contains("MUDDY_MANGROVE")) {
            return TerrainCategory.DIRT;
        }

        if (name.equals("SNOW") || name.equals("SNOW_BLOCK") || name.equals("POWDER_SNOW")) {
            return TerrainCategory.SNOW;
        }
        if (name.endsWith("ICE")) {
            return TerrainCategory.ICE;
        }

        if (name.equals("SAND") || name.equals("RED_SAND") || name.contains("SANDSTONE")) {
            return TerrainCategory.SAND;
        }
        if (name.equals("GRAVEL")) {
            return TerrainCategory.GRAVEL;
        }
        if (name.equals("CLAY") || name.endsWith("_TERRACOTTA") || name.equals("TERRACOTTA")) {
            return TerrainCategory.CLAY;
        }

        if (name.endsWith("_BLOCK") && (name.contains("IRON") || name.contains("GOLD")
                || name.contains("COPPER") || name.contains("NETHERITE") || name.contains("DIAMOND")
                || name.contains("EMERALD") || name.contains("LAPIS") || name.contains("REDSTONE"))) {
            return TerrainCategory.METAL;
        }

        // Player/structure builds: bricks, glass, concrete, wool and friends.
        if (name.contains("BRICK") || name.contains("GLASS") || name.contains("CONCRETE")
                || name.contains("WOOL") || name.contains("PRISMARINE") || name.contains("COPPER")
                || name.endsWith("_TILES") || name.contains("CHISELED") || name.contains("POLISHED")
                || name.contains("SHULKER_BOX") || name.equals("BOOKSHELF")) {
            return TerrainCategory.STRUCTURE;
        }

        if (name.contains("STONE") || name.equals("GRANITE") || name.equals("DIORITE")
                || name.equals("ANDESITE") || name.equals("CALCITE") || name.equals("DRIPSTONE_BLOCK")
                || name.equals("BEDROCK") || name.equals("AMETHYST_BLOCK")) {
            return TerrainCategory.STONE;
        }

        // Small plants read as foliage; they sit on top of terrain and are the most likely non-
        // solid block to be the highest one at a coordinate.
        if (name.contains("FLOWER") || name.contains("SAPLING") || name.contains("GRASS")
                || name.contains("FERN") || name.contains("VINE") || name.contains("CROP")
                || name.contains("BUSH") || name.contains("KELP") || name.contains("BAMBOO")
                || name.contains("MUSHROOM") || name.contains("CACTUS") || name.contains("SUGAR_CANE")
                || name.contains("LILY") || name.contains("WHEAT") || name.contains("CARROT")
                || name.contains("POTATO") || name.contains("BEETROOT") || name.contains("MELON")
                || name.contains("PUMPKIN") || name.contains("SEAGRASS") || name.contains("CORAL")
                || name.contains("AZALEA") || name.contains("SPORE_BLOSSOM")
                || name.contains("PINK_PETALS") || name.contains("HAY_BLOCK")) {
            return TerrainCategory.FOLIAGE;
        }

        // Anything solid but unrecognised still deserves to be visible.
        return material.isSolid() ? TerrainCategory.STRUCTURE : TerrainCategory.UNKNOWN;
    }
}
