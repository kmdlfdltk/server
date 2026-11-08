package dev.champions.hyperbridge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.loader.api.FabricLoader;

/** Reads installed Cobblemon's Korean translation data; no hardcoded species names. */
public final class SpeciesNames {
    private static volatile Map<String,String> translations;
    private SpeciesNames() {}

    private static synchronized Map<String,String> names() {
        if (translations!=null)return translations;
        Map<String,String> map=new HashMap<>();
        try {
            var mod=FabricLoader.getInstance().getModContainer("cobblemon")
                .orElseThrow();
            var resource=mod.findPath("assets/cobblemon/lang/ko_kr.json")
                .orElseThrow();
            JsonObject root=JsonParser.parseString(
                Files.readString(resource,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:root.entrySet()) {
                if(entry.getValue().isJsonPrimitive())
                    map.put(entry.getKey(),entry.getValue().getAsString());
            }
        } catch(Exception e) {
            HyperBridgeMod.LOG.warn("Cobblemon Korean language file unavailable; using fallback",e);
        }
        translations=Map.copyOf(map);
        return translations;
    }

    public static String of(Pokemon p) {
        if(p.getNickname()!=null && !p.getNickname().getString().isBlank())
            return p.getNickname().getString();
        String id=p.getSpecies().getResourceIdentifier().getPath();
        String key="cobblemon.species."+id+".name";
        return names().getOrDefault(key,p.getSpecies().getTranslatedName().getString());
    }
}