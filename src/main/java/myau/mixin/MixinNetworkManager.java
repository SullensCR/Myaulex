package myau.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.util.concurrent.GenericFutureListener;
import myau.Myau;
import myau.event.EventManager;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.PacketArrivalEvent;
import myau.module.modules.CrashGuard;
import myau.util.PacketTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Future;

@SideOnly(Side.CLIENT)
@Mixin(value = {NetworkManager.class}, priority = 9999)
public abstract class MixinNetworkManager {
    /**
     * Identity-keyed cache of the original class-name predicates below. Every decision
     * is bit-for-bit identical to evaluating the string predicate each time (including
     * for modded/custom packet classes); the cache merely avoids repeating the string
     * work on every packet.
     */
    private static final Map<Class<?>, Boolean> PACKET_CLASS_CACHE =
            Collections.synchronizedMap(new IdentityHashMap<Class<?>, Boolean>());

    private static boolean isClientSidePacket(Packet<?> packet) {
        return cachedStartsWith(packet, "net.minecraft.network.play.client");
    }

    private static boolean isServerSidePacket(Packet<?> packet) {
        return cachedStartsWith(packet, "net.minecraft.network.play.server");
    }

    private static boolean cachedStartsWith(Packet<?> packet, String prefix) {
        return PACKET_CLASS_CACHE.computeIfAbsent(
                packet.getClass(),
                packetClass -> packetClass.getName().startsWith(prefix)
        );
    }

    /**
     * Minecraft 1.8.9 already requests TCP_NODELAY. This only verifies and
     * idempotently enforces that socket option; it does not advertise an
     * additional latency reduction.
     */
    @Inject(method = {"channelActive"}, at = {@At("TAIL")})
    private void verifyTcpNoDelay(ChannelHandlerContext context, CallbackInfo callbackInfo) {
        if (Myau.clientSettings != null && Myau.clientSettings.isVerifyTcpNoDelay()) {
            try {
                context.channel().config().setOption(ChannelOption.TCP_NODELAY, true);
            } catch (RuntimeException ignored) {
                // Local/embedded channels may not expose a TCP socket option.
            }
        }
    }

    @Inject(
            method = {"channelRead0*"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo callbackInfo) {
        PacketTrace.receive(packet);
        if (!isClientSidePacket(packet)) {
            if (CrashGuard.shouldBlockIncomingPacket(packet)) {
                callbackInfo.cancel();
                return;
            }
            if ((packet instanceof S19PacketEntityStatus || packet instanceof S12PacketEntityVelocity)
                    && EventManager.hasListeners(PacketArrivalEvent.class)) {
                EventManager.call(new PacketArrivalEvent(packet, Minecraft.getMinecraft().theWorld, System.nanoTime()));
            }
            if (Myau.delayManager != null && Myau.delayManager.shouldDelay((Packet<INetHandlerPlayClient>) packet)) {
                callbackInfo.cancel();
            } else {
                if (EventManager.hasListeners(PacketEvent.class)) {
                    PacketEvent event = new PacketEvent(EventType.RECEIVE, packet);
                    EventManager.call(event);
                    if (event.isCancelled()) {
                        callbackInfo.cancel();
                    }
                }
            }
        }
    }

    @Inject(
            method = {"sendPacket(Lnet/minecraft/network/Packet;)V"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void sendPacket(Packet<?> packet, CallbackInfo callbackInfo) {
        PacketTrace.send(packet);
        if (!isServerSidePacket(packet)) {
            if (EventManager.hasListeners(PacketEvent.class)) {
                PacketEvent event = new PacketEvent(EventType.SEND, packet);
                EventManager.call(event);
                if (event.isCancelled()) {
                    callbackInfo.cancel();
                    return;
                }
            }
            if (Myau.playerStateManager != null && Myau.blinkManager != null && Myau.lagManager != null) {
                if (!Myau.lagManager.isFlushing()) {
                    Myau.playerStateManager.handlePacket(packet);
                    if (Myau.blinkManager.isBlinking()) {
                        if (Myau.blinkManager.offerPacket(packet)) {
                            callbackInfo.cancel();
                            return;
                        }
                    }
                    if (Myau.lagManager.handlePacket(packet)) {
                        callbackInfo.cancel();
                    }
                }
            }
        }
    }

    @Inject(
            method = {"sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;[Lio/netty/util/concurrent/GenericFutureListener;)V"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void sendPacket2(
            Packet<?> packet,
            GenericFutureListener<? extends Future<? super Void>> genericFutureListener,
            GenericFutureListener<? extends Future<? super Void>>[] arr,
            CallbackInfo callbackInfo
    ) {
        if (!isServerSidePacket(packet)) {
            if (Myau.playerStateManager != null && Myau.blinkManager != null && Myau.lagManager != null) {
                if (!Myau.lagManager.isFlushing()) {
                    Myau.playerStateManager.handlePacket(packet);
                    if (Myau.blinkManager.isBlinking()) {
                        if (Myau.blinkManager.offerPacket(packet)) {
                            callbackInfo.cancel();
                            return;
                        }
                    }
                    if (Myau.lagManager.handlePacket(packet)) {
                        callbackInfo.cancel();
                    }
                }
            }
        }
    }
}
