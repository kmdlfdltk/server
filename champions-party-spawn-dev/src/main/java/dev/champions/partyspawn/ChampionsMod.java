package dev.champions.partyspawn;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChampionsMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ChampionsPartySpawn");
    private static final Set<String> BUCKETS = Set.of("common", "uncommon", "rare", "ultra-rare");
    private static final Map<UUID, Long> LAST_SPAWN_QUERY = new ConcurrentHashMap<>();

    @Override public void onInitialize() {
        ChampionsPackets.registerTypes();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("파티").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                showPresets(p, "");
                return 1;
            }));
            dispatcher.register(Commands.literal("프리셋").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                showPresets(p, "");
                return 1;
            }));
        });

        ServerPlayNetworking.registerGlobalReceiver(ChampionsPackets.Request.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> handle(player, payload));
        });
    }

    private static PartyPresetRepository repository(ServerPlayer player) {
        Path worldDir = player.getServer().getWorldPath(LevelResource.ROOT);
        return new PartyPresetRepository(worldDir.resolve("champions_party_presets"));
    }

    private static void send(ServerPlayer player, String kind, JsonObject obj) {
        ServerPlayNetworking.send(player, new ChampionsPackets.Reply(kind, obj.toString()));
    }
    private static void error(ServerPlayer player, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("message", message);
        send(player, "ERROR", result);
    }

    private static void showPresets(ServerPlayer player, String message) {
        try {
            JsonObject data = new JsonObject();
            JsonArray presets = new JsonArray();
            for (var entry : repository(player).list(player.getUUID()).entrySet()) {
                JsonObject slot = new JsonObject();
                slot.addProperty("name", entry.getKey());
                slot.addProperty("count", entry.getValue().size());
                presets.add(slot);
            }
            JsonArray party = new JsonArray();
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                party.add(pokemon.getSpecies().getName());
            }
            data.add("presets", presets);
            data.add("party", party);
            data.addProperty("message", message);
            send(player, "PRESETS", data);
        } catch (Exception ex) {
            LOGGER.error("Preset listing failed", ex);
            error(player, "프리셋 목록을 가져올 수 없습니다: " + ex.getMessage());
        }
    }

    private static void handle(ServerPlayer player, ChampionsPackets.Request payload) {
        if (player.isRemoved()) return;
        String action = payload.action();
        String value = payload.value();
        try {
            PartyPresetRepository repo = repository(player);
            PartyPresetService service =
                new PartyPresetService(new CobblemonStorageAdapter(player.getServer()));
            switch (action) {
                case "OPEN" -> showPresets(player, "");
                case "SAVE" -> {
                    repo.save(player.getUUID(), value, service.captureCurrentParty(player.getUUID()));
                    showPresets(player, "저장 완료: " + value);
                }
                case "LOAD" -> {
                    List<UUID> saved = repo.load(player.getUUID(), value)
                        .orElseThrow(() -> new IllegalStateException("존재하지 않는 프리셋"));
                    service.applySavedPreset(player.getUUID(), saved);
                    showPresets(player, "불러오기 완료: " + value);
                }
                case "DELETE" -> {
                    if (!repo.delete(player.getUUID(), value))
                        throw new IllegalArgumentException("프리셋을 찾을 수 없습니다");
                    showPresets(player, "삭제 완료: " + value);
                }
                case "SPAWN" -> showSpawns(player, value);
                default -> error(player, "알 수 없는 요청");
            }
        } catch (Exception ex) {
            LOGGER.warn("Party/Spawn request {} failed for {}", action, player.getGameProfile().getName(), ex);
            error(player, ex.getMessage() == null ? "요청 처리 실패" : ex.getMessage());
        }
    }

    /** Server runs a single, fixed, privileged diagnostic command. Player never gains OP. */
    private static void showSpawns(ServerPlayer player, String bucket) {
        if (!BUCKETS.contains(bucket)) {
            error(player, "허용되지 않은 희귀도"); return;
        }
        long now = System.currentTimeMillis();
        long last = LAST_SPAWN_QUERY.getOrDefault(player.getUUID(), 0L);
        if (now - last < 1800) {
            error(player, "잠시 후 다시 조회해주세요"); return;
        }
        LAST_SPAWN_QUERY.put(player.getUUID(), now);

        List<String> lines = new ArrayList<>();
        CommandSource collector = new CommandSource() {
            @Override public void sendSystemMessage(Component message) {
                String plain = message.getString();
                for (String line : plain.split("\\R")) {
                    if (lines.size() < 100 && !line.isBlank())
                        lines.add(line.substring(0, Math.min(line.length(), 240)));
                }
            }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        CommandSourceStack source = player.createCommandSourceStack()
            .withPermission(2).withSource(collector);
        // IMPORTANT: the argument is an allowlisted bucket, never arbitrary client command text.
        player.getServer().getCommands().performPrefixedCommand(source, "checkspawn " + bucket);

        JsonObject data = new JsonObject();
        data.addProperty("bucket", bucket);
        data.addProperty("world", player.serverLevel().dimension().location().toString());
        data.addProperty("biome", player.serverLevel().getBiome(player.blockPosition()).unwrapKey()
            .map(key -> key.location().toString()).orElse("unknown"));
        JsonArray results = new JsonArray();
        if (lines.isEmpty()) lines.add("스폰 결과를 수집하지 못했습니다. 서버 로그를 확인해주세요.");
        for (String line : lines) results.add(line);
        data.add("lines", results);
        send(player, "SPAWNS", data);
    }
}
