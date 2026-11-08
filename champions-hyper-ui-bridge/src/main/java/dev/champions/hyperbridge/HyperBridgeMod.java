package dev.champions.hyperbridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HyperBridgeMod implements ModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("ChampionsHyperBridge");
    private static final int PARTY_PROTOCOL=1;
    private static final int BUILDER_PROTOCOL=3;

    @Override
    public void onInitialize() {
        HyperWire.initialize();

        HyperWire.receive(HyperWire.PARTY+"$Request",(payload,player)->{
            try { BridgePresets.handle(player,payload); }
            catch(Throwable e) {
                LOG.error("Party preset request failed for {}",player.getGameProfile().getName(),e);
                BridgePresets.fail(player,payload,13,e.getMessage());
            }
        });
        HyperWire.receive(HyperWire.BUILDER+"$Apply",(payload,player)->{
            try { BridgeBuilder.handleApply(player,payload); }
            catch(Throwable e) {
                LOG.error("PokeBuilder Apply failed for {}",player.getGameProfile().getName(),e);
                BridgeBuilder.fail(player,payload,e.getMessage());
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->{
            dispatcher.register(Commands.literal("하이퍼파티").executes(ctx->{
                openParty(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("hyperparty").executes(ctx->{
                openParty(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("하이퍼빌더").executes(ctx->{
                openBuilder(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("hyperbuilder").executes(ctx->{
                openBuilder(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("하이퍼브리지").executes(ctx->{
                ServerPlayer p=ctx.getSource().getPlayerOrException();
                p.sendSystemMessage(Component.literal(
                    "[Champions Bridge] Party Net: "+HyperWire.ready(HyperWire.PARTY)
                    +" | Builder Net: "+HyperWire.ready(HyperWire.BUILDER)
                    +" | 원본 클라이언트 UI가 설치되어 있어야 열립니다."));
                return 1;
            }));
        });

        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->
            server.execute(() -> {
                // Enables the original PC / summary preset button on client.
                try { HyperWire.sendParty(handler.player,"Status",PARTY_PROTOCOL,true); }
                catch(Exception error){ LOG.warn("Party preset status handshake failed",error); }
            }));
        LOG.info("Champions Hyper UI Bridge initialized (original UI, no copied assets).");
    }

    public static void openParty(ServerPlayer player) {
        try {
            HyperWire.sendParty(player,"Status",PARTY_PROTOCOL,true);
            if (!HyperWire.sendParty(player,"Open",PARTY_PROTOCOL)) {
                player.sendSystemMessage(Component.literal(
                    "하이퍼 파티 UI를 받을 수 없습니다. 클라이언트에 HyperPartyPreset 0.1.29가 설치되어 있는지 확인하세요."));
                return;
            }
            // The original screen also sends SYNC on open; proactively send
            // state to populate 10 slots even if its UI response is delayed.
            BridgePresets.sendState(player,0,0,0);
        } catch(Exception e) {
            LOG.error("Could not open original HyperPartyPreset",e);
            player.sendSystemMessage(Component.literal("하이퍼 파티 화면 열기 실패: "+e.getMessage()));
        }
    }

    public static void openBuilder(ServerPlayer player) {
        try {
            if (!BridgeBuilder.sendView(player,true)) {
                player.sendSystemMessage(Component.literal(
                    "하이퍼 포켓빌더 UI를 받을 수 없습니다. 클라이언트에 HyperPokeBuilderUI 1.0.42가 필요합니다."));
            }
        } catch(Exception e) {
            LOG.error("Could not open original HyperPokeBuilderUI",e);
            player.sendSystemMessage(Component.literal("하이퍼 포켓빌더 화면 열기 실패: "+e.getMessage()));
        }
    }
}
