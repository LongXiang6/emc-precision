package com.emcprecision;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.shaded.org.apache.commons.math3.fraction.BigFraction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.FlowerBlock;

final class ItemPriceRules {
    record Quote(BigFraction tenths, String source, String basis) {}
    private record Form(String suffix, String baseSuffix, int inputs, int outputs) {}

    private static final TagKey<Item> EXCLUDED = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("emc_precision", "exclude_from_auto_pricing"));
    private static final TagKey<Item> OBTAINABLE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("emc_precision", "obtainable_spawn_eggs"));
    private static final Pattern COLOR = Pattern.compile(
            "^(light_blue|light_gray|white|orange|magenta|yellow|lime|pink|gray|cyan|purple|blue|brown|green|red|black)_");
    private static final Pattern ADMIN = Pattern.compile("(^|_)(creative|debug|placeholder)(_|$)");
    private static final Set<String> TECHNICAL = Set.of(
            "minecraft:barrier", "minecraft:bedrock", "minecraft:light", "minecraft:command_block",
            "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:command_block_minecart",
            "minecraft:jigsaw", "minecraft:structure_block", "minecraft:structure_void", "minecraft:knowledge_book",
            "minecraft:farmland", "minecraft:frogspawn", "minecraft:spawner", "minecraft:trial_spawner",
            "minecraft:vault", "minecraft:reinforced_deepslate", "minecraft:petrified_oak_slab",
            "minecraft:budding_amethyst", "minecraft:small_amethyst_bud", "minecraft:medium_amethyst_bud",
            "minecraft:large_amethyst_bud", "minecraft:dirt_path", "minecraft:infested_stone",
            "minecraft:infested_cobblestone", "minecraft:infested_stone_bricks", "minecraft:infested_deepslate",
            "minecraft:infested_cracked_stone_bricks", "minecraft:infested_mossy_stone_bricks",
            "minecraft:infested_chiseled_stone_bricks", "minecraft:filled_map", "minecraft:written_book",
            "minecraft:enchanted_book", "minecraft:suspicious_stew", "minecraft:tipped_arrow",
            "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion",
            "minecraft:firework_star", "minecraft:goat_horn", "minecraft:player_head",
            "projecte:tome", "avaritia:matter_cluster", "avaritia:full_matter_cluster",
            "ftblibrary:icon_item", "ftbquests:barrier", "ftbquests:custom_icon", "ftbquests:detector",
            "ftbquests:missing_item", "ftbquests:stage_barrier", "moonlight:placeable_item",
            "moonlight:spawn_box", "confluence_magic_lib:group", "exdeorum:crook_fake_player");
    private static final List<Form> FORMS = List.of(
            new Form("_pressure_plate", "_planks", 2, 1),
            new Form("_fence_gate", "_planks", 4, 1), new Form("_fence", "_planks", 6, 3),
            new Form("_trapdoor", "_planks", 6, 2), new Form("_door", "_planks", 6, 3),
            new Form("_button", "_planks", 1, 1), new Form("_hanging_sign", "_log", 6, 6),
            new Form("_sign", "_planks", 6, 3), new Form("_planks", "_log", 1, 4),
            new Form("_carpet", "_wool", 2, 3), new Form("_stained_glass_pane", "_stained_glass", 6, 16),
            new Form("_slab", "", 1, 2), new Form("_stairs", "", 6, 4), new Form("_wall", "", 1, 1));

    private final Map<NormalizedSimpleStack, BigFraction> prices;

    ItemPriceRules(Map<NormalizedSimpleStack, BigFraction> prices) {
        this.prices = prices;
    }

    static String exclusion(ResourceLocation id, Item item) {
        String path = id.getPath();
        if (item.builtInRegistryHolder().is(EXCLUDED)) return "pack_exclusion_tag";
        if (TECHNICAL.contains(id.toString()) || ADMIN.matcher(path).find()) return "technical_or_component_only";
        if ((item instanceof SpawnEggItem || path.endsWith("_spawn_egg"))
                && !item.builtInRegistryHolder().is(OBTAINABLE)) return "spawn_egg_without_survival_tag";
        return null;
    }

    Quote estimate(ResourceLocation id, Item item) {
        Quote value = matchingForm(id);
        if (value != null) return value;
        value = matchingTag(item);
        if (value != null) return value;
        value = matchingColor(id, item);
        if (value != null) return value;
        return category(id, item);
    }

    private Quote matchingTag(Item item) {
        return item.builtInRegistryHolder().tags()
                .filter(tag -> tag.location().getNamespace().equals("c")
                        && tag.location().getPath().contains("/")
                        && !tag.location().getPath().contains("tool/")
                        && !tag.location().getPath().startsWith("enchantables/"))
                .sorted(Comparator.comparing(TagKey::location))
                .map(tag -> BuiltInRegistries.ITEM.getTag(tag).map(group -> {
                    var known = group.stream()
                            .filter(peer -> (peer.value() instanceof BlockItem) == (item instanceof BlockItem))
                            .map(peer -> prices.get(NSSItem.createItem(peer)))
                            .filter(ItemPriceRules::positive)
                            .sorted()
                            .toList();
                    return known.isEmpty() ? null : new Quote(known.get((known.size() - 1) / 2),
                            "shared_item_tag", tag.location().toString());
                }).orElse(null))
                .filter(quote -> quote != null)
                .findFirst().orElse(null);
    }

    private Quote matchingForm(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("stripped_")) {
            Quote same = peer(id.withPath(path.substring("stripped_".length())), 1, 1, "material_variant");
            if (same != null) return same;
        }
        for (Form form : FORMS) {
            if (!path.endsWith(form.suffix())) continue;
            String base = path.substring(0, path.length() - form.suffix().length()) + form.baseSuffix();
            for (String name : List.of(base, base + "s", base + "_block", base + "_planks")) {
                Quote quote = peer(id.withPath(name), form.inputs(), form.outputs(), "material_variant");
                if (quote != null) return quote;
                quote = peer(vanilla(name), form.inputs(), form.outputs(), "material_variant");
                if (quote != null) return quote;
            }
        }
        return null;
    }

    private Quote matchingColor(ResourceLocation id, Item item) {
        var match = COLOR.matcher(id.getPath());
        if (!match.find()) return null;
        String suffix = id.getPath().substring(match.end());
        for (String prefix : List.of("white_", "red_", "", "black_", "blue_", "green_", "yellow_")) {
            var key = id.withPath(prefix + suffix);
            if (key.equals(id)) continue;
            var other = BuiltInRegistries.ITEM.getOptional(key);
            if (other.isPresent() && other.get().getClass() == item.getClass()) {
                Quote quote = peer(key, 1, 1, "color_variant");
                if (quote != null) return quote;
            }
        }
        return null;
    }

    private Quote category(ResourceLocation id, Item item) {
        String mod = id.getNamespace();
        String path = id.getPath();
        var stack = item.getDefaultInstance();
        int rarity = switch (stack.getRarity()) {
            case UNCOMMON -> 2;
            case RARE -> 4;
            case EPIC -> 8;
            default -> 1;
        };
        if (path.startsWith("music_disc_")) return anchor("music_disc_13", 20480, rarity, 1, "music_disc");
        if (path.contains("elytra")) return anchor("diamond", 81920, mod.equals("avaritia") ? 1024 : 4, 1, "flight_equipment");
        if (path.equals("singularity") || path.equals("tesseract")) return anchor("diamond", 81920, 1024, 1, "endgame_material");
        if (path.contains("aeternium") || path.contains("termininite")) {
            int ingots = path.endsWith("_block") ? 9 : stack.isDamageableItem() ? 4 : 1;
            return anchor(path.contains("aeternium") ? "netherite_ingot" : "diamond", 81920, ingots, 1, "end_material");
        }
        if (path.endsWith("_bucket") || path.startsWith("bucket_")) {
            return anchor("cod_bucket", 8320, rarity, 1, "filled_bucket");
        }
        if (path.endsWith("_ingot") || path.endsWith("_gem") || path.endsWith("_crystal")
                || path.endsWith("_shard") || path.endsWith("_pearl")) {
            return anchor("amethyst_shard", 320, rarity, 1, "gem_or_metal");
        }
        if (stack.isDamageableItem()) return anchor("iron_pickaxe", 7760, rarity, 1, "tool_or_equipment");
        if (path.endsWith("_seeds") || path.endsWith("_seed") || path.endsWith("_sapling")) {
            return anchor("wheat_seeds", 160, rarity, 1, "planting_material");
        }
        if (stack.has(DataComponents.FOOD)) {
            if (mod.equals("tide") || path.contains("fish")) return anchor("cod", 640, rarity, 1, "fish");
            if (path.endsWith("berry") || path.endsWith("_berries")) return anchor("sweet_berries", 160, rarity, 1, "berries");
            return anchor("apple", 1280, rarity, 1, "food");
        }
        if (path.endsWith("_dye")) return anchor("red_dye", 53, 1, 1, "dye");
        if (path.endsWith("_stained_glass_pane")) return anchor("glass_pane", 4, 1, 1, "stained_glass_pane");
        if (path.endsWith("_stained_glass")) return anchor("glass", 10, 1, 1, "stained_glass");
        if (path.endsWith("_concrete_powder") || path.endsWith("_concrete")) {
            return anchor("white_concrete_powder", 20, 1, 1, "concrete");
        }
        if (path.endsWith("_candle")) return anchor("candle", 640, 1, 1, "candle");
        if (path.endsWith("_bundle")) return anchor("leather", 318, 1, 1, "bundle");
        if (path.endsWith("_shulker_box")) return anchor("shulker_shell", 20480, 2, 1, "shulker_box");
        if (path.endsWith("_plate") || path.endsWith("_sheet") || path.endsWith("_gear")) {
            return anchor("iron_ingot", 2560, rarity, 1, "machined_component");
        }
        if (path.endsWith("_template") || path.endsWith("_key")) {
            return anchor("diamond", 81920, rarity, 1, "progression_item");
        }
        if (item instanceof BlockItem block) {
            if (path.endsWith("_slab")) return anchor("cobblestone", 10, rarity, 2, "slab");
            if (path.endsWith("_stairs")) return anchor("cobblestone", 10, 6 * rarity, 4, "stairs");
            if (path.endsWith("_terracotta")) return anchor("terracotta", 640, 1, 1, "terracotta");
            if (path.endsWith("_lantern") || path.endsWith("_chest")) return anchor("oak_planks", 80, 8 * rarity, 1, "utility_block");
            if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem")) {
                return anchor("oak_log", 320, 1, 1, "wood");
            }
            if (path.endsWith("_planks")) return anchor("oak_planks", 80, 1, 1, "planks");
            if (block.getBlock() instanceof FlowerBlock) return anchor("poppy", 160, 1, 1, "flower");
            if (path.contains("leaves") || path.contains("grass") || path.contains("moss") || path.endsWith("_vine")) {
                return anchor("oak_leaves", 10, 1, 1, "foliage");
            }
            if (path.endsWith("_ore") || path.startsWith("raw_")) {
                return anchor("iron_ingot", 2560, 2 * rarity, 1, "untagged_ore");
            }
            if (path.endsWith("_machine") || path.startsWith("mechanical_") || path.endsWith("_casing")
                    || path.endsWith("_controller") || path.endsWith("_workbench")) {
                return anchor("iron_ingot", 2560, 4 * rarity, 1, "machine_or_workstation");
            }
            return anchor("cobblestone", 10, rarity, 1, "block");
        }
        if (path.startsWith("raw_") || path.endsWith("_nugget")) {
            return anchor("iron_ingot", 2560, rarity, path.endsWith("_nugget") ? 9 : 1, "raw_material");
        }
        if (stack.getRarity() != Rarity.COMMON || stack.getMaxStackSize() == 1) {
            return anchor("name_tag", 1920, rarity, 1, "special_item");
        }
        return anchor("red_mushroom", 320, 1, 1, "stackable_material");
    }

    private Quote anchor(String name, long defaultTenths, int inputs, int outputs, String category) {
        var id = vanilla(name);
        var price = peer(id, inputs, outputs, "category:" + category);
        return price == null ? new Quote(new BigFraction(defaultTenths).multiply(inputs).divide(outputs),
                "category:" + category, "fixed_reference:" + id) : price;
    }

    private Quote peer(ResourceLocation id, int inputs, int outputs, String source) {
        var value = prices.get(NSSItem.createItem(id));
        return positive(value) ? new Quote(value.multiply(inputs).divide(outputs), source,
                inputs + "*" + id + "/" + outputs) : null;
    }

    private static ResourceLocation vanilla(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    static boolean positive(BigFraction value) {
        return value != null && value.getNumerator().signum() > 0;
    }
}
