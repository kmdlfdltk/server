package dev.champions.hyperbridge;

import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.pokeball.PokeBall;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class BallChoices {
    // Known built-in IDs are only used as a fallback when PokeBall's identifier
    // accessor differs between Cobblemon versions. Actual edits resolve through
    // the live Cobblemon PokeBalls registry.
    private static final List<String> BUILT_INS=List.of(
        "poke_ball","great_ball","ultra_ball","master_ball","safari_ball",
        "fast_ball","level_ball","lure_ball","heavy_ball","love_ball",
        "friend_ball","moon_ball","sport_ball","net_ball","dive_ball",
        "nest_ball","repeat_ball","timer_ball","luxury_ball","premier_ball",
        "dusk_ball","heal_ball","quick_ball","cherish_ball","park_ball",
        "dream_ball","beast_ball","slate_ball","azure_ball","verdant_ball",
        "roseate_ball","citrine_ball","ancient_poke_ball",
        "ancient_great_ball","ancient_ultra_ball","ancient_heavy_ball",
        "ancient_leaden_ball","ancient_gigaton_ball",
        "ancient_feather_ball","ancient_wing_ball","ancient_jet_ball");
    private BallChoices(){}

    public static String normalize(String candidate) {
        if(candidate==null)throw new IllegalArgumentException("볼 이름이 비어 있습니다");
        String name=candidate.trim().toLowerCase(Locale.ROOT);
        if(name.startsWith("cobblemon:"))name=name.substring("cobblemon:".length());
        if(name.startsWith("item.cobblemon."))name=name.substring("item.cobblemon.".length());
        if(name.endsWith(".name"))name=name.substring(0,name.length()-5);
        name=name.replace('-', '_').replace(' ', '_');
        if(!name.matches("[a-z0-9_]{3,60}"))
            throw new IllegalArgumentException("지원하지 않는 볼 식별자: "+candidate);
        return name;
    }

    public static PokeBall require(String candidate) {
        String name=normalize(candidate);
        PokeBall ball=PokeBalls.getPokeBall(
            ResourceLocation.fromNamespaceAndPath("cobblemon",name));
        if(ball==null || !PokeBalls.all().contains(ball))
            throw new IllegalArgumentException("코블몬에 등록되지 않은 볼: "+candidate);
        return ball;
    }

    public static String idOf(PokeBall ball) {
        if(ball==null)return "poke_ball";
        for(String accessor:List.of("getName","getIdentifier","getId")) {
            try {
                Method getter=ball.getClass().getMethod(accessor);
                Object raw=getter.invoke(ball);
                if(raw!=null) {
                    String identifier=raw.toString();
                    if(identifier.contains(":"))identifier=identifier.substring(
                        identifier.indexOf(':')+1);
                    return normalize(identifier);
                }
            } catch(Exception ignored) {}
        }
        // No private field writes. Resolve known values by registry identity.
        for(String id:BUILT_INS) {
            PokeBall registry=PokeBalls.getPokeBall(
                ResourceLocation.fromNamespaceAndPath("cobblemon",id));
            if(registry==ball || Objects.equals(registry,ball))return id;
        }
        return "poke_ball";
    }

    public static void set(PokeBall original, PokeBall next,
                           com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
        pokemon.setCaughtBall(next);
        if(pokemon.getCaughtBall()!=next &&
           !Objects.equals(pokemon.getCaughtBall(),next))
            throw new IllegalStateException("볼 변경이 적용되지 않았습니다");
    }
}
