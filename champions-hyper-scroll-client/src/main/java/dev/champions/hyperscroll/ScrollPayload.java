package dev.champions.hyperscroll;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ScrollPayload(int direction) implements CustomPacketPayload {
    public static final Type<ScrollPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("champions_hyper_ui_bridge","builder_scroll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScrollPayload> CODEC =
        StreamCodec.of((buf,p)->buf.writeVarInt(p.direction()),
                       buf->new ScrollPayload(buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
