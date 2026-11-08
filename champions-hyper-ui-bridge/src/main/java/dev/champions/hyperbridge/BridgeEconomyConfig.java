package dev.champions.hyperbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * Server authoritative prices. Config may be edited while stopped.
 * Do not expose free editing when the economy provider is unavailable.
 */
public final class BridgeEconomyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static BridgeEconomyConfig INSTANCE = new BridgeEconomyConfig();

    public boolean enableCobbleDollars = true;
    public boolean operatorBypassPayment = false;
    public boolean allowFreeEditsIfProviderMissing = false;
    public long levelPerPoint = 50;
    public long levelReset = 2000;
    public long ivPerPoint = 100;
    public long evPerPoint = 20;
    public long nature = 1500;
    public long ability = 2000;
    public long hiddenAbility = 5000;
    public long friendshipPerPoint = 10;
    public boolean allowAbilityEditing = true;

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("champions-hyper-ui-bridge.json");
        try {
            if (Files.exists(path)) {
                BridgeEconomyConfig loaded = GSON.fromJson(
                    Files.readString(path, StandardCharsets.UTF_8), BridgeEconomyConfig.class);
                if (loaded != null) INSTANCE = loaded;
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path,GSON.toJson(INSTANCE),StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            HyperBridgeMod.LOG.error("Invalid economy config, using safe defaults",ex);
            INSTANCE = new BridgeEconomyConfig();
        }
        BridgeEconomyConfig c=INSTANCE;
        c.levelPerPoint=positive(c.levelPerPoint);
        c.levelReset=positive(c.levelReset);
        c.ivPerPoint=positive(c.ivPerPoint);
        c.evPerPoint=positive(c.evPerPoint);
        c.nature=positive(c.nature);
        c.ability=positive(c.ability);
        c.hiddenAbility=positive(c.hiddenAbility);
        c.friendshipPerPoint=positive(c.friendshipPerPoint);
    }

    private static long positive(long x) { return Math.max(0,Math.min(x,100_000_000L)); }
}