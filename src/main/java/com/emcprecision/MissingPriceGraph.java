package com.emcprecision;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import moze_intel.projecte.api.mapper.arithmetic.IValueArithmetic;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.emc.SimpleGraphMapper;
import moze_intel.projecte.shaded.org.apache.commons.math3.fraction.BigFraction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import static com.emcprecision.ItemPriceRules.positive;

public final class MissingPriceGraph extends SimpleGraphMapper<NormalizedSimpleStack, BigFraction, IValueArithmetic<BigFraction>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RECIPE_ROUNDS = 12;
    private record Candidate(ResourceLocation id, Item item) {}
    private record Decision(BigInteger tenths, String source, String basis) {}

    public MissingPriceGraph(IValueArithmetic<BigFraction> arithmetic) {
        super(arithmetic);
    }

    @Override
    public Map<NormalizedSimpleStack, BigFraction> generateValues() {
        var original = super.generateValues();
        long started = System.nanoTime();
        Map<NormalizedSimpleStack, Candidate> candidates = new HashMap<>();
        Map<String, Decision> report = new TreeMap<>();
        BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
            if (entry.getValue() == Items.AIR) return;
            var id = entry.getKey().location();
            var key = NSSItem.createItem(id);
            var originalValue = original.get(key);
            if (positive(originalValue) && originalValue.compareTo(BigFraction.ONE) >= 0) return;
            String exclusion = exclusion(key, id, entry.getValue(), originalValue);
            if (exclusion == null) {
                candidates.put(key, new Candidate(id, entry.getValue()));
            } else {
                report.put(id.toString(), new Decision(BigInteger.ZERO, exclusion, ""));
            }
        });

        Map<NormalizedSimpleStack, BigFraction> working = new HashMap<>(original);
        // Only explicit zero/free mappings stay excluded. A rejected recipe graph node can get a category estimate.
        candidates.keySet().forEach(working::remove);
        Map<NormalizedSimpleStack, ItemPriceRules.Quote> quotes = new HashMap<>();
        var floors = recoverableValues(original, candidates.keySet());
        resolveRecipes(working, candidates.keySet(), quotes, floors, "recipe");
        var rules = new ItemPriceRules(working);
        candidates.forEach((key, candidate) -> {
            if (!quotes.containsKey(key) && !hasRecipe(key)) {
                var quote = applyFloor(rules.estimate(candidate.id(), candidate.item()), floors.get(key));
                quotes.put(key, quote);
            }
        });
        quotes.forEach((key, quote) -> working.put(key, quote.tenths()));
        resolveRecipes(working, candidates.keySet(), quotes, floors, "recipe_with_estimates");

        int added = 0;
        for (var entry : candidates.entrySet()) {
            var candidate = entry.getValue();
            var quote = quotes.get(entry.getKey());
            if (quote == null) {
                quote = applyFloor(rules.estimate(candidate.id(), candidate.item()), floors.get(entry.getKey()));
            }
            var tenths = quote.tenths().getNumerator().divide(quote.tenths().getDenominator());
            report.put(candidate.id().toString(), new Decision(tenths, quote.source(), quote.basis()));
            if (tenths.signum() > 0) {
                original.put(entry.getKey(), quote.tenths());
                added++;
            }
        }
        writeAudit(report);
        LOGGER.info("EMC Precision assigned {} missing prices, reviewed {} items in {} ms",
                added, report.size(), (System.nanoTime() - started) / 1_000_000);
        return original;
    }

    private String exclusion(NormalizedSimpleStack key, ResourceLocation id, Item item, BigFraction existing) {
        var fixed = fixValueAfterInherit.getOrDefault(key, fixValueBeforeInherit.get(key));
        if (fixed != null && !positive(fixed)) return "projecte_explicit_zero_or_free";
        var rule = ItemPriceRules.exclusion(id, item);
        if (rule != null) return rule;
        if (positive(existing)) return "recipe_below_one_tenth";
        return null;
    }

    private boolean hasRecipe(NormalizedSimpleStack key) {
        return overwriteConversion.containsKey(key) || conversionsFor.getOrDefault(key, Set.of()).stream()
                .anyMatch(conversion -> !alias(conversion) && !conversion.ingredientsWithAmount.isEmpty());
    }

    private boolean alias(Conversion conversion) {
        return conversion.ingredientsWithAmount.size() == 1
                && conversion.ingredientsWithAmount.keySet().iterator().next() instanceof NSSItem item && item.representsTag();
    }

    private void resolveRecipes(Map<NormalizedSimpleStack, BigFraction> values, Set<NormalizedSimpleStack> candidates,
            Map<NormalizedSimpleStack, ItemPriceRules.Quote> quotes,
            Map<NormalizedSimpleStack, ItemPriceRules.Quote> floors, String source) {
        for (int round = 0; round < RECIPE_ROUNDS; round++) {
            Map<NormalizedSimpleStack, ItemPriceRules.Quote> next = new HashMap<>();
            conversionsFor.forEach((key, recipes) ->
                    resolveOne(key, recipes, values, candidates, next, floors, source));
            overwriteConversion.forEach((key, conversion) -> {
                if (!conversionsFor.containsKey(key)) {
                    resolveOne(key, Set.of(conversion), values, candidates, next, floors, source);
                }
            });
            if (next.isEmpty()) return;
            next.forEach((key, quote) -> {
                values.put(key, quote.tenths());
                if (candidates.contains(key)) quotes.put(key, quote);
            });
        }
    }

    private void resolveOne(NormalizedSimpleStack key, Set<Conversion> recipes, Map<NormalizedSimpleStack, BigFraction> values,
            Set<NormalizedSimpleStack> candidates, Map<NormalizedSimpleStack, ItemPriceRules.Quote> next,
            Map<NormalizedSimpleStack, ItemPriceRules.Quote> floors, String source) {
        if (values.containsKey(key) || fixValueBeforeInherit.containsKey(key) || fixValueAfterInherit.containsKey(key)) return;
        if (key instanceof NSSItem item && !item.representsTag() && !candidates.contains(key)) return;
        Conversion forced = overwriteConversion.get(key);
        ItemPriceRules.Quote best = null;
        for (Conversion recipe : forced == null ? recipes : Set.of(forced)) {
            var price = recipePrice(recipe, values);
            if (!positive(price) || price.compareTo(BigFraction.ONE) < 0) continue;
            String basis = recipe.toString();
            int order = best == null ? -1 : price.compareTo(best.tenths());
            if (order < 0 || (order == 0 && basis.compareTo(best.basis()) < 0)) {
                best = new ItemPriceRules.Quote(price, source, basis);
            }
        }
        if (best != null) next.put(key, applyFloor(best, floors.get(key)));
    }

    private BigFraction recipePrice(Conversion conversion, Map<NormalizedSimpleStack, BigFraction> values) {
        BigFraction cost = conversion.value;
        for (var ingredient : conversion.ingredientsWithAmount.object2IntEntrySet()) {
            var key = ingredient.getKey();
            var value = values.get(key);
            if (!positive(value) || key.equals(conversion.output)) return null;
            cost = cost.add(value.multiply(ingredient.getIntValue()));
        }
        return cost.divide(conversion.outnumber);
    }

    private Map<NormalizedSimpleStack, ItemPriceRules.Quote> recoverableValues(
            Map<NormalizedSimpleStack, BigFraction> original, Set<NormalizedSimpleStack> candidates) {
        Map<NormalizedSimpleStack, ItemPriceRules.Quote> floors = new HashMap<>();
        conversionsFor.forEach((output, conversions) -> {
            var value = original.get(output);
            if (!(output instanceof NSSItem item) || item.representsTag() || !positive(value)) return;
            for (Conversion conversion : conversions) {
                if (conversion.ingredientsWithAmount.size() != 1) continue;
                var input = conversion.ingredientsWithAmount.object2IntEntrySet().iterator().next();
                if (!candidates.contains(input.getKey()) || input.getIntValue() <= 0) continue;
                var floor = value.multiply(conversion.outnumber).divide(input.getIntValue());
                var quote = new ItemPriceRules.Quote(floor, "recoverable_recipe_output", conversion.toString());
                floors.merge(input.getKey(), quote, (a, b) -> a.tenths().compareTo(b.tenths()) > 0
                        || a.tenths().equals(b.tenths()) && a.basis().compareTo(b.basis()) < 0 ? a : b);
            }
        });
        return floors;
    }

    private static ItemPriceRules.Quote applyFloor(ItemPriceRules.Quote quote, ItemPriceRules.Quote floor) {
        return floor != null && floor.tenths().compareTo(quote.tenths()) > 0 ? floor : quote;
    }

    private static void writeAudit(Map<String, Decision> report) {
        var directory = FMLPaths.CONFIGDIR.get();
        var file = directory.resolve("emc_precision-auto-prices.json");
        try {
            var temporary = Files.createTempFile(directory, "emc_precision-prices", ".tmp");
            try {
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
                Files.writeString(temporary, json);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            LOGGER.warn("Failed to record EMC Precision prices to {}", file, error);
        }
    }
}
