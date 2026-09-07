package myau.module.modules;

import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ExpoAutoBlockReblockTest {
    private static final Vec3 HIT = new Vec3(0.2D, 1.4D, -0.3D);

    @Test
    public void packetOrderJOracleRejectsThePreviousPortsAttackThenUseSequence() {
        List<Packet<?>> packets = new ArrayList<>();
        packets.add(new C02PacketUseEntity(new TestEntity(), C02PacketUseEntity.Action.ATTACK));
        packets.add(new C08PacketPlayerBlockPlacement((ItemStack) null));

        assertEquals(1, packetOrderJViolations(packets));
    }

    @Test
    public void successfulAttackReblocksWithExposTwoInteractionPackets() throws Exception {
        Entity target = new TestEntity();
        ExpoAutoBlockReblock reblock = new ExpoAutoBlockReblock();
        List<Packet<?>> packets = new ArrayList<>();
        packets.add(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        reblock.recordAttackResult(true, target, HIT);

        assertTrue(reblock.sendBlock(null, true, packets::add));
        assertEquals(4, packets.size());
        assertInteraction(packets.get(1), target, C02PacketUseEntity.Action.INTERACT_AT);
        assertInteraction(packets.get(2), target, C02PacketUseEntity.Action.INTERACT);
        Vec3 actualHit = ((C02PacketUseEntity) packets.get(1)).getHitVec();
        assertEquals(HIT.xCoord, actualHit.xCoord, 0.0D);
        assertEquals(HIT.yCoord, actualHit.yCoord, 0.0D);
        assertEquals(HIT.zCoord, actualHit.zCoord, 0.0D);
        assertTrue(packets.get(3) instanceof C08PacketPlayerBlockPlacement);
        assertEquals(0, packetOrderJViolations(packets));
        assertNull(reblock.getAttackedTarget());
    }

    @Test
    public void lagNewRetainsTheSuccessfulHitAcrossPreAndPost() throws Exception {
        ExpoAutoBlockState state = new ExpoAutoBlockState();
        ExpoAutoBlockReblock reblock = new ExpoAutoBlockReblock();
        Entity attackedTarget = new TestEntity();
        List<Packet<?>> packets = new ArrayList<>();

        execute(state.pre(ExpoAutoBlockState.LAG_NEW, 3, false, false), reblock, attackedTarget, true, packets);
        assertEquals(2, packets.size()); // Swing + attack; reblock is not in PRE.
        assertSame(attackedTarget, reblock.getAttackedTarget());
        packets.add(new C03PacketPlayer(true));
        reblock.onMovement();
        // POST may use restored camera rotations; the saved ray belongs to the hit.
        execute(state.post(), reblock, new TestEntity(), true, packets);

        assertEquals(6, packets.size());
        assertInteraction(packets.get(3), attackedTarget, C02PacketUseEntity.Action.INTERACT_AT);
        assertInteraction(packets.get(4), attackedTarget, C02PacketUseEntity.Action.INTERACT);
        assertTrue(packets.get(5) instanceof C08PacketPlayerBlockPlacement);
        assertEquals(0, packetOrderJViolations(packets));
    }

    @Test
    public void skippedHitSelectAttackStillBlocksWithoutInventingAnInteraction() {
        ExpoAutoBlockReblock reblock = new ExpoAutoBlockReblock();
        List<Packet<?>> packets = new ArrayList<>();
        reblock.recordAttackResult(false, new TestEntity(), HIT);

        assertTrue(reblock.sendBlock(null, true, packets::add));
        assertEquals(1, packets.size());
        assertTrue(packets.get(0) instanceof C08PacketPlayerBlockPlacement);
        assertEquals(0, packetOrderJViolations(packets));
    }

    @Test
    public void rangeOnlyHitWaitsForAnActualMovementBoundary() {
        ExpoAutoBlockReblock reblock = new ExpoAutoBlockReblock();
        Entity target = new TestEntity();
        List<Packet<?>> packets = new ArrayList<>();
        packets.add(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        reblock.recordAttackResult(true, target, null);

        assertFalse(reblock.sendBlock(null, true, packets::add));
        assertTrue(reblock.isDeferred());
        assertEquals(1, packets.size());
        // No fake INTERACT_AT vector or manufactured movement is sent by reblock.
        assertFalse(reblock.sendBlock(null, true, packets::add));
        packets.add(new C03PacketPlayer(true));
        reblock.onMovement();
        assertTrue(reblock.sendBlock(null, true, packets::add));

        assertEquals(3, packets.size());
        assertTrue(packets.get(2) instanceof C08PacketPlayerBlockPlacement);
        assertEquals(0, packetOrderJViolations(packets));
        assertFalse(reblock.isDeferred());
    }

    @Test
    public void vanishedTargetIsNotInteractedWithAndUseWaitsForMovement() {
        ExpoAutoBlockReblock reblock = new ExpoAutoBlockReblock();
        Entity target = new TestEntity();
        List<Packet<?>> packets = new ArrayList<>();
        packets.add(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        reblock.recordAttackResult(true, target, HIT);
        assertFalse(reblock.sendBlock(null, false, packets::add));
        packets.add(new C03PacketPlayer(true));
        reblock.onMovement();
        assertTrue(reblock.sendBlock(null, false, packets::add));

        assertEquals(3, packets.size());
        assertEquals(0, packetOrderJViolations(packets));
    }

    @Test
    public void resetAndFailedNextAttemptClearPreviousInteractionContext() {
        ExpoAutoBlockReblock reblock = new ExpoAutoBlockReblock();
        Entity target = new TestEntity();
        reblock.recordAttackResult(true, target, HIT);
        reblock.reset();
        assertNull(reblock.getAttackedTarget());
        assertFalse(reblock.isDeferred());

        reblock.recordAttackResult(true, target, HIT);
        reblock.recordAttackResult(false, target, HIT);
        List<Packet<?>> packets = new ArrayList<>();
        reblock.sendBlock(null, true, packets::add);
        assertEquals(1, packets.size());
        assertTrue(packets.get(0) instanceof C08PacketPlayerBlockPlacement);
    }

    @Test
    public void everyModeApsAndNoSlowProfileReplaysPacketsAcceptedByPacketOrderJ() {
        int[] modes = {ExpoAutoBlockState.LEGIT, ExpoAutoBlockState.LAG_LEGIT,
                ExpoAutoBlockState.LAG, ExpoAutoBlockState.LAG_NEW};
        for (int mode : modes) {
            for (int aps : new int[]{3, 5, 7, 10, 14}) {
                for (boolean noSlow : new boolean[]{false, true}) {
                    ExpoAutoBlockState state = new ExpoAutoBlockState();
                    ExpoAutoBlockReblock reblock = new ExpoAutoBlockReblock();
                    List<Packet<?>> buffered = new ArrayList<>();
                    List<Packet<?>> delivered = new ArrayList<>();
                    Entity target = new TestEntity();
                    int attempts = 0;
                    for (int tick = 0; tick < 80; tick++) {
                        // Include successful attacks and misses/Hit Select skips.
                        ExpoAutoBlockState.Step pre = state.pre(mode, aps, true, noSlow);
                        boolean attackSucceeded = attempts % 3 != 1;
                        if (pre.getActions().contains(ExpoAutoBlockState.Action.ATTACK)) {
                            attempts++;
                        }
                        execute(pre, reblock, target, attackSucceeded, buffered);
                        buffered.add(new C03PacketPlayer(true));
                        reblock.onMovement();
                        ExpoAutoBlockState.Step post = state.post();
                        execute(post, reblock, target, attackSucceeded, buffered);
                        if (mode == ExpoAutoBlockState.LEGIT
                                || post.getActions().contains(ExpoAutoBlockState.Action.FLUSH_BUFFER)) {
                            delivered.addAll(buffered);
                            buffered.clear();
                        }
                    }
                    delivered.addAll(buffered); // Owner cleanup also preserves FIFO.
                    String profile = "mode=" + mode + " aps=" + aps + " noSlow=" + noSlow;
                    assertEquals(profile, 0, packetOrderJViolations(delivered));
                    long attacks = delivered.stream().filter(p -> p instanceof C02PacketUseEntity
                            && ((C02PacketUseEntity) p).getAction() == C02PacketUseEntity.Action.ATTACK).count();
                    long interactions = delivered.stream().filter(p -> p instanceof C02PacketUseEntity
                            && ((C02PacketUseEntity) p).getAction() == C02PacketUseEntity.Action.INTERACT).count();
                    assertTrue(profile, attacks > 0);
                    assertEquals(profile, attacks, interactions);
                }
            }
        }
    }

    private static void execute(ExpoAutoBlockState.Step step, ExpoAutoBlockReblock reblock,
                                Entity target, boolean attackSucceeded, List<Packet<?>> packets) {
        for (ExpoAutoBlockState.Action action : step.getActions()) {
            switch (action) {
                case RELEASE:
                    packets.add(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                            BlockPos.ORIGIN, EnumFacing.DOWN));
                    break;
                case SPOOF_AWAY:
                    packets.add(new C09PacketHeldItemChange(1));
                    break;
                case SPOOF_BACK:
                    packets.add(new C09PacketHeldItemChange(0));
                    break;
                case ATTACK:
                    packets.add(new C0APacketAnimation());
                    if (attackSucceeded) {
                        packets.add(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                    }
                    reblock.recordAttackResult(attackSucceeded, target, HIT);
                    break;
                case BLOCK:
                    assertTrue(reblock.sendBlock(null, true, packets::add));
                    break;
                default:
                    break;
            }
        }
    }

    /** PacketOrderJ + the attack/interaction reset from PacketOrderProcessor (1.8.9). */
    private static int packetOrderJViolations(List<Packet<?>> packets) {
        boolean attacking = false;
        boolean interacting = false;
        int invalid = 0;
        for (Packet<?> packet : packets) {
            if (packet instanceof C02PacketUseEntity) {
                if (((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
                    attacking = true;
                } else {
                    interacting = true;
                }
            }
            if (packet instanceof C08PacketPlayerBlockPlacement && attacking && !interacting) {
                invalid++;
            }
            if (packet instanceof C03PacketPlayer) {
                attacking = false;
                interacting = false;
            }
        }
        return invalid;
    }

    private static void assertInteraction(Packet<?> packet, Entity target,
                                           C02PacketUseEntity.Action action) throws Exception {
        assertTrue(packet instanceof C02PacketUseEntity);
        assertEquals(action, ((C02PacketUseEntity) packet).getAction());
        PacketBuffer encoded = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writePacketData(encoded);
            assertEquals(target.getEntityId(), encoded.readVarIntFromBuffer());
        } finally {
            encoded.release();
        }
    }

    private static final class TestEntity extends Entity {
        TestEntity() {
            super(null);
        }

        @Override
        protected void entityInit() {
        }

        @Override
        protected void readEntityFromNBT(NBTTagCompound tag) {
        }

        @Override
        protected void writeEntityToNBT(NBTTagCompound tag) {
        }
    }
}
