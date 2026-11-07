package dev.champions.partyspawn;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Server-side UUID-only storage. Does not mutate Cobblemon Pokemon.
 * Call from the Minecraft server thread or otherwise serialize accesses.
 */
public final class PartyPresetRepository {
    public static final int MAX_PRESETS = 10;
    private final Path directory;

    public PartyPresetRepository(Path directory) {
        this.directory = Objects.requireNonNull(directory);
    }

    public synchronized void save(UUID player, String name, List<UUID> pokemon) throws IOException {
        Objects.requireNonNull(player, "player");
        validateName(name);
        validateParty(pokemon);
        Properties data = read(player);
        String key = key(name);
        long count = data.stringPropertyNames().stream()
                .filter(k -> k.startsWith("preset."))
                .count();
        if (!data.containsKey(key) && count >= MAX_PRESETS) {
            throw new IllegalStateException("Maximum " + MAX_PRESETS + " presets per player");
        }
        data.setProperty(key, encode(pokemon));
        write(player, data);
    }

    public synchronized Optional<List<UUID>> load(UUID player, String name) throws IOException {
        Objects.requireNonNull(player, "player");
        validateName(name);
        String encoded = read(player).getProperty(key(name));
        if (encoded == null) return Optional.empty();
        return Optional.of(decode(encoded));
    }

    public synchronized Map<String, List<UUID>> list(UUID player) throws IOException {
        Objects.requireNonNull(player, "player");
        Map<String, List<UUID>> result = new TreeMap<>();
        Properties data = read(player);
        for (String k : data.stringPropertyNames()) {
            if (k.startsWith("preset.")) {
                result.put(k.substring("preset.".length()), decode(data.getProperty(k)));
            }
        }
        return Map.copyOf(result);
    }

    public synchronized boolean delete(UUID player, String name) throws IOException {
        Objects.requireNonNull(player, "player");
        validateName(name);
        Properties data = read(player);
        if (data.remove(key(name)) == null) return false;
        write(player, data);
        return true;
    }

    static void validateParty(List<UUID> pokemon) {
        Objects.requireNonNull(pokemon, "pokemon");
        if (pokemon.isEmpty() || pokemon.size() > 6 || pokemon.stream().anyMatch(Objects::isNull)
                || new HashSet<>(pokemon).size() != pokemon.size()) {
            throw new IllegalArgumentException("Preset must contain 1 to 6 unique Pokemon UUIDs");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 24 ||
                name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Preset name must be 1–24 printable characters");
        }
    }

    private static String key(String name) {
        return "preset." + name;
    }

    private static String encode(List<UUID> pokemon) {
        return String.join(",", pokemon.stream().map(UUID::toString).toList());
    }

    private static List<UUID> decode(String encoded) throws IOException {
        try {
            List<UUID> ids = new ArrayList<>();
            for (String s : encoded.split(",", -1)) ids.add(UUID.fromString(s));
            validateParty(ids);
            return List.copyOf(ids);
        } catch (RuntimeException ex) {
            throw new IOException("Invalid preset data", ex);
        }
    }

    private Path file(UUID player) {
        return directory.resolve(player + ".properties");
    }

    private Properties read(UUID player) throws IOException {
        Properties data = new Properties();
        Path path = file(player);
        if (Files.isRegularFile(path)) {
            try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                data.load(in);
            }
        }
        return data;
    }

    private void write(UUID player, Properties data) throws IOException {
        Files.createDirectories(directory);
        Path target = file(player);
        Path temp = Files.createTempFile(directory, "party-preset-", ".tmp");
        try {
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                data.store(out, "Champions Party Presets");
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
