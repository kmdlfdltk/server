package dev.champions.hyperbridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Runtime bridge to user-supplied HyperNetwork protocol JARs.
 * The Java bridge never repackages original proprietary classes/assets.
 */
public final class HyperWire {
    public static final String PARTY = "org.hypernetwork.partypreset.net.PartyPresetPayloads";
    public static final String BUILDER = "org.hypernetwork.pokebuilder.net.PbPayloads";

    private HyperWire() {}

    public static boolean ready(String base) {
        try { Class.forName(base); return true; }
        catch (ClassNotFoundException e) { return false; }
    }

    private static Object instance(String base) throws Exception {
        Class<?> cls=Class.forName(base);
        Object instance=cls.getField("INSTANCE").get(null);
        return instance;
    }

    private static void ensureRegistered(String base) throws Exception {
        Object instance=instance(base);
        instance.getClass().getMethod("register").invoke(instance);
    }

    public static void initialize() {
        try {
            ensureRegistered(PARTY);
            ensureRegistered(BUILDER);
        } catch (Exception e) {
            throw new IllegalStateException(
                "The separate HyperPartyPresetNet and HyperPokeBuilderNet " +
                "protocol JARs must be installed on the server!", e);
        }
    }

    private static Class<?> packetClass(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    public static Object construct(String name,Object...args) {
        try {
            Class<?> clazz=packetClass(name);
            for (Constructor<?> c : clazz.getConstructors()) {
                if (c.getParameterCount() != args.length) continue;
                // Every original payload has a public, fixed-arity data constructor.
                try { return c.newInstance(args); }
                catch (IllegalArgumentException bad) { /* try next constructor */ }
            }
            throw new NoSuchMethodException(name+" expected constructor arity "+args.length);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Hyper payload construction failed: "+name,e);
        }
    }

    public static Object field(Object target,String getter) {
        try { return target.getClass().getMethod(getter).invoke(target); }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Hyper payload getter failed: "+getter,e);
        }
    }

    public static int integer(Object target,String getter) {
        return ((Number)field(target,getter)).intValue();
    }
    public static long number(Object target,String getter) {
        return ((Number)field(target,getter)).longValue();
    }
    public static String string(Object target,String getter) {
        Object x=field(target,getter);return x==null?"":x.toString();
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    public static void receive(String name,
             BiConsumer<Object,ServerPlayer> receiveOnMainThread) {
        try {
            Class<?> type=packetClass(name);
            Object companion=type.getField("Companion").get(null);
            CustomPacketPayload.Type packetType=(CustomPacketPayload.Type)
                companion.getClass().getMethod("getID").invoke(companion);
            ServerPlayNetworking.registerGlobalReceiver(packetType,(payload,context)->
                context.server().execute(()->
                    receiveOnMainThread.accept(payload,context.player())));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register Hyper request handler: "+name,e);
        }
    }

    public static boolean send(ServerPlayer player,String name,Object...values) {
        CustomPacketPayload packet=(CustomPacketPayload)construct(name,values);
        if (!ServerPlayNetworking.canSend(player,packet.type())) {
            HyperBridgeMod.LOG.warn("Player {} missing client payload {}",player.getGameProfile().getName(),name);
            return false;
        }
        ServerPlayNetworking.send(player,packet);
        return true;
    }

    public static Object party(String name,Object...values) {
        return construct(PARTY+"$"+name,values);
    }
    public static Object builder(String name,Object...values) {
        return construct(BUILDER+"$"+name,values);
    }
    public static boolean sendParty(ServerPlayer p,String name,Object...values) {
        return send(p,PARTY+"$"+name,values);
    }
    public static boolean sendBuilder(ServerPlayer p,String name,Object...values) {
        return send(p,BUILDER+"$"+name,values);
    }
}
