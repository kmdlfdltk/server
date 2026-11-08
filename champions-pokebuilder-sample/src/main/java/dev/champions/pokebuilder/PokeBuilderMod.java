package dev.champions.pokebuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-authoritative standalone sample. Player sees only their own party.
 * No automatic CobbleDollars charge: all modifications are operator-only
 * by default, until a transactional economy bridge is implemented.
 */
public final class PokeBuilderMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ChampionsPokeBuilder");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Stats[] STATS = {
        Stats.HP, Stats.ATTACK, Stats.DEFENCE,
        Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };
    private static final String[] KEYS = {"hp","atk","def","spa","spd","spe"};
    private static final Set<String> NATURES = Set.of(
        "hardy","lonely","brave","adamant","naughty","bold","docile","relaxed",
        "impish","lax","timid","hasty","serious","jolly","naive",
        "modest","mild","quiet","bashful","rash","calm","gentle",
        "sassy","careful","quirky"
    );
    private static Config config = new Config();

    public static final class Config {
        public boolean requiresPermissionLevel2 = true;
        public boolean allowLevelEditing = true;
        public boolean allowIvEditing = true;
        public boolean allowEvEditing = true;
        public boolean allowNatureEditing = true;
        public boolean allowFriendshipEditing = true;
        public int maximumLevel = 100;
    }

    @Override
    public void onInitialize() {
        loadConfig();
        PokeBuilderPackets.registerTypes();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("포켓빌더").executes(ctx -> {
                show(ctx.getSource().getPlayerOrException(), 0, "");
                return 1;
            }));
            dispatcher.register(Commands.literal("pokebuilder").executes(ctx -> {
                show(ctx.getSource().getPlayerOrException(), 0, "");
                return 1;
            }));
        });

        ServerPlayNetworking.registerGlobalReceiver(PokeBuilderPackets.Request.TYPE,
            (packet, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null || player.isRemoved()) return;
                    try {
                        if ("OPEN".equals(packet.action())) {
                            show(player, packet.slot(), "");
                        } else if ("SET".equals(packet.action())) {
                            updatePokemon(player, packet);
                        }
                    } catch (Exception error) {
                        LOGGER.warn("PokeBuilder request rejected for {}",
                            player.getGameProfile().getName(), error);
                        JsonObject response = new JsonObject();
                        response.addProperty("message", error.getMessage() == null
                            ? "요청 처리 실패" : error.getMessage());
                        ServerPlayNetworking.send(player,
                            new PokeBuilderPackets.Reply("ERROR", response.toString()));
                    }
                })
        );
        LOGGER.info("Champions PokeBuilder sample initialized (operator edits: {})",
            config.requiresPermissionLevel2);
    }

    private static void loadConfig() {
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("champions-pokebuilder.json");
        try {
            if (Files.exists(path)) {
                Config loaded = GSON.fromJson(
                    Files.readString(path, StandardCharsets.UTF_8), Config.class);
                if (loaded != null) config = loaded;
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException problem) {
            LOGGER.error("Failed to read PokeBuilder config, keeping operator-only defaults", problem);
            config = new Config();
        }
        config.maximumLevel = Math.max(1, Math.min(100, config.maximumLevel));
    }

    private static boolean canEdit(ServerPlayer p) {
        return !config.requiresPermissionLevel2 || p.hasPermissions(2);
    }

    private static Pokemon partyPokemon(ServerPlayer p, int slot) {
        if (slot < 0 || slot > 5) throw new IllegalArgumentException("포켓몬 슬롯 범위 오류");
        Pokemon poke = Cobblemon.INSTANCE.getStorage().getParty(p).get(slot);
        if (poke == null) throw new IllegalArgumentException("해당 슬롯에 포켓몬이 없습니다");
        return poke;
    }

    private static int statIndex(String field) {
        for (int i=0;i<KEYS.length;i++) if (KEYS[i].equals(field)) return i;
        throw new IllegalArgumentException("올바르지 않은 능력치");
    }

    private static int parseValue(String value, int min, int max) {
        try {
            int num = Integer.parseInt(value);
            if (num < min || num > max) throw new IllegalArgumentException(
                "값은 " + min + "부터 " + max + "까지 가능합니다");
            return num;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("숫자 입력이 필요합니다");
        }
    }

    private static void updatePokemon(ServerPlayer player,
                                      PokeBuilderPackets.Request packet) {
        if (!canEdit(player)) throw new IllegalStateException(
            "샘플 편집은 OP 2레벨 이상만 가능합니다 (서버 설정 확인)");
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null)
            throw new IllegalStateException("배틀 중에는 변경할 수 없습니다");

        Pokemon poke = partyPokemon(player, packet.slot());
        if (poke.getEntity() != null)
            throw new IllegalStateException("포켓몬을 볼에 넣은 뒤 변경해주세요");

        String field = packet.field().toLowerCase(Locale.ROOT);
        String value = packet.value().toLowerCase(Locale.ROOT);
        if ("level".equals(field)) {
            if (!config.allowLevelEditing) throw new IllegalStateException("레벨 편집 비활성화");
            poke.setLevel(parseValue(value, 1, config.maximumLevel));
        } else if (field.startsWith("iv.")) {
            if (!config.allowIvEditing) throw new IllegalStateException("개체값 편집 비활성화");
            poke.setIV(STATS[statIndex(field.substring(3))], parseValue(value, 0, 31));
        } else if (field.startsWith("ev.")) {
            if (!config.allowEvEditing) throw new IllegalStateException("노력치 편집 비활성화");
            Stats stat = STATS[statIndex(field.substring(3))];
            int requested = parseValue(value, 0, 252);
            int current = poke.getEvs().getOrDefault(stat);
            if (poke.getEvs().total() - current + requested > 510)
                throw new IllegalStateException("노력치 총합이 510을 초과합니다");
            poke.setEV(stat, requested);
        } else if ("nature".equals(field)) {
            if (!config.allowNatureEditing) throw new IllegalStateException("성격 편집 비활성화");
            if (!NATURES.contains(value)) throw new IllegalArgumentException("지원하지 않는 성격");
            Nature nature = Natures.getNature(value);
            if (nature == null) throw new IllegalArgumentException("성격을 찾지 못했습니다");
            poke.setNature(nature);
        } else if ("friendship".equals(field)) {
            if (!config.allowFriendshipEditing) throw new IllegalStateException("친밀도 편집 비활성화");
            poke.setFriendship(parseValue(value, 0, 255), false);
        } else {
            throw new IllegalArgumentException("지원하지 않는 편집 항목");
        }
        // Cobblemon setter methods notify their owning storage of changes.
        show(player, packet.slot(), "변경 적용 완료: " + field);
    }

    private static JsonObject preview(Pokemon poke, int slot) {
        JsonObject o = new JsonObject();
        o.addProperty("slot", slot);
        if (poke == null) {
            o.addProperty("empty", true);
            return o;
        }
        o.addProperty("empty", false);
        o.addProperty("uuid", poke.getUuid().toString());
        o.addProperty("species", poke.getSpecies().getName());
        o.addProperty("displayName", poke.getSpecies().getTranslatedName().getString());
        o.addProperty("form", poke.getForm().getName());
        o.addProperty("shiny", poke.getShiny());
        o.addProperty("level", poke.getLevel());
        o.addProperty("nature", poke.getNature().getName().getPath());
        o.addProperty("friendship", poke.getFriendship());
        o.addProperty("gender", poke.getGender().name());
        o.addProperty("ability", poke.getAbility().getName());
        JsonArray ivs = new JsonArray(), evs = new JsonArray();
        for (Stats stat : STATS) {
            ivs.add(poke.getIvs().getOrDefault(stat));
            evs.add(poke.getEvs().getOrDefault(stat));
        }
        o.add("ivs", ivs);
        o.add("evs", evs);
        o.addProperty("ivTotal", poke.getIvs().total());
        o.addProperty("evTotal", poke.getEvs().total());
        return o;
    }

    private static void show(ServerPlayer player, int selected, String notice) {
        JsonObject data = new JsonObject();
        JsonArray party = new JsonArray();
        for (int i=0; i<6; i++)
            party.add(preview(Cobblemon.INSTANCE.getStorage().getParty(player).get(i), i));
        data.add("party",party);
        data.addProperty("selected", Math.max(0,Math.min(5,selected)));
        data.addProperty("message",notice);
        data.addProperty("canEdit",canEdit(player));
        data.addProperty("maxLevel",config.maximumLevel);
        ServerPlayNetworking.send(player,new PokeBuilderPackets.Reply("STATE",data.toString()));
    }
}
