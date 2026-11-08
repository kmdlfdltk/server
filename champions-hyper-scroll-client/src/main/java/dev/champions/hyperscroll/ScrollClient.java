package dev.champions.hyperscroll;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Does not modify the original Hyper UI; sends 5-row page shifts to the
 * Champions server bridge when scrolling the left-hand Pokémon list.
 */
public final class ScrollClient implements ClientModInitializer {
    private static long lastScroll;
    @Override public void onInitializeClient(){
        PayloadTypeRegistry.playC2S().register(ScrollPayload.TYPE,ScrollPayload.CODEC);
    }
    public static boolean intercept(double rawWheelY) {
        Minecraft mc=Minecraft.getInstance();
        Screen screen=mc.screen;
        if(screen==null || !screen.getClass().getSimpleName().equals("PokeBuilderScreen")) return false;
        if(rawWheelY==0 || !ClientPlayNetworking.canSend(ScrollPayload.TYPE)) return false;

        double width=Math.max(1,mc.getWindow().getScreenWidth());
        double height=Math.max(1,mc.getWindow().getScreenHeight());
        // Original tablet's left party strip occupies this screen region.
        double x=mc.mouseHandler.xpos()/width;
        double y=mc.mouseHandler.ypos()/height;
        if(x<0.10 || x>0.42 || y<0.12 || y>0.95) return false;

        // Prevent sending dozens of packets from a high-resolution wheel.
        long now=System.nanoTime();
        if(now-lastScroll<110_000_000L) return true;
        lastScroll=now;
        ClientPlayNetworking.send(new ScrollPayload(rawWheelY<0?1:-1));
        return true;
    }
}
