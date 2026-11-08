package dev.champions.partyspawn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/**
 * A scope limited to a single /checkspawn execution on the main server thread.
 * The Cobblemon 1.8.1 command directly calls ServerPlayer.sendSystemMessage,
 * so changing CommandSource is not enough to capture its messages.
 */
public final class SpawnCapture {
    private static final ThreadLocal<Capture> ACTIVE = new ThreadLocal<>();

    public static final class Capture implements AutoCloseable {
        private final UUID player;
        private final Thread thread;
        private final List<String> lines = new ArrayList<>();
        private boolean closed;

        private Capture(UUID player) {
            this.player = player;
            this.thread = Thread.currentThread();
        }

        public boolean accept(UUID recipient, Component message) {
            if (closed || thread != Thread.currentThread() || !player.equals(recipient))
                return false;
            // Cobblemon 1.8.1 sends the entire comma-separated spawn pool
            // as one Component. Split it into individual display rows instead
            // of truncating the one huge chat message at 240 characters.
            for (String paragraph : message.getString().split("\\R")) {
                for (String piece : paragraph.split(",\\s*")) {
                    if (lines.size() >= 125) break;
                    String row = piece.trim();
                    if (!row.isBlank()) lines.add(row.substring(0, Math.min(190, row.length())));
                }
            }
            return true;
        }

        public List<String> lines() { return List.copyOf(lines); }

        @Override public void close() {
            if (closed) return;
            closed=true;
            if (ACTIVE.get() == this) ACTIVE.remove();
        }
    }

    public static Capture begin(UUID player) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Already capturing spawn output");
        Capture value = new Capture(player);
        ACTIVE.set(value);
        return value;
    }

    public static boolean intercept(UUID player, Component message) {
        Capture c = ACTIVE.get();
        return c != null && c.accept(player, message);
    }
}
