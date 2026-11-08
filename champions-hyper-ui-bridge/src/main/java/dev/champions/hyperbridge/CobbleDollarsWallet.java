package dev.champions.hyperbridge;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional CobbleDollars bridge: discovers official compiled Kotlin property
 * extensions, e.g. player.cobbleDollars: BigInteger, without shipping the mod.
 * Fail-closed: editing is unavailable if the real provider cannot be found.
 *
 * CobbleDollars 2.x API source usage:
 * fr.harmex.cobbledollars.common.utils.extensions.cobbleDollars
 * (as used by PokeSkies/CobbleDollarsBridge).
 */
public final class CobbleDollarsWallet {
    private static volatile Method getter, setter;
    private static volatile String failure = "Not initialized";
    private static final AtomicBoolean scanned = new AtomicBoolean();
    private CobbleDollarsWallet() {}

    private static boolean register(Class<?> c) {
        Method g=null, s=null;
        for (Method m:c.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] params=m.getParameterTypes();
            if (m.getName().equals("getCobbleDollars") && params.length==1
                && params[0].isAssignableFrom(ServerPlayer.class)
                && BigInteger.class.isAssignableFrom(m.getReturnType())) g=m;
            if (m.getName().equals("setCobbleDollars") && params.length==2
                && params[0].isAssignableFrom(ServerPlayer.class)
                && params[1] == BigInteger.class) s=m;
        }
        if (g!=null && s!=null) {
            g.setAccessible(true);s.setAccessible(true);
            getter=g;setter=s;
            failure="";
            HyperBridgeMod.LOG.info("CobbleDollars wallet API found in {}",c.getName());
            return true;
        }
        return false;
    }

    private static synchronized void ensure() {
        if (scanned.get()) return;
        scanned.set(true);
        if (!FabricLoader.getInstance().isModLoaded("cobbledollars")) {
            failure="CobbleDollars mod is not installed on server";
            return;
        }
        try {
            var mod=FabricLoader.getInstance().getModContainer("cobbledollars")
                .orElseThrow();
            for (Path root:mod.getRootPaths()) {
                Path folder=root.resolve("fr/harmex/cobbledollars/common/utils/extensions");
                if (!Files.isDirectory(folder)) continue;
                try(Stream<Path> paths=Files.walk(folder)) {
                    for(Path classFile:paths.filter(p->p.toString().endsWith(".class")).toList()){
                        String name=root.relativize(classFile).toString()
                            .replace('\\','/').replace('/','.').replaceAll("\\.class$","");
                        try {
                            Class<?> c=Class.forName(name,false,
                                CobbleDollarsWallet.class.getClassLoader());
                            if (register(c))return;
                        }catch(Throwable ignored){
                            // Skip an optional client-only extension.
                        }
                    }
                }
            }
            failure="CobbleDollars 2.x get/setCobbleDollars API not found";
        }catch(Exception exception){
            failure="CobbleDollars wallet discovery failed: "+exception.getMessage();
        }
        HyperBridgeMod.LOG.warn("{}",failure);
    }

    public static boolean available() {
        ensure();
        return getter!=null && setter!=null;
    }

    public static String status() {
        return available()?"CobbleDollars connected":failure;
    }

    public static BigInteger balance(ServerPlayer player) {
        if (!available())throw new IllegalStateException(status());
        try {
            return (BigInteger)getter.invoke(null,player);
        }catch(Exception ex){
            throw new IllegalStateException("CobbleDollars balance read failed",ex);
        }
    }

    public static long balanceForView(ServerPlayer player) {
        if (!available())return -1L;
        BigInteger value=balance(player);
        return value.signum()<0?0L:
            value.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0
                ?Long.MAX_VALUE:value.longValue();
    }

    private static void setBalance(ServerPlayer player,BigInteger value) {
        Objects.requireNonNull(value);
        if(value.signum()<0)throw new IllegalArgumentException("negative balance");
        try { setter.invoke(null,player,value); }
        catch(Exception ex){
            throw new IllegalStateException("CobbleDollars update failed",ex);
        }
    }

    /**
     * Deduct only after preflight validation. One world-server thread handles
     * each incoming operation. On mutation error, refund the exact prior
     * balance, preserving original Pokémon and logging refund failures.
     */
    public static void payAndApply(ServerPlayer player,long cost,Runnable mutate) {
        if(cost<0)throw new IllegalArgumentException("negative cost");
        if(cost==0){mutate.run();return;}
        if(!BridgeEconomyConfig.INSTANCE.enableCobbleDollars
            || !available())throw new IllegalStateException("코블달러 결제 모드를 사용할 수 없습니다: "+status());
        BigInteger before=balance(player);
        BigInteger charge=BigInteger.valueOf(cost);
        if(before.compareTo(charge)<0)
            throw new IllegalStateException("코블달러 부족: 필요 "+cost+" / 보유 "+before);
        setBalance(player,before.subtract(charge));
        try {
            mutate.run();
        }catch(Throwable failure){
            try {setBalance(player,before);}
            catch(Throwable refund){
                failure.addSuppressed(refund);
                HyperBridgeMod.LOG.error("CRITICAL CobbleDollars REFUND FAILED for {} cost {}",
                    player.getUUID(),cost,refund);
            }
            if(failure instanceof RuntimeException re)throw re;
            if(failure instanceof Error e)throw e;
            throw new IllegalStateException("Pokemon change failed",failure);
        }
    }
}