package dev.champions.partyspawn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/** Standalone working fallback screens; no proprietary Hyper UI classes/assets are bundled. */
public final class ChampionsClient implements ClientModInitializer {
    private static KeyMapping presetKey, spawnKey;
    private static final String[] BUCKETS = {"common","uncommon","rare","ultra-rare"};

    @Override public void onInitializeClient() {
        presetKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.champions_party_spawn.presets", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.champions_party_spawn"));
        spawnKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.champions_party_spawn.spawns", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.champions_party_spawn"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (presetKey.consumeClick()) request("OPEN", "");
            while (spawnKey.consumeClick()) request("SPAWN", "common");
        });
        ClientPlayNetworking.registerGlobalReceiver(ChampionsPackets.Reply.TYPE, (payload, context) ->
            context.client().execute(() -> {
                try {
                    JsonObject object = JsonParser.parseString(payload.json()).getAsJsonObject();
                    Minecraft mc = context.client();
                    switch (payload.kind()) {
                        case "PRESETS" -> mc.setScreen(new PresetScreen(object));
                        case "SPAWNS" -> mc.setScreen(new SpawnScreen(object));
                        case "ERROR" -> {
                            if (mc.player != null) mc.player.displayClientMessage(
                                Component.literal(object.has("message") ? object.get("message").getAsString() :
                                    "서버 처리 오류"), false);
                        }
                    }
                } catch (Exception e) {
                    ChampionsMod.LOGGER.error("Bad server reply", e);
                }
            }));
    }

    public static void request(String action, String value) {
        if (Minecraft.getInstance().player != null &&
            ClientPlayNetworking.canSend(ChampionsPackets.Request.TYPE)) {
            ClientPlayNetworking.send(new ChampionsPackets.Request(action, value));
        }
    }

    private static final class PresetScreen extends Screen {
        private final JsonObject data;
        private EditBox nameBox;
        private String selectedName = "";
        PresetScreen(JsonObject data) {
            super(Component.literal("Champions | 파티 프리셋"));
            this.data = data;
        }
        @Override protected void init() {
            int x = width/2-150;
            int y = height/2-96;
            addRenderableWidget(Button.builder(Component.literal("새로고침"),
                    b -> request("OPEN", "")).bounds(x+202, y+10, 96,20).build());
            nameBox = new EditBox(font,x+1,y+175,194,20,Component.literal("프리셋 이름"));
            nameBox.setMaxLength(24);
            nameBox.setHint(Component.literal("프리셋 이름을 입력"));
            addRenderableWidget(nameBox);
            addRenderableWidget(Button.builder(Component.literal("저장"),
                b -> { if (!nameBox.getValue().isBlank()) request("SAVE",nameBox.getValue()); })
                .bounds(x+200,y+175,98,20).build());
            addRenderableWidget(Button.builder(Component.literal("불러오기"),
                b -> { if (!selectedName.isBlank()) request("LOAD",selectedName); })
                .bounds(x,y+201,95,20).build());
            addRenderableWidget(Button.builder(Component.literal("삭제"),
                b -> { if (!selectedName.isBlank()) request("DELETE",selectedName); })
                .bounds(x+101,y+201,95,20).build());
            addRenderableWidget(Button.builder(Component.literal("닫기"),
                b -> onClose()).bounds(x+202,y+201,96,20).build());

            JsonArray arr = data.has("presets") ? data.getAsJsonArray("presets") : new JsonArray();
            int n=0;
            for (JsonElement elem : arr) {
                if (n >= 9) break;
                JsonObject preset = elem.getAsJsonObject();
                String name = preset.get("name").getAsString();
                int count = preset.get("count").getAsInt();
                int row = n++;
                addRenderableWidget(Button.builder(Component.literal(name + " ("+count+"/6)"),
                    b -> { selectedName=name; nameBox.setValue(name); })
                    .bounds(x+2+(row%3)*100,y+51+(row/3)*35,96,30).build());
            }
        }

        @Override public void render(GuiGraphics g,int mx,int my,float dt) {
            renderBackground(g,mx,my,dt);
            int x=width/2-150, y=height/2-96;
            g.drawCenteredString(font,title,width/2,y-13,0xFFFFFF);
            g.drawString(font,"현재 파티",x,y+1,0xEEEEEE);
            JsonArray party=data.has("party")?data.getAsJsonArray("party"):new JsonArray();
            StringBuilder members=new StringBuilder();
            for (JsonElement elem:party) {
                if (!members.isEmpty()) members.append(" · ");
                members.append(elem.getAsString());
            }
            String visible = members.toString();
            if (visible.length()>45) visible=visible.substring(0,44)+"…";
            g.drawString(font,visible.isEmpty()?"포켓몬 없음":visible,x,y+25,0xAAAAAA);
            String notice=data.has("message")?data.get("message").getAsString():"";
            if (!notice.isEmpty()) g.drawCenteredString(font,notice,width/2,y+157,0x77DD77);
            super.render(g,mx,my,dt);
        }
    }

    private static final class SpawnScreen extends Screen {
        private final JsonObject data;
        private final List<String> lines = new ArrayList<>();
        private final String bucket;
        private int page=0;
        private static final int PAGE_SIZE=10;
        SpawnScreen(JsonObject data) {
            super(Component.literal("Champions | 주변 출현 포켓몬"));
            this.data=data;
            bucket=data.has("bucket")?data.get("bucket").getAsString():"common";
            if(data.has("lines")) for(JsonElement element:data.getAsJsonArray("lines"))
                lines.add(element.getAsString());
        }
        @Override protected void init() {
            int x=width/2-160, y=height/2-105;
            for(int i=0;i<BUCKETS.length;i++){
                String b=BUCKETS[i];
                addRenderableWidget(Button.builder(Component.literal(b),
                    button -> request("SPAWN",b))
                    .bounds(x+i*81,y+24,78,20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("이전"),
                b -> page=Math.max(0,page-1)).bounds(x+25,y+205,76,20).build());
            addRenderableWidget(Button.builder(Component.literal("다음"),
                b -> page=Math.min(Math.max(0,(lines.size()-1)/PAGE_SIZE),page+1))
                .bounds(x+109,y+205,76,20).build());
            addRenderableWidget(Button.builder(Component.literal("닫기"),
                b -> onClose()).bounds(x+209,y+205,90,20).build());
        }
        @Override public void render(GuiGraphics g,int mx,int my,float dt) {
            renderBackground(g,mx,my,dt);
            int x=width/2-160,y=height/2-105;
            g.drawCenteredString(font,title,width/2,y+2,0xFFFFFF);
            g.drawString(font,"희귀도: "+bucket+" | 차원: "+
                (data.has("world")?data.get("world").getAsString():"?"),x,y+51,0xAAAAAA);
            for (int i=0;i<PAGE_SIZE;i++) {
                int at=page*PAGE_SIZE+i;
                if(at>=lines.size())break;
                String entry=lines.get(at);
                if(entry.length()>62)entry=entry.substring(0,61)+"…";
                g.drawString(font,entry,x+3,y+68+i*12,0xE0E0E0);
            }
            g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(lines.size()+PAGE_SIZE-1)/PAGE_SIZE),
                width/2,y+190,0xCCCCCC);
            super.render(g,mx,my,dt);
        }
    }
}
