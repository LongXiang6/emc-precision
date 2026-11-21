package com.emcprecision;

import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexPotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.MillstoneRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.tag.TagMod;
import com.tagnumelite.projecteintegration.api.recipe.ACustomRecipeMapper;
import com.tagnumelite.projecteintegration.api.recipe.CustomRecipeMapper;
import com.tagnumelite.projecteintegration.api.recipe.nss.NSSInput;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@CustomRecipeMapper("kaleidoscope_cookery")
public final class CookeryRecipeMapper extends ACustomRecipeMapper<Recipe<?>> {
    @Override
    public List<Recipe<?>> getRecipes() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return List.of();
        }
        var registries = server.registryAccess();
        var loaded = server.getRecipeManager().getRecipes();
        var furnaceResults = loaded.stream().map(RecipeHolder::value)
                .filter(AbstractCookingRecipe.class::isInstance)
                .map(recipe -> recipe.getResultItem(registries).getItem())
                .collect(Collectors.toSet());
        // Cookery uses RecipeType.simple without registering these types in the vanilla registry.
        return loaded.stream()
                .<Recipe<?>>map(RecipeHolder::value)
                .filter(CookeryRecipeMapper::hasSingleOutput)
                .filter(recipe -> "kaleidoscope_cookery".equals(BuiltInRegistries.ITEM
                        .getKey(recipe.getResultItem(registries).getItem()).getNamespace()))
                .filter(recipe -> !(recipe instanceof PotRecipe || recipe instanceof FlexPotRecipe)
                        || !furnaceResults.contains(recipe.getResultItem(registries).getItem()))
                .toList();
    }

    private static boolean hasSingleOutput(Recipe<?> recipe) {
        return switch (recipe) {
            case MillstoneRecipe mill -> mill.results().size() == 1 && mill.results().getFirst().chance() == 1;
            case ChoppingBoardRecipe board -> board.getResults().size() == 1;
            case PotRecipe pot -> true;
            case FlexPotRecipe pot -> true;
            case SteamerRecipe steamer -> true;
            default -> false;
        };
    }

    @Override
    protected List<Ingredient> getIngredients(Recipe<?> recipe) {
        return switch (recipe) {
            case ChoppingBoardRecipe board -> List.of(board.getIngredient());
            case SteamerRecipe steamer -> List.of(steamer.getIngredient());
            default -> recipe.getIngredients();
        };
    }

    @Override
    protected ItemStack getResult(Recipe<?> recipe) {
        return recipe.getResultItem(registryAccess);
    }

    @Override
    public NSSInput getInput(Recipe<?> recipe) {
        List<Ingredient> ingredients = getIngredients(recipe);
        if (ingredients.isEmpty() || ingredients.stream().anyMatch(Ingredient::isEmpty)) {
            return null;
        }
        var input = getInputBuilder();
        ingredients.forEach(input::addIngredient);
        Ingredient carrier = switch (recipe) {
            case PotRecipe pot -> pot.carrier();
            case FlexPotRecipe pot -> pot.carrier();
            default -> null;
        };
        if (carrier != null) {
            input.addIngredient(Ingredient.of(TagMod.OIL));
            if (!carrier.isEmpty()) {
                input.addIngredient(getResult(recipe).getCount(), carrier);
            }
        }
        return input.build();
    }

    @Override
    public String getName() {
        return "EMC-Precision-Cookery";
    }

    @Override
    public String getDescription() {
        return "Maps deterministic Kaleidoscope Cookery millstone, chopping, steamer and pot recipes.";
    }
}
