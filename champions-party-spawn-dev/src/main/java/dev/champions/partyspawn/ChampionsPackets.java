package dev.champions.partyspawn;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class ChampionsPackets {
    private ChampionsPackets() {}

    public record Request(String action, String value) implements CustomPacketPayload {
        public static final Type<Request> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("champions_party_spawn", "request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
            StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.action(), 32); buf.writeUtf(p.value(), 64); },
                buf -> new Request(buf.readUtf(32), buf.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Reply(String kind, String json) implements CustomPacketPayload {
        public static final Type<Reply> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("champions_party_spawn", "reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC =
            StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.kind(), 32); buf.writeUtf(p.json(), 32767); },
                buf -> new Reply(buf.readUtf(32), buf.readUtf(32767)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Reply.TYPE, Reply.CODEC);
    }
}
