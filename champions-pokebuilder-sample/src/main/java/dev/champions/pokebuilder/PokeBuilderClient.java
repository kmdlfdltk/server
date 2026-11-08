package dev.champions.pokebuilder;

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

import java.util.ArrayList;
import java.util.List;

/**
 * An original standalone tablet-style UI, referencing the user's screenshot
 * for general information architecture but containing no HyperNetwork code.
 */
public final class PokeBuilderClient implements ClientModInitializer {
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.champions_pokebuilder.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, "key.categories.champions_pokebuilder"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) send("OPEN",0,"","");
        });

        ClientPlayNetworking.registerGlobalReceiver(PokeBuilderPackets.Reply.TYPE,
            (response, context) -> context.client().execute(() -> {
                try {
                    JsonObject data = JsonParser.parseString(response.json()).getAsJsonObject();
                    if ("STATE".equals(response.kind())) {
                        int tab=0;
                        if (context.client().screen instanceof BuilderScreen prior) tab=prior.tab;
                        context.client().setScreen(new BuilderScreen(data,tab));
                    } else if ("ERROR".equals(response.kind())) {
                        String msg=string(data,"message","처리 오류");
                        if (context.client().screen instanceof BuilderScreen screen)
                            screen.showError(msg);
                        else if (context.client().player != null)
                            context.client().player.displayClientMessage(
                                Component.literal("포켓빌더: "+msg),false);
                    }
                } catch (Exception error) {
                    PokeBuilderMod.LOGGER.error("PokeBuilder client response parsing failed",error);
                }
            }));
    }

    private static void send(String action,int slot,String field,String value) {
        if (Minecraft.getInstance().player != null
            && ClientPlayNetworking.canSend(PokeBuilderPackets.Request.TYPE)) {
            ClientPlayNetworking.send(new PokeBuilderPackets.Request(action,slot,field,value));
        } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                Component.literal("서버에 Champions PokeBuilder가 설치되어 있는지 확인하세요"),false);
        }
    }

    private static JsonArray array(JsonObject obj,String name) {
        return obj != null && obj.has(name) && obj.get(name).isJsonArray()
            ? obj.getAsJsonArray(name) : new JsonArray();
    }
    private static String string(JsonObject obj,String name,String fallback) {
        return obj != null && obj.has(name) && !obj.get(name).isJsonNull()
            ? obj.get(name).getAsString() : fallback;
    }
    private static int number(JsonObject obj,String name,int fallback) {
        return obj != null && obj.has(name) && obj.get(name).isJsonPrimitive()
            ? obj.get(name).getAsInt() : fallback;
    }
    private static boolean bool(JsonObject obj,String name,boolean fallback) {
        return obj != null && obj.has(name) && obj.get(name).isJsonPrimitive()
            ? obj.get(name).getAsBoolean() : fallback;
    }

    /** The pill and card controls are intentionally original assets, not copied Hyper UI. */
    private static final class FlatButton extends Button {
        final boolean selected;
        final boolean left;
        FlatButton(int x,int y,int w,int h,String text,boolean selected,boolean left,OnPress action) {
            super(x,y,w,h,Component.literal(text),action,Button.DEFAULT_NARRATION);
            this.selected=selected;
            this.left=left;
        }
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float dt) {
            int bg=selected?0xFF594A48:(isHoveredOrFocused()?0xFF494044:0xFF302D31);
            if (!active) bg=0xFF242326;
            g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),bg);
            if (selected) g.fill(getX(),getY(),getX()+3,getY()+getHeight(),0xFFEFA86C);
            int color=active?0xFFF3EDF0:0xFF847F85;
            if (left) g.drawString(Minecraft.getInstance().font,getMessage(),
                getX()+39,getY()+(getHeight()-8)/2,color,false);
            else g.drawCenteredString(Minecraft.getInstance().font,getMessage(),
                getX()+getWidth()/2,getY()+(getHeight()-8)/2,color);
        }
    }

    private static final class BuilderScreen extends Screen {
        private static final String[] TABS={"레벨","개체값","노력치","성격","친밀도"};
        private static final String[] KEYS={"hp","atk","def","spa","spd","spe"};
        private static final String[] LABELS={"HP","공격","방어","특공","특방","스피드"};
        private static final String[] NATURE_IDS={
            "hardy","lonely","brave","adamant","naughty",
            "bold","docile","relaxed","impish","lax",
            "timid","hasty","serious","jolly","naive",
            "modest","mild","quiet","bashful","rash",
            "calm","gentle","sassy","careful","quirky"
        };
        private static final String[] NATURE_KR={
            "노력","외로움","용감","고집","개구쟁이",
            "대담","온순","무사태평","장난꾸러기","촐랑",
            "겁쟁이","성급","성실","명랑","천진난만",
            "조심","의젓","냉정","수줍음","덜렁",
            "차분","얌전","건방","신중","변덕"
        };
        private final JsonObject data;
        private final List<EditBox> statInputs=new ArrayList<>();
        private int tab;
        private int x,y,w,h,leftWidth,rightWidth,contentX,contentW,rightX;
        private int selectedSlot;
        private EditBox mainInput;
        private String error="";

        BuilderScreen(JsonObject data,int tab) {
            super(Component.literal("CHAMPIONS  /  POKÉBUILDER"));
            this.data=data;
            this.tab=Math.max(0,Math.min(4,tab));
            this.selectedSlot=number(data,"selected",0);
        }

        private JsonObject pokemon(int slot) {
            JsonArray arr=array(data,"party");
            if (slot<0 || slot>=arr.size() || !arr.get(slot).isJsonObject()) return null;
            JsonObject p=arr.get(slot).getAsJsonObject();
            return bool(p,"empty",true)?null:p;
        }

        private FlatButton button(int px,int py,int pw,int ph,String text,
                                  boolean active,boolean left,Button.OnPress callback) {
            FlatButton b=new FlatButton(px,py,pw,ph,text,active,left,callback);
            addRenderableWidget(b);
            return b;
        }

        private void switchTab(int next) {
            Minecraft.getInstance().setScreen(new BuilderScreen(data,next));
        }
        private void edit(String field,String value) {
            if (!bool(data,"canEdit",false)) {
                showError("샘플 편집은 OP 2레벨 이상만 가능합니다");
                return;
            }
            send("SET",selectedSlot,field,value);
        }
        void showError(String message) { error=message; }

        private EditBox numberBox(int xx,int yy,int ww,String original) {
            EditBox edit=new EditBox(font,xx,yy,ww,20,Component.literal("수정 값"));
            edit.setMaxLength(3);
            edit.setFilter(s -> s.matches("[0-9]{0,3}"));
            edit.setValue(original);
            addRenderableWidget(edit);
            return edit;
        }

        private void buildLevel(JsonObject p) {
            int current=number(p,"level",1);
            int max=number(data,"maxLevel",100);
            int rowY=y+143;
            mainInput=numberBox(contentX+92,rowY+36,70,Integer.toString(current));
            button(contentX+10,rowY+36,76,20,"-10",false,false,b -> adjust(-10,1,max));
            button(contentX+169,rowY+36,76,20,"+10",false,false,b -> adjust(10,1,max));
            button(contentX+10,rowY+69,76,20,"-1",false,false,b -> adjust(-1,1,max));
            button(contentX+169,rowY+69,76,20,"+1",false,false,b -> adjust(1,1,max));
            button(contentX+10,rowY+112,Math.min(contentW-20,240),25,"레벨 적용",true,false,
                b -> edit("level",mainInput.getValue()));
        }
        private void adjust(int delta,int min,int max) {
            if (mainInput==null) return;
            try {
                int num=Integer.parseInt(mainInput.getValue());
                mainInput.setValue(Integer.toString(Math.max(min,Math.min(max,num+delta))));
            } catch (NumberFormatException err) { mainInput.setValue(Integer.toString(min)); }
        }

        private void buildStats(JsonObject p,boolean iv) {
            JsonArray stats=array(p,iv?"ivs":"evs");
            statInputs.clear();
            int top=y+135;
            int gap=Math.max(24, Math.min(33,(h-188)/6));
            int inpX=contentX+Math.max(65,contentW/3);
            int btnX=inpX+55;
            int max=iv?31:252;
            for(int i=0;i<6;i++){
                int val=i<stats.size()?stats.get(i).getAsInt():0;
                int yy=top+i*gap;
                EditBox input=numberBox(inpX,yy,46,Integer.toString(val));
                statInputs.add(input);
                final String field=(iv?"iv.":"ev.")+KEYS[i];
                final EditBox reference=input;
                if (contentW>315)
                    button(btnX+3,yy,45,20,"MAX",false,false,b -> reference.setValue(Integer.toString(max)));
                button(Math.min(contentX+contentW-56,btnX+54),yy,52,20,"적용",false,false,
                    b -> edit(field,reference.getValue()));
            }
        }

        private void buildNatures(JsonObject p) {
            String active=string(p,"nature","hardy");
            int gridW=contentW-16;
            int bw=Math.max(42,(gridW-16)/5);
            int bh=23;
            int top=y+138;
            for(int i=0;i<NATURE_IDS.length;i++){
                final String nature=NATURE_IDS[i];
                int ix=i%5, iy=i/5;
                int xx=contentX+7+ix*(bw+3), yy=top+iy*29;
                button(xx,yy,bw,bh,NATURE_KR[i],nature.equals(active),false,
                    b -> edit("nature",nature));
            }
        }

        private void buildFriendship(JsonObject p) {
            int current=number(p,"friendship",0);
            int top=y+170;
            mainInput=numberBox(contentX+92,top,80,Integer.toString(current));
            button(contentX+12,top,73,20,"-10",false,false,b -> adjust(-10,0,255));
            button(contentX+182,top,73,20,"+10",false,false,b -> adjust(10,0,255));
            button(contentX+12,top+41,Math.min(contentW-24,245),26,
                   "친밀도 적용",true,false,b -> edit("friendship",mainInput.getValue()));
        }

        @Override protected void init() {
            w=Math.min(width-16,965);
            h=Math.min(height-16,565);
            x=(width-w)/2;
            y=(height-h)/2;
            leftWidth=Math.max(127,Math.min(180,w/5));
            rightWidth=Math.max(144,Math.min(205,w/4));
            contentX=x+leftWidth+14;
            rightX=x+w-rightWidth-8;
            contentW=rightX-contentX-9;

            int top=y+83;
            int rowHeight=Math.max(26,Math.min(51,(h-132)/6));
            for(int i=0;i<6;i++){
                int slot=i;
                JsonObject mon=pokemon(i);
                String name=mon==null?"비어 있음":string(mon,"displayName","?");
                String prefix=name.length()>9?name.substring(0,8)+"…":name;
                FlatButton b=button(x+15,top+i*rowHeight,leftWidth-23,rowHeight-4,
                    prefix,selectedSlot==i,true,
                    bt -> send("OPEN",slot,"",""));
                b.active=mon!=null;
            }

            int tabW=Math.max(38,contentW/TABS.length-2);
            for(int i=0;i<TABS.length;i++){
                int activeTab=i;
                button(contentX+2+i*(tabW+1),y+76,tabW,23,
                    TABS[i],tab==i,false,b -> switchTab(activeTab));
            }

            JsonObject mon=pokemon(selectedSlot);
            if(mon==null)return;
            switch(tab){
                case 0 -> buildLevel(mon);
                case 1 -> buildStats(mon,true);
                case 2 -> buildStats(mon,false);
                case 3 -> buildNatures(mon);
                case 4 -> buildFriendship(mon);
                default -> {}
            }
            button(rightX+13,y+h-64,rightWidth-27,29,
                bool(data,"canEdit",false)?"새로고침":"보기 전용 · OP 필요",
                false,false,b -> send("OPEN",selectedSlot,"",""));
        }

        @Override public void render(GuiGraphics g,int mx,int my,float dt) {
            renderBackground(g,mx,my,dt);
            // Dark tablet shell, large contrast blocks and warm accents.
            g.fill(x-3,y-3,x+w+3,y+h+3,0xFF090C10);
            g.fill(x,y,x+w,y+h,0xF4221F22);
            g.fill(x+4,y+4,x+w-4,y+58,0xFF332B2B);
            g.fill(x+12,y+72,x+leftWidth-2,y+h-17,0xFF1D1C1E);
            g.fill(contentX-5,y+110,rightX-10,y+h-21,0xFF191A1C);
            g.fill(rightX,y+72,x+w-10,y+h-17,0xFF201C20);
            g.fill(x+12,y+59,x+w-12,y+60,0xFF71544B);

            g.drawString(font,"CHAMPIONS",x+17,y+17,0xFFFFD8B2);
            g.drawString(font,"포켓빌더",x+17,y+34,0xFFFAF0EE);
            g.drawString(font,"POKÉBUILDER  |  육성 편집",contentX,y+24,0xFFF2E5E3);
            g.drawString(font,"소유한 포켓몬을 선택하고 능력치를 수정하세요",contentX,y+44,0xFFB0A0A0);
            g.drawString(font,"파티",x+18,y+65,0xFFD6C2B9);

            JsonObject mon=pokemon(selectedSlot);
            String notice=error.isBlank()?string(data,"message",""):error;
            if (!notice.isBlank()){
                String brief=notice.length()>55?notice.substring(0,54)+"…":notice;
                g.drawString(font,brief,contentX,y+h-31,error.isBlank()?0xFF9FE0B2:0xFFFF9999);
            }

            if(mon!=null){
                String display=string(mon,"displayName","포켓몬");
                String nature=string(mon,"nature","?");
                int lev=number(mon,"level",1);
                g.drawString(font,"선택: "+display,contentX+7,y+118,0xFFFFDFBF);

                if (tab==0){
                    g.drawString(font,"레벨 변경",contentX+14,y+143,0xFFDADADA);
                    g.drawString(font,"현재  Lv."+lev+"   →   변경값",contentX+14,y+165,0xFFB6A8A8);
                }else if (tab==1 || tab==2){
                    boolean iv=tab==1;
                    JsonArray stats=array(mon,iv?"ivs":"evs");
                    int total=number(mon,iv?"ivTotal":"evTotal",0);
                    g.drawString(font,(iv?"개체값":"노력치")+" 총합: "+total+(iv?"/186":"/510"),
                        contentX+9,y+117,0xFFCEB2A6);
                    int gap=Math.max(24,Math.min(33,(h-188)/6));
                    for(int i=0;i<6;i++){
                        int yy=y+135+i*gap;
                        int val=i<stats.size()?stats.get(i).getAsInt():0;
                        g.drawString(font,LABELS[i],contentX+13,yy+6,0xFFDED6D3);
                        int barStart=contentX+Math.max(70,contentW/3);
                        // Tiny warm progress rail below current stat text.
                        int length=Math.max(30,Math.min(90,contentW/4));
                        int bx=contentX+contentW-length-7;
                        if(contentW>390){
                            g.fill(bx,yy+22,bx+length,yy+25,0xFF393235);
                            g.fill(bx,yy+22,bx+(int)(length*(val/(double)(iv?31:252))),
                                yy+25,0xFFE3A578);
                        }
                    }
                } else if(tab==3){
                    g.drawString(font,"성격 선택 · 현재: "+nature,
                        contentX+8,y+117,0xFFCEB2A6);
                } else {
                    g.drawString(font,"친밀도: "+number(mon,"friendship",0)+"/255",
                        contentX+8,y+131,0xFFCEB2A6);
                    g.drawString(font,"변경할 값을 입력하세요",contentX+8,y+159,0xFFAD9EA0);
                }

                // Profile: model is rendered after widgets to avoid being covered.
                int rx=rightX+10;
                int rw=rightWidth-20;
                g.fill(rx,y+92,rx+rw,y+Math.min(h-160,275),0xFF343033);
                g.drawCenteredString(font,display,rightX+rightWidth/2,y+h-155,0xFFF7E9E3);
                g.drawCenteredString(font,"Lv."+lev+"   "+nature,
                    rightX+rightWidth/2,y+h-140,0xFFBEB0B0);
                g.drawString(font,"IV   "+number(mon,"ivTotal",0)+"/186",rx+6,y+h-117,0xFFE7DDDA);
                g.drawString(font,"EV   "+number(mon,"evTotal",0)+"/510",rx+6,y+h-104,0xFFE7DDDA);
                g.drawString(font,"친밀도   "+number(mon,"friendship",0),rx+6,y+h-91,0xFFE7DDDA);
            }
            super.render(g,mx,my,dt);

            // Miniature models on left rows and large portrait on right.
            int top=y+83;
            int rowHeight=Math.max(26,Math.min(51,(h-132)/6));
            for(int i=0;i<6;i++){
                JsonObject pm=pokemon(i);
                if(pm!=null){
                    int cy=top+i*rowHeight;
                    g.enableScissor(x+17,cy+2,x+49,cy+rowHeight-5);
                    PokemonPortraits.render(g,string(pm,"species",""),string(pm,"form",""),
                        bool(pm,"shiny",false),x+32,cy+11,0.36f);
                    g.disableScissor();
                }
            }
            if(mon!=null){
                int areaTop=y+91;
                int areaBottom=y+Math.min(h-160,275);
                g.enableScissor(rightX+13,areaTop+3,x+w-23,areaBottom-3);
                PokemonPortraits.render(g,string(mon,"species",""),string(mon,"form",""),
                    bool(mon,"shiny",false),rightX+rightWidth/2,areaTop+26,1.15f);
                g.disableScissor();
            }
        }
    }
}
