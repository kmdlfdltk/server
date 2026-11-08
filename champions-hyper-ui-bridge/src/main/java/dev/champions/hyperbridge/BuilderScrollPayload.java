package dev.champions.hyperbridge;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One mouse-wheel step to page the original builder's 6 Pokemon through 5 rows. */
public record BuilderScrollPayload(int direction) implements CustomPacketPayload {
    public static final Type<BuilderScrollPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("champions_hyper_ui_bridge","builder_scroll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuilderScrollPayload> CODEC =
        StreamCodec.of((buffer,packet)->buffer.writeVarInt(packet.direction()),
                       buffer->new BuilderScrollPayload(buffer.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE,CODEC);
    }
}
