package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.BlockUtil;
import myau.util.ItemUtil;
import myau.util.RandomUtil;
import myau.util.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.*;

public class AutoBlockIn extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
    private final Set<String> placeThrough = new HashSet<>();
    
    public FloatProperty range;
    public IntProperty speed;
    public IntProperty tolerance;
    public BooleanProperty itemSpoof;
    
    private float serverYaw;
    private float serverPitch;
    private double filled;
    private Vec3 placePos;
    private Vec3 hitPos;
    private String face = "";
    private boolean pendingPlace;
    private boolean placingActive;
    private boolean skipTick;
    public int origSlot = -1;
    private int plannedPlaceSlot = -1;
    private int leftUnpressed;
    private int rightUnpressed;
    private boolean swapped;
    public boolean active;
    
    public AutoBlockIn() {
        super("AutoBlockIn", false);
        
        // Initialize block scores (lower is better)
        BLOCK_SCORE.put("obsidian", 0);
        BLOCK_SCORE.put("end_stone", 1);
        BLOCK_SCORE.put("planks", 2);
        BLOCK_SCORE.put("log", 2);
        BLOCK_SCORE.put("glass", 3);
        BLOCK_SCORE.put("stained_glass", 3);
        BLOCK_SCORE.put("hardened_clay", 4);
        BLOCK_SCORE.put("stained_hardened_clay", 4);
        BLOCK_SCORE.put("wool", 5);
        
        // Initialize blocks we can place through
        placeThrough.add("air");
        placeThrough.add("water");
        placeThrough.add("lava");
        placeThrough.add("fire");
        
        // Register properties
        this.range = new FloatProperty("Range", 4.5f, 0.5f, 4.5f);
        this.speed = new IntProperty("Speed", 8, 0, 100);
        this.tolerance = new IntProperty("Rotation-Tolerance", 25, 20, 100);
        this.itemSpoof = new BooleanProperty("Spoof-Item", false);
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
        }
    }

    @Override
    public void onDisabled() {
        disablePlacing(true);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        
        // Track server rotations from packets
        serverYaw = event.getNewYaw();
        serverPitch = event.getNewPitch();
        
        // Track mouse button states
        leftUnpressed = mc.gameSettings.keyBindAttack.isPressed() ? 0 : leftUnpressed + 1;
        rightUnpressed = mc.gameSettings.keyBindUseItem.isPressed() ? 0 : rightUnpressed + 1;
        
        // Check if keybind is pressed (using sneak as the keybind)
        boolean pressed = mc.gameSettings.keyBindSneak.isKeyDown();
        
        // Disable if NoFall or Scaffold is active
        if (pressed && mc.currentScreen == null) {
            // Find best block slot
            plannedPlaceSlot = -1;
            int bestScore = Integer.MAX_VALUE;
            int currentSlot = mc.thePlayer.inventory.currentItem;
            
            for (int slot = 8; slot >= 0; --slot) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
                if (stack == null || stack.stackSize == 0) continue;
                
                if (stack.getItem() instanceof ItemBlock) {
                    Block block = ((ItemBlock) stack.getItem()).getBlock();
                    String blockName = block.getUnlocalizedName().replace("tile.", "");
                    
                    Integer score = BLOCK_SCORE.get(blockName);
                    if (score != null && score < bestScore) {
                        bestScore = score;
                        plannedPlaceSlot = slot;
                        if (score == 0) break; // Obsidian found, can't get better
                    }
                }
            }
            
            if (plannedPlaceSlot == -1) {
                disablePlacing(true);
                return;
            }
            
            // Try to aim at roof or sides
            Object[] res = roofAim();
            if (res == null) res = sidesAim();
            
            if (res == null) {
                disablePlacing(true);
                return;
            }
            
            // Enable placing mode
            if (!placingActive) {
                if (enablePlacing()) return;
            }
            
            if (skipTick) {
                skipTick = false;
                return;
            }
            
            // Switch to the planned slot
            if (plannedPlaceSlot != -1 && plannedPlaceSlot != currentSlot) {
                mc.thePlayer.inventory.currentItem = plannedPlaceSlot;
                swapped = true;
            }
            
            // Extract rotation data
            Object[] ray = (Object[]) res[0];
            Vec3 hit0 = (Vec3) ray[0];
            String face0 = (String) ray[1];
            
            float aimYaw = (float) res[1];
            float aimPitch = (float) res[2];
            
            // Smooth the rotations
            Float[] sm = getRotationsSmoothed(aimYaw, aimPitch);
            
            // Verify we can actually place at this rotation
            double reach = range.getValue();
            MovingObjectPosition mop = RotationUtil.rayTrace(sm[0], sm[1], reach, 1.0f);
            
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                Vec3 hit1 = mop.hitVec;
                String face1 = mop.sideHit.getName();
                
                BlockPos hitBlock = mop.getBlockPos();
                BlockPos targetBlock = new BlockPos((int)Math.floor(hit0.xCoord), (int)Math.floor(hit0.yCoord), (int)Math.floor(hit0.zCoord));
                
                if (hitBlock.equals(targetBlock) && face1.equals(face0)) {
                    double tol = tolerance.getValue();
                    if (Math.abs(sm[0] - serverYaw) <= tol && Math.abs(sm[1] - serverPitch) <= tol) {
                        hitPos = hit1;
                        face = face1;
                        placePos = new Vec3(hitBlock.getX() + hit1.xCoord - Math.floor(hit1.xCoord),
                                          hitBlock.getY() + hit1.yCoord - Math.floor(hit1.yCoord),
                                          hitBlock.getZ() + hit1.zCoord - Math.floor(hit1.zCoord));
                        pendingPlace = true;
                    }
                }
            }
            
            // Set the rotation
            event.setRotation(sm[0], sm[1], 3);
        } else {
            disablePlacing(true);
        }
        
        // Calculate fill percentage for display
        filled = 0;
        if (pressed && mc.currentScreen == null) {
            Vec3 feet = mc.thePlayer.getPositionVector();
            BlockPos feetPos = new BlockPos(Math.floor(feet.xCoord), Math.floor(feet.yCoord), Math.floor(feet.zCoord));
            
            // Check block above head
            if (!canPlaceThrough(mc.theWorld.getBlockState(feetPos.up(2)).getBlock())) filled++;
            
            // Check 4 sides at feet and head level
            int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] d : dirs) {
                BlockPos posFeet = feetPos.add(d[0], 0, d[2]);
                if (!canPlaceThrough(mc.theWorld.getBlockState(posFeet).getBlock())) filled++;
                
                BlockPos posHead = feetPos.add(d[0], 1, d[2]);
                if (!canPlaceThrough(mc.theWorld.getBlockState(posHead).getBlock())) filled++;
            }
        }
    }
    
    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        
        if (pendingPlace && hitPos != null && face != null) {
            pendingPlace = false;
            
            // Place the block
            BlockPos blockPos = new BlockPos(Math.floor(hitPos.xCoord), Math.floor(hitPos.yCoord), Math.floor(hitPos.zCoord));
            
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, 
                    mc.thePlayer.inventory.getCurrentItem(), blockPos, 
                    getEnumFacing(face), hitPos)) {
                mc.thePlayer.swingItem();
            }
        }
    }
    
    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (placingActive) {
            event.setCancelled(true);
        }
    }
    
    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (placingActive) {
            event.setCancelled(true);
        }
    }

    private boolean enablePlacing() {
        if (placingActive) return false;
        
        placingActive = true;
        if (leftUnpressed < 2 || rightUnpressed < 2) skipTick = true;
        
        swapped = false;
        active = true;
        origSlot = mc.thePlayer.inventory.currentItem;
        
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        
        return true;
    }

    private void disablePlacing(boolean resetSlot) {
        if (!placingActive) return;
        
        if (resetSlot && swapped && origSlot != -1 && origSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = origSlot;
        }
        
        placingActive = false;
        swapped = false;
        skipTick = false;
        origSlot = -1;
        plannedPlaceSlot = -1;
        active = false;
    }

    private Float[] getRotationsSmoothed(float targetYaw, float targetPitch) {
        float curYaw = serverYaw;
        float curPitch = serverPitch;
        
        float dYaw = targetYaw - curYaw;
        float dPit = targetPitch - curPitch;
        
        // Wrap yaw difference
        while (dYaw > 180) dYaw -= 360;
        while (dYaw < -180) dYaw += 360;
        
        if (Math.abs(dYaw) < 0.1f) curYaw = targetYaw;
        if (Math.abs(dPit) < 0.1f) curPitch = targetPitch;
        
        if (curYaw == targetYaw && curPitch == targetPitch) {
            return new Float[] {curYaw, curPitch};
        }
        
        float maxStep = speed.getValue().floatValue();
        float random = 20;
        
        if (random > 0f) {
            float factor = 1f - (float) RandomUtil.nextDouble(0, random / 100f);
            maxStep *= factor;
        }
        
        float stepYaw = Math.max(-maxStep, Math.min(maxStep, dYaw));
        float stepPit = Math.max(-maxStep, Math.min(maxStep, dPit));
        
        curYaw += stepYaw;
        curPitch += stepPit;
        
        // Check if we overshot
        if (Math.signum(targetYaw - curYaw) != Math.signum(dYaw)) curYaw = targetYaw;
        if (Math.signum(targetPitch - curPitch) != Math.signum(dPit)) curPitch = targetPitch;
        
        return new Float[] {curYaw, curPitch};
    }

    private Object[] roofAim() {
        Vec3 p = mc.thePlayer.getPositionVector();
        BlockPos aboveHead = new BlockPos(Math.floor(p.xCoord), Math.floor(p.yCoord) + 2, Math.floor(p.zCoord));
        
        if (!canPlaceThrough(mc.theWorld.getBlockState(aboveHead).getBlock())) {
            return null;
        }
        
        double r = range.getValue();
        Vec3 eye = new Vec3(p.xCoord, p.yCoord + mc.thePlayer.getEyeHeight(), p.zCoord);
        
        int minY = (int) Math.floor(eye.yCoord) + 1;
        int maxY = (int) Math.floor(eye.yCoord + r);
        int minX = (int) Math.floor(eye.xCoord - r);
        int maxX = (int) Math.floor(eye.xCoord + r);
        int minZ = (int) Math.floor(eye.zCoord - r);
        int maxZ = (int) Math.floor(eye.zCoord + r);
        
        List<Object[]> candidates = new ArrayList<>();
        
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5) - eye.xCoord;
                    double dy = (y + 0.5) - eye.yCoord;
                    double dz = (z + 0.5) - eye.zCoord;
                    
                    if (dx*dx + dy*dy + dz*dz > (r + 1) * (r + 1)) continue;
                    
                    Block b = mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (canPlaceThrough(b)) continue;
                    
                    double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                    if (dist > r) continue;
                    
                    candidates.add(new Object[]{dist, new BlockPos(x, y, z)});
                }
            }
        }
        
        // Sort by distance
        candidates.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
        
        // Try each candidate
        for (Object[] cand : candidates) {
            BlockPos blockPos = (BlockPos) cand[1];
            Object[] res = getBestRotationsToBlock(blockPos, eye, r, minY);
            if (res != null) return res;
        }
        
        return null;
    }

    private Object[] getBestRotationsToBlock(BlockPos b, Vec3 eye, double reach, int minY) {
        float baseYaw = normYaw(serverYaw);
        float basePit = serverPitch;
        
        // Try different points on each face
        List<Object[]> candidates = new ArrayList<>();
        candidates.add(new Object[]{0D, baseYaw, basePit});
        
        for (double u = 0.1; u < 1.0; u += 0.2) {
            for (double v = 0.1; v < 1.0; v += 0.2) {
                // Try each face
                float[] rots = getRotationsToPoint(eye, b.getX() + u, b.getY() + 0.05, b.getZ() + v);
                double cost = Math.abs(wrapYawDelta(baseYaw, rots[0])) + Math.abs(rots[1] - basePit);
                candidates.add(new Object[]{cost, rots[0], rots[1]});
            }
        }
        
        candidates.sort((a, b1) -> Double.compare((Double) a[0], (Double) b1[0]));
        
        for (Object[] cand : candidates) {
            float yaw = unwrapYaw((Float) cand[1], serverYaw);
            float pit = (Float) cand[2];
            
            MovingObjectPosition ray = RotationUtil.rayTrace(yaw, pit, reach, 1.0f);
            if (ray == null) continue;
            
            if (ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                BlockPos hit = ray.getBlockPos();
                if (hit.equals(b) && hit.getY() >= minY) {
                    return new Object[]{new Object[]{ray.hitVec, ray.sideHit.getName()}, yaw, pit};
                }
            }
        }
        
        return null;
    }

    private Object[] sidesAim() {
        Vec3 feet = mc.thePlayer.getPositionVector();
        BlockPos feetPos = new BlockPos(Math.floor(feet.xCoord), Math.floor(feet.yCoord), Math.floor(feet.zCoord));
        BlockPos headPos = feetPos.up();
        
        List<BlockPos> goals = new ArrayList<>();
        
        int[][] dirs = {{1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}};
        for (int[] d : dirs) {
            BlockPos g1 = feetPos.add(d[0], 0, d[2]);
            BlockPos g2 = headPos.add(d[0], 0, d[2]);
            
            if (canPlaceThrough(mc.theWorld.getBlockState(g1).getBlock())) goals.add(g1);
            if (canPlaceThrough(mc.theWorld.getBlockState(g2).getBlock())) goals.add(g2);
        }
        
        if (goals.isEmpty()) return null;
        
        double reach = range.getValue();
        Vec3 eye = new Vec3(feet.xCoord, feet.yCoord + mc.thePlayer.getEyeHeight(), feet.zCoord);
        
        return findBestForGoals(goals, reach, eye);
    }

    private Object[] findBestForGoals(List<BlockPos> goals, double reach, Vec3 eye) {
        if (goals.isEmpty()) return null;
        
        float curYaw = normYaw(serverYaw);
        float curPitch = serverPitch;
        
        // Check current rotation first
        MovingObjectPosition now = RotationUtil.rayTrace(curYaw, curPitch, reach, 1.0f);
        if (now != null && now.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos hit = now.getBlockPos();
            if (!canPlaceThrough(mc.theWorld.getBlockState(hit).getBlock())) {
                BlockPos placeAt = hit.offset(now.sideHit);
                for (BlockPos g : goals) {
                    if (placeAt.equals(g)) {
                        return new Object[]{new Object[]{now.hitVec, now.sideHit.getName()}, serverYaw, serverPitch};
                    }
                }
            }
        }
        
        // Try different rotations
        List<Object[]> candidates = new ArrayList<>();
        
        for (BlockPos g : goals) {
            // Try placing on surrounding blocks
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        
                        BlockPos support = g.add(dx, dy, dz);
                        if (canPlaceThrough(mc.theWorld.getBlockState(support).getBlock())) continue;
                        
                        // Try different points on this support block
                        for (double u = 0.2; u < 1.0; u += 0.3) {
                            for (double v = 0.2; v < 1.0; v += 0.3) {
                                float[] rot = getRotationsToPoint(eye, support.getX() + u, support.getY() + v, support.getZ() + u);
                                float dYaw = Math.abs(wrapYawDelta(curYaw, rot[0]));
                                float dPit = Math.abs(rot[1] - curPitch);
                                
                                double cost = dYaw + dPit;
                                candidates.add(new Object[]{cost, rot[0], rot[1], support, g});
                            }
                        }
                    }
                }
            }
        }
        
        if (candidates.isEmpty()) return null;
        
        candidates.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
        
        for (Object[] cand : candidates) {
            float yaw = unwrapYaw((Float) cand[1], serverYaw);
            float pit = (Float) cand[2];
            BlockPos support = (BlockPos) cand[3];
            BlockPos goal = (BlockPos) cand[4];
            
            MovingObjectPosition ray = RotationUtil.rayTrace(yaw, pit, reach, 1.0f);
            if (ray == null) continue;
            
            if (ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                BlockPos hitGrid = ray.getBlockPos();
                if (!hitGrid.equals(support)) continue;
                
                BlockPos placeAt = hitGrid.offset(ray.sideHit);
                if (placeAt.equals(goal)) {
                    return new Object[]{new Object[]{ray.hitVec, ray.sideHit.getName()}, yaw, pit};
                }
            }
        }
        
        return null;
    }

    private boolean canPlaceThrough(Block block) {
        if (block == Blocks.air) return true;
        if (block == Blocks.water) return true;
        if (block == Blocks.flowing_water) return true;
        if (block == Blocks.lava) return true;
        if (block == Blocks.flowing_lava) return true;
        if (block == Blocks.fire) return true;
        return false;
    }

    private float normYaw(float yaw) {
        yaw = ((yaw % 360f) + 360f) % 360f;
        return (yaw > 180f) ? (yaw - 360f) : yaw;
    }

    private float wrapYawDelta(float base, float target) {
        float d = target - base;
        while (d <= -180f) d += 360f;
        while (d > 180f) d -= 360f;
        return d;
    }

    private float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + ((((yaw - prevYaw + 180f) % 360f) + 360f) % 360f - 180f);
    }

    private float[] getRotationsToPoint(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord;
        double dy = ty - eye.yCoord;
        double dz = tz - eye.zCoord;
        double hd = Math.sqrt(dx*dx + dz*dz);
        
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        yaw = normYaw(yaw);
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
        
        return new float[]{yaw, pitch};
    }
    
    private net.minecraft.util.EnumFacing getEnumFacing(String face) {
        switch (face.toUpperCase()) {
            case "UP": return net.minecraft.util.EnumFacing.UP;
            case "DOWN": return net.minecraft.util.EnumFacing.DOWN;
            case "NORTH": return net.minecraft.util.EnumFacing.NORTH;
            case "SOUTH": return net.minecraft.util.EnumFacing.SOUTH;
            case "EAST": return net.minecraft.util.EnumFacing.EAST;
            case "WEST": return net.minecraft.util.EnumFacing.WEST;
            default: return net.minecraft.util.EnumFacing.UP;
        }
    }
}