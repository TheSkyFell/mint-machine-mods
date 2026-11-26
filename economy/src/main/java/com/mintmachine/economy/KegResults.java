package com.mintmachine.economy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Either;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * What a Brewin' and Chewin' keg recipe makes, looked up the same way in single player and on a dedicated server.
 * <p>
 * A keg recipe that makes a fluid reports its result as the item the fluid is poured into. Brewin' and Chewin' finds
 * that item through the keg pouring recipes, but when a player's game runs in the same process (single player) it asks
 * the player's game, which has no recipes yet when prices are worked out, so the answer is empty and the drink can't be
 * priced from its recipe. This asks the server's recipes instead, as a dedicated server does. Pouring recipes that
 * aren't strict are tried first, as Brewin' and Chewin' does, then by id so the answer doesn't depend on load order.
 * <p>
 * Read by reflection (Brewin' and Chewin' 4.5.0): {@code KegFermentingRecipe.getResult()} is an
 * {@code Either<AbstractedFluidStack, ItemStack>}; {@code KegPouringRecipe} has {@code getRawFluid()},
 * {@code isStrict()} and {@code getOutput()}; {@code AbstractedFluidStack.matches(other)} compares fluid and components.
 */
final class KegResults {
    static final String FERMENTING = "umpaz.brewinandchewin.common.crafting.KegFermentingRecipe";
    private static final ResourceLocation POURING = ResourceLocation.fromNamespaceAndPath("brewinandchewin", "keg_pouring");

    /** For packs without Brewin' and Chewin'. */
    static final KegResults NONE = new KegResults(List.of());

    private final List<Recipe<?>> pouring;

    private KegResults(List<Recipe<?>> pouring) {
        this.pouring = pouring;
    }

    static KegResults of(Collection<RecipeHolder<?>> holders) {
        List<RecipeHolder<?>> found = new ArrayList<>();
        for (RecipeHolder<?> h : holders) {
            if (POURING.equals(BuiltInRegistries.RECIPE_TYPE.getKey(h.value().getType()))) found.add(h);
        }
        if (found.isEmpty()) return NONE;
        found.sort(Comparator.<RecipeHolder<?>, Boolean>comparing(h -> isStrict(h.value())).thenComparing(h -> h.id().toString()));
        List<Recipe<?>> recipes = new ArrayList<>();
        for (RecipeHolder<?> h : found) recipes.add(h.value());
        return new KegResults(recipes);
    }

    boolean handles(Recipe<?> recipe) {
        return recipe.getClass().getName().equals(FERMENTING);
    }

    /** The item the recipe makes, or an empty stack if it can't be worked out. */
    ItemStack resultOf(Recipe<?> recipe) throws ReflectiveOperationException {
        Either<?, ?> result = (Either<?, ?>) recipe.getClass().getMethod("getResult").invoke(recipe);
        Optional<?> item = result.right();
        if (item.isPresent()) return ((ItemStack) item.get()).copy();
        Optional<?> fluid = result.left();
        if (fluid.isEmpty()) return ItemStack.EMPTY;
        for (Recipe<?> p : pouring) {
            Object raw = p.getClass().getMethod("getRawFluid").invoke(p);
            if (raw != null && Boolean.TRUE.equals(matches(raw, fluid.get()))) {
                return ((ItemStack) p.getClass().getMethod("getOutput").invoke(p)).copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static Object matches(Object raw, Object fluid) throws ReflectiveOperationException {
        for (Method m : raw.getClass().getMethods()) {
            if (m.getName().equals("matches") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(fluid)) {
                return m.invoke(raw, fluid);
            }
        }
        throw new NoSuchMethodException(raw.getClass().getName() + ".matches");
    }

    private static boolean isStrict(Recipe<?> recipe) {
        try {
            return Boolean.TRUE.equals(recipe.getClass().getMethod("isStrict").invoke(recipe));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
