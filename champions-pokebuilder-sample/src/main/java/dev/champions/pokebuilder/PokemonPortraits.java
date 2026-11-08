package dev.champions.pokebuilder;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders genuine Cobblemon 3D portraits, not fake sprites or generic Pokeball icons.
 * Uses Cobblemon's profile GUI renderer (the same approach Cobblemon storage uses).
 *
 * Adapted from the Apache-2.0-licensed GUI rendering integration described in
 * https://github.com/TrAdrienGit/Phantasmon-Client
 * No third-party texture/model assets are bundled; the installed Cobblemon
 * resource pack provides them. All reflection errors fall back to species text.
 */
public final class PokemonPortraits {
    private static Method cached;
    private static boolean unavailable;
    private PokemonPortraits() {}

    public static boolean render(GuiGraphics graphics, String speciesId,
                                 String form, boolean shiny,
                                 int centerX, int anchorY, float outerScale) {
        if (unavailable || speciesId == null || speciesId.isBlank()) return false;
        Species species = PokemonSpecies.INSTANCE.getByName(speciesId);
        if (species == null) return false;

        Set<String> aspects = new HashSet<>();
        if (form != null && !form.isBlank() && !form.equalsIgnoreCase("standard")) {
            String f = form.toLowerCase(java.util.Locale.ROOT);
            for (var candidate : species.getForms()) {
                if (candidate.getName().equalsIgnoreCase(form)) {
                    aspects.addAll(candidate.getAspects());
                    f = null;
                    break;
                }
            }
            if (f != null) aspects.add(f);
        }
        if (shiny) aspects.add("shiny");
        RenderablePokemon model = new RenderablePokemon(species, aspects, ItemStack.EMPTY);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(centerX, anchorY, 0);
            pose.scale(outerScale, outerScale, 1.0f);
            Quaternionf turn = QuaternionUtilsKt.fromEulerXYZDegrees(
                new Quaternionf(), new Vector3f(13.0f, 35.0f, 0.0f));
            bridge().invoke(null,
                model, pose, turn, null, new FloatingState(),
                0f, 4.5f, null, false,
                0f, 0f, 0f, 0f, 0f, 0f,
                0, 65416, null);
            return true;
        } catch (Throwable e) {
            unavailable = true;
            PokeBuilderMod.LOGGER.warn("Cobblemon portrait renderer unavailable; using text fallback", e);
            return false;
        } finally {
            pose.popPose();
        }
    }

    private static Method bridge() throws NoSuchMethodException {
        if (cached != null) return cached;
        for (Method method : PokemonGuiUtilsKt.class.getMethods()) {
            if (method.getName().equals("drawProfilePokemon$default")
                && method.getParameterCount() == 18
                && method.getParameterTypes()[0] == RenderablePokemon.class) {
                method.setAccessible(true);
                cached = method;
                return method;
            }
        }
        throw new NoSuchMethodException("Cobblemon 1.8.1 portrait bridge");
    }
}
