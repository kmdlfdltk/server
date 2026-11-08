package dev.champions.pokebuilder;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class PokeBuilderPackets {
    private PokeBuilderPackets() {}

    /** action OPEN/SET, slot 0..5, field level/iv/ev/nature/friendship, value. */
    public record Request(String action, int slot, String field, String value)
            implements CustomPacketPayload {
        public static final Type<Request> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("champions_pokebuilder", "request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeUtf(p.action(), 16);
                buf.writeVarInt(p.slot());
                buf.writeUtf(p.field(), 32);
                buf.writeUtf(p.value(), 64);
            }, buf -> new Request(
                buf.readUtf(16), buf.readVarInt(), buf.readUtf(32), buf.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Reply(String kind, String json) implements CustomPacketPayload {
        public static final Type<Reply> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("champions_pokebuilder", "reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC =
            StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.kind(), 16); buf.writeUtf(p.json(), 32767); },
                buf -> new Reply(buf.readUtf(16), buf.readUtf(32767)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Reply.TYPE, Reply.CODEC);
    }
}
