package dev.champions.partyspawn;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

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

import org.lwjgl.glfw.GLFW;

public final class ChampionsClient implements ClientModInitializer {
    private static KeyMapping presetKey;
    private static KeyMapping spawnKey;
    private static final String[] BUCKETS = {"common", "uncommon", "rare", "ultra-rare"};

    @Override
    public void onInitializeClient() {
        presetKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.champions_party_spawn.presets", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P, "key.categories.champions_party_spawn"));
        spawnKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.champions_party_spawn.spawns", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K, "key.categories.champions_party_spawn"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (presetKey.consumeClick()) request("OPEN", "");
            while (spawnKey.consumeClick()) openSpawns("common");
        });
        ClientPlayNetworking.registerGlobalReceiver(ChampionsPackets.Reply.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    try {
                        JsonObject object = JsonParser.parseString(payload.json()).getAsJsonObject();
                        Minecraft mc = context.client();
                        switch (payload.kind()) {
                            case "PRESETS" -> {
                                String keep = mc.screen instanceof PresetScreen s ? s.selectedName : "";
                                mc.setScreen(new PresetScreen(object, keep));
                            }
                            case "SPAWNS" -> {
                                if (mc.screen instanceof SpawnScreen s) s.accept(object);
                                else mc.setScreen(new SpawnScreen(object));
                            }
                            case "ERROR" -> {
                                String msg = object.has("message")
                                        ? object.get("message").getAsString() : "요청 처리 오류";
                                if (mc.screen instanceof SpawnScreen s) s.fail(msg);
                                else if (mc.player != null) mc.player.displayClientMessage(Component.literal(msg), false);
                            }
                        }
                    } catch (Exception e) {
                        ChampionsMod.LOGGER.error("Could not parse Champions server reply", e);
                    }
                }));
    }

    public static boolean request(String action, String value) {
        if (Minecraft.getInstance().player == null
                || !ClientPlayNetworking.canSend(ChampionsPackets.Request.TYPE)) return false;
        ClientPlayNetworking.send(new ChampionsPackets.Request(action, value));
        return true;
    }

    private static void openSpawns(String bucket) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof SpawnScreen s) s.waitFor(bucket);
        else client.setScreen(new SpawnScreen(bucket));
        if (!request("SPAWN", bucket) && client.screen instanceof SpawnScreen s)
            s.fail("서버에 Champions Party & Spawn이 설치되어 있지 않거나 통신할 수 없습니다.");
    }

    private static JsonArray arr(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonArray())
            return obj.getAsJsonArray(key);
        return new JsonArray();
    }
    private static String str(JsonObject obj, String key, String fallback) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString() : fallback;
    }
    private static int intVal(JsonObject obj, String key, int fallback) {
        return obj != null && obj.has(key) ? obj.get(key).getAsInt() : fallback;
    }

    private static final class PresetScreen extends Screen {
        private final JsonObject data;
        private final String initialSelectedName;
        private String selectedName;
        private EditBox nameBox;
        private int x, y, w, h;

        PresetScreen(JsonObject data, String selectedName) {
            super(Component.literal("CHAMPIONS  |  PARTY PRESETS"));
            this.data = data;
            this.initialSelectedName = selectedName;
            this.selectedName = selectedName;
        }

        @Override
        protected void init() {
            w = Math.min(width - 18, 510);
            h = Math.min(height - 16, 315);
            x = (width-w)/2;
            y = (height-h)/2;

            int listWidth = Math.max(115, w/3-17);
            addRenderableWidget(Button.builder(Component.literal("현재 파티"),
                b -> { selectedName = ""; nameBox.setValue(""); })
                .bounds(x+9, y+38, listWidth, 20).build());

            int idx = 0;
            for (JsonElement elem : arr(data, "presets")) {
                if (!elem.isJsonObject() || idx >= 10) break;
                JsonObject p = elem.getAsJsonObject();
                String name = str(p, "name", "unnamed");
                int count = intVal(p, "count", 0);
                int row = idx++;
                int yy = y+62+row*20;
                if (yy+19 > y+h-38) break;
                addRenderableWidget(Button.builder(Component.literal(name+"  "+count+"/6"),
                    b -> { selectedName = name; nameBox.setValue(name); })
                    .bounds(x+9, yy, listWidth, 19).build());
            }

            int bottom = y+h-30;
            int start = x+9;
            int inputW = Math.max(88, w-310);
            nameBox = new EditBox(font, start, bottom, inputW, 20,
                                  Component.literal("프리셋 이름"));
            nameBox.setMaxLength(24);
            nameBox.setHint(Component.literal("프리셋 이름"));
            addRenderableWidget(nameBox);
            if (!initialSelectedName.isBlank()) nameBox.setValue(initialSelectedName);

            int bx = start+inputW+5;
            addRenderableWidget(Button.builder(Component.literal("저장"),
                b -> {
                    String name = nameBox.getValue().trim();
                    if (!name.isBlank()) request("SAVE", name);
                }).bounds(bx,bottom,58,20).build());
            bx += 61;
            addRenderableWidget(Button.builder(Component.literal("불러오기"),
                b -> { if (!selectedName.isBlank()) request("LOAD",selectedName); })
                .bounds(bx,bottom,65,20).build());
            bx += 68;
            addRenderableWidget(Button.builder(Component.literal("삭제"),
                b -> { if (!selectedName.isBlank()) request("DELETE",selectedName); })
                .bounds(bx,bottom,55,20).build());
            bx += 58;
            addRenderableWidget(Button.builder(Component.literal("닫기"),
                b -> onClose()).bounds(bx,bottom,48,20).build());
        }

        private JsonArray selectedParty() {
            if (selectedName.isBlank()) return arr(data, "party");
            for (JsonElement element : arr(data, "presets")) {
                if (element.isJsonObject()) {
                    JsonObject preset = element.getAsJsonObject();
                    if (selectedName.equals(str(preset,"name",""))) return arr(preset,"pokemon");
                }
            }
            return new JsonArray();
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            renderBackground(g,mx,my,dt);
            g.fill(x,y,x+w,y+h,0xF2182029);
            g.fill(x,y,x+w,y+30,0xFF244250);
            g.fill(x,y,x+w,y+2,0xFF6BA5C5);
            g.drawString(font,"CHAMPIONS  /  PARTY PRESETS",x+11,y+11,0xFFFFFFFF);
            g.drawString(font,"선택한 파티의 포켓몬을 확인하고 저장·불러오기",x+11,y+28,0xFFB4C6D0);

            int lw = Math.max(115, w/3-17);
            g.fill(x+6,y+36,x+lw+12,y+h-37,0x9915212B);

            int startX = x+lw+18;
            int areaW = w-(startX-x)-9;
            g.drawString(font,selectedName.isBlank()?"현재 파티":selectedName,
                startX+3,y+43,0xFFDFEAF0);

            JsonArray members = selectedParty();
            int slotGap = 5;
            int slotW = Math.max(55, (areaW-2*slotGap)/3);
            int slotH = Math.max(65, (h-106)/2);

            for (int i=0;i<6;i++) {
                int tx=startX+(i%3)*(slotW+slotGap);
                int ty=y+60+(i/3)*(slotH+3);
                g.fill(tx,ty,tx+slotW,ty+slotH,0xFF263845);
                g.fill(tx,ty,tx+slotW,ty+1,0xFF496374);
                if (i>=members.size() || !members.get(i).isJsonObject()) {
                    g.drawCenteredString(font,"빈 슬롯",tx+slotW/2,ty+slotH/2,0xFF617786);
                    continue;
                }
                JsonObject pokemon=members.get(i).getAsJsonObject();
                String species=str(pokemon,"species","missing");
                if ("missing".equals(species)) {
                    g.drawCenteredString(font,"없어진 포켓몬",tx+slotW/2,ty+slotH/2,0xFFFF8888);
                    continue;
                }
                String form=str(pokemon,"form","");
                boolean shiny=pokemon.has("shiny") && pokemon.get("shiny").getAsBoolean();

                // The installed Cobblemon model/texture renderer draws the real Pokémon.
                int right=tx+slotW, bottom=ty+slotH-18;
                g.enableScissor(tx+2,ty+2,right-2,bottom);
                PokemonPortraits.render(g,species,form,shiny,tx+slotW/2,ty+14,0.75f);
                g.disableScissor();

                g.drawCenteredString(font,str(pokemon,"name",species),
                    tx+slotW/2,ty+slotH-20,0xFFEDF6FF);
                g.drawCenteredString(font,"Lv."+intVal(pokemon,"level",1),
                    tx+slotW/2,ty+slotH-9,0xFFB5D0E2);
            }
            String notice=str(data,"message","");
            if (!notice.isBlank())
                g.drawString(font,notice,startX+3,y+h-47,0xFF85E5A8);
            super.render(g,mx,my,dt);
        }
    }

    private static final class SpawnScreen extends Screen {
        private JsonObject data;
        private String bucket;
        private String error="";
        private List<String> lines = new ArrayList<>();
        private int page;
        private static final int PAGE_SIZE=10;
        private int x, y;

        SpawnScreen(String bucket) {
            super(Component.literal("CHAMPIONS  |  주변 포켓몬 출현 목록"));
            waitFor(bucket);
        }

        SpawnScreen(JsonObject data) {
            this(str(data,"bucket","common"));
            accept(data);
        }

        void waitFor(String bucket) {
            this.bucket=bucket;
            lines.clear();
            lines.add("서버에서 현재 위치 스폰 목록을 조회하는 중...");
            error="";
            page=0;
        }
        void accept(JsonObject response) {
            this.data=response;
            this.bucket=str(response,"bucket",bucket);
            this.lines=new ArrayList<>();
            for (JsonElement line:arr(response,"lines")) lines.add(line.getAsString());
            if(lines.isEmpty()) lines.add("조회 결과가 없습니다.");
            page=0;
            error="";
        }
        void fail(String message) {
            this.error=message;
            this.lines=new ArrayList<>(List.of("요청 오류: "+message));
        }

        @Override protected void init() {
            x=(width-344)/2;
            y=(height-249)/2;
            for (int i=0;i<BUCKETS.length;i++) {
                String b=BUCKETS[i];
                addRenderableWidget(Button.builder(Component.literal(b),
                    btn -> openSpawns(b)).bounds(x+8+i*84,y+35,81,20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("이전"),
                b -> page=Math.max(0,page-1)).bounds(x+17,y+213,78,20).build());
            addRenderableWidget(Button.builder(Component.literal("다음"),
                b -> page=Math.min(Math.max(0,(lines.size()-1)/PAGE_SIZE),page+1))
                .bounds(x+104,y+213,78,20).build());
            addRenderableWidget(Button.builder(Component.literal("재조회"),
                b -> openSpawns(bucket)).bounds(x+193,y+213,68,20).build());
            addRenderableWidget(Button.builder(Component.literal("닫기"),
                b -> onClose()).bounds(x+269,y+213,68,20).build());
        }

        @Override public void render(GuiGraphics g,int mx,int my,float dt) {
            renderBackground(g,mx,my,dt);
            g.fill(x,y,x+344,y+249,0xF2182029);
            g.fill(x,y,x+344,y+25,0xFF244250);
            g.fill(x,y,x+344,y+2,0xFF6BA5C5);
            g.drawString(font,"CHAMPIONS  /  SPAWN VIEWER",x+10,y+9,0xFFFFFFFF);
            String world=data==null?"?":str(data,"world","?");
            String biome=data==null?"?":str(data,"biome","?");
            g.drawString(font,"희귀도: "+bucket,x+9,y+63,0xFF97C7DF);
            g.drawString(font,"월드: "+world,x+9,y+76,0xFFBFCED6);
            g.drawString(font,"바이옴: "+biome,x+9,y+88,0xFFBFCED6);
            int first=page*PAGE_SIZE;
            for (int i=0;i<PAGE_SIZE && first+i<lines.size();i++) {
                String line=lines.get(first+i);
                if (line.length()>71) line=line.substring(0,70)+"…";
                g.drawString(font,line,x+11,y+105+i*10,0xFFE1EDF2);
            }
            g.drawString(font,(page+1)+" / "+Math.max(1,(lines.size()+PAGE_SIZE-1)/PAGE_SIZE),
                x+306,y+197,0xFFCCD2D9);
            if (!error.isBlank()) g.drawString(font,"서버 응답 오류",x+9,y+197,0xFFFF8585);
            super.render(g,mx,my,dt);
        }
    }
}
