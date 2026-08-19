package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.LoadWorldEvent;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.mixin.IAccessorEntityRenderer;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.render.BedplateState;
import myau.render.ui.UiRenderer;
import myau.render.ui.UiTransform;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders JSON-defined, nametag-like indicators for nearby beds.  The Figma
 * JSON is represented by {@link BedplateState}; its bed and defense drawings
 * deliberately use Minecraft item models instead of exported image assets.
 */
public final class Bedplates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int PANEL_COLOR = 0xB21A1A24;
    private static final int SHADOW_COLOR = 0x63000000;
    private static final int EMPTY_BORDER_COLOR = 0xFFFF4F4F;
    private static final float DESIGN_WIDTH = 1920.0F;
    private static final float DESIGN_HEIGHT = 1080.0F;

    public final IntProperty scanRadius = new IntProperty("scan-radius", 32, 8, 96);
    public final FloatProperty size = new FloatProperty("size", 1.0F, 0.5F, 2.0F);
    private final Map<BlockPos, Defense> defenses = new LinkedHashMap<>();
    private final Set<BlockPos> observedBeds = new LinkedHashSet<>();
    private final Map<BlockPos, Plate> plates = new LinkedHashMap<>();
    private int scanTick;

    public Bedplates() {
        super("Bedplates", false, false, "Displays the first three layers of nearby bed defenses.");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST || mc.thePlayer == null || mc.theWorld == null) return;

        long now = System.currentTimeMillis();
        for (Plate plate : plates.values()) {
            if (!isBedIntact(plate.defense)) plate.destroy(now);
        }
        if (++scanTick % 20 == 0) refreshObserved(now);

        Iterator<Plate> iterator = plates.values().iterator();
        while (iterator.hasNext()) {
            if (BedplateState.fadeComplete(iterator.next().destroyedAtMillis, now)) iterator.remove();
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null || plates.isEmpty()) return;
        UiRenderer renderer = Myau.uiRenderer;
        if (renderer == null || !renderer.isSupported()) return;

        boolean frameStarted = false;
        try {
            ScaledResolution resolution = new ScaledResolution(mc);
            ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
            Map<Plate, Vector3d> projected = new LinkedHashMap<>();
            for (Plate plate : plates.values()) {
                Vector3d point = projectPlate(plate.defense, resolution.getScaleFactor());
                if (point != null) projected.put(plate, point);
            }
            mc.entityRenderer.setupOverlayRendering();
            if (projected.isEmpty()) return;

            UiTransform transform = new UiTransform(mc, DESIGN_WIDTH, DESIGN_HEIGHT, 1.0F, 0.0F);
            long nowNanos = System.nanoTime();
            long nowMillis = System.currentTimeMillis();
            float crosshairX = transform.getLogicalWidth() * 0.5F;
            float crosshairY = transform.getLogicalHeight() * 0.5F;

            for (Map.Entry<Plate, Vector3d> entry : projected.entrySet()) {
                Plate plate = entry.getKey();
                Vector3d point = entry.getValue();
                boolean expanded = pointsAtBed(plate.defense) || pointsAtPlate(plate, point, transform, crosshairX, crosshairY);
                plate.update(expanded, nowNanos);
            }

            renderer.beginFrame("Bedplates", transform, 31.0F);
            frameStarted = true;
            try {
                for (Map.Entry<Plate, Vector3d> entry : projected.entrySet()) {
                    drawPlate(entry.getKey(), entry.getValue(), transform, nowMillis);
                }
            } finally {
                renderer.endFrame();
                frameStarted = false;
            }
        } catch (Throwable ignored) {
            // A failed optional framebuffer/shader must never break Minecraft's HUD.
        } finally {
            if (frameStarted) renderer.endFrame();
        }
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        clear();
    }

    @Override
    public void onDisabled() {
        clear();
    }

    public Collection<Defense> getDefenses() {
        return Collections.unmodifiableCollection(new ArrayList<>(defenses.values()));
    }

    public void observeBed(BlockPos position) {
        if (position != null) observedBeds.add(position);
    }

    private void clear() {
        defenses.clear();
        observedBeds.clear();
        plates.clear();
        scanTick = 0;
    }

    private void refreshObserved(long now) {
        int radius = Math.max(8, Math.min(96, scanRadius.getValue()));
        Map<BlockPos, Defense> found = new LinkedHashMap<>();
        Iterator<BlockPos> iterator = observedBeds.iterator();
        while (iterator.hasNext()) {
            BlockPos bed = iterator.next();
            if (!(mc.theWorld.getBlockState(bed).getBlock() instanceof BlockBed)
                    || mc.thePlayer.getDistanceSq(bed) > radius * radius) {
                iterator.remove();
                continue;
            }
            BlockPos canonical = canonicalBed(bed);
            if (!found.containsKey(canonical)) found.put(canonical, buildDefense(canonical));
        }
        defenses.clear();
        defenses.putAll(found);

        for (Map.Entry<BlockPos, Defense> entry : found.entrySet()) {
            Plate plate = plates.get(entry.getKey());
            if (plate == null) plates.put(entry.getKey(), new Plate(entry.getValue(), now));
            else plate.refresh(entry.getValue());
        }
    }

    private Vector3d projectPlate(Defense defense, int scaleFactor) {
        double x = (defense.bed.getX() + defense.mate.getX() + 1.0D) * 0.5D;
        double y = defense.bed.getY() + 2.0D;
        double z = (defense.bed.getZ() + defense.mate.getZ() + 1.0D) * 0.5D;
        return RenderUtil.projectToScreen(x, y, z, scaleFactor);
    }

    private boolean pointsAtBed(Defense defense) {
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;
        BlockPos hit = mc.objectMouseOver.getBlockPos();
        return defense.bed.equals(hit) || defense.mate.equals(hit);
    }

    private boolean pointsAtPlate(Plate plate, Vector3d point, UiTransform transform, float crosshairX, float crosshairY) {
        int layers = plate.defense.layers.size();
        float scale = size.getValue();
        float width = BedplateState.width(layers, plate.expansion) * scale * transform.getLogicalScale();
        float height = BedplateState.height(plate.expansion) * scale * transform.getLogicalScale();
        return crosshairX >= point.x - width * 0.5F && crosshairX <= point.x + width * 0.5F
                && crosshairY >= point.y - height * 0.5F && crosshairY <= point.y + height * 0.5F;
    }

    private void drawPlate(Plate plate, Vector3d point, UiTransform transform, long now) {
        float alpha = BedplateState.fadeAlpha(plate.destroyedAtMillis, now);
        if (alpha <= 0.0F) return;

        int layers = plate.defense.layers.size();
        float scale = size.getValue();
        float width = BedplateState.width(layers, plate.expansion) * scale;
        float height = BedplateState.height(plate.expansion) * scale;
        float x = (float) ((point.x - transform.getLogicalX()) / transform.getLogicalScale()) - width * 0.5F;
        float y = (float) ((point.y - transform.getLogicalY()) / transform.getLogicalScale()) - height * 0.5F;
        float borderInset = plate.expansion * scale; // JSON: collapsed border is outside; expanded border is inside.
        int opacity = Math.round(alpha * 255.0F);

        Myau.uiRenderer.shadow(x, y, width, height, BedplateState.CORNER_RADIUS * scale,
                0.0F, 3.0F * scale, 10.0F * scale, 0.0F, multiplyAlpha(SHADOW_COLOR, opacity));
        Myau.uiRenderer.backdrop(x, y, width, height, BedplateState.CORNER_RADIUS * scale, multiplyAlpha(PANEL_COLOR, opacity));
        Myau.uiRenderer.outline(x + borderInset, y + borderInset, width - borderInset * 2.0F,
                height - borderInset * 2.0F, Math.max(0.0F, BedplateState.CORNER_RADIUS * scale - borderInset),
                2.0F * scale, multiplyAlpha(borderColor(plate.defense), opacity));

        float itemY = y + (height - BedplateState.ITEM_SIZE * scale) * 0.5F;
        drawItem(new ItemStack(Items.bed), x + BedplateState.itemX(0) * scale, itemY, scale, alpha);
        for (int index = 0; index < layers; index++) {
            IBlockState state = plate.defense.layers.get(index);
            float targetX = x + BedplateState.itemX(index + 1) * scale;
            float initialX = x + BedplateState.itemX(0) * scale;
            float itemX = initialX + (targetX - initialX) * plate.expansion;
            drawItem(stackFor(state), itemX, itemY,
                    scale * (0.75F + 0.25F * plate.expansion), alpha * plate.expansion);
        }
    }

    private static void drawItem(ItemStack stack, float x, float y, float scale, float alpha) {
        if (stack == null || alpha <= 0.001F) return;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(4.5F * scale, 4.5F * scale, 1.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            RenderUtil.renderItemAndEffectIntoGui3D(stack, 0, 0);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private static ItemStack stackFor(IBlockState state) {
        if (state == null) return null;
        Block block = state.getBlock();
        Item item = Item.getItemFromBlock(block);
        return item == null ? null : new ItemStack(item, 1, block.getMetaFromState(state));
    }

    private static int borderColor(Defense defense) {
        if (defense.layers.isEmpty()) return EMPTY_BORDER_COLOR;
        MapColor color = defense.layers.get(0).getBlock().getMapColor(defense.layers.get(0));
        return color == null ? EMPTY_BORDER_COLOR : 0xFF000000 | color.colorValue;
    }

    private static int multiplyAlpha(int color, int alpha) {
        int source = color >>> 24;
        return ((source * alpha / 255) << 24) | (color & 0x00FFFFFF);
    }

    private boolean isBedIntact(Defense defense) {
        return mc.theWorld.getBlockState(defense.bed).getBlock() instanceof BlockBed
                && mc.theWorld.getBlockState(defense.mate).getBlock() instanceof BlockBed;
    }

    private BlockPos canonicalBed(BlockPos pos) {
        for (BlockPos neighbor : horizontalNeighbors(pos)) {
            if (mc.theWorld.getBlockState(neighbor).getBlock() == Blocks.bed && compare(neighbor, pos) < 0) return neighbor;
        }
        return pos;
    }

    private Defense buildDefense(BlockPos bed) {
        BlockPos mate = findMate(bed);
        List<IBlockState> layers = new ArrayList<>();
        for (int layer = 1; layer <= 3; layer++) {
            Map<MaterialKey, MaterialCount> materials = new HashMap<>();
            int minX = Math.min(bed.getX(), mate.getX()) - layer;
            int maxX = Math.max(bed.getX(), mate.getX()) + layer;
            int minZ = Math.min(bed.getZ(), mate.getZ()) - layer;
            int maxZ = Math.max(bed.getZ(), mate.getZ()) + layer;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int dy = 0; dy <= layer; dy++) {
                        int bedDistance = shellDistance(x, bed.getY() + dy, z, bed);
                        int mateDistance = shellDistance(x, mate.getY() + dy, z, mate);
                        if (Math.min(bedDistance, mateDistance) != layer) continue;

                        IBlockState state = mc.theWorld.getBlockState(new BlockPos(x, bed.getY() + dy, z));
                        Block block = state.getBlock();
                        if (block == Blocks.air || block == Blocks.bed) continue;
                        MaterialKey key = new MaterialKey(state);
                        MaterialCount count = materials.get(key);
                        if (count == null) {
                            count = new MaterialCount(state, nearestDistanceSquared(x, bed.getY() + dy, z, bed, mate));
                            materials.put(key, count);
                        } else {
                            count.nearestDistanceSquared = Math.min(count.nearestDistanceSquared,
                                    nearestDistanceSquared(x, bed.getY() + dy, z, bed, mate));
                        }
                        count.count++;
                    }
                }
            }
            if (!materials.isEmpty()) {
                MaterialCount selected = Collections.min(materials.values(), MaterialCount.ORDERING);
                layers.add(selected.state);
            }
        }
        return new Defense(bed, mate, layers);
    }

    private BlockPos findMate(BlockPos bed) {
        for (BlockPos neighbor : horizontalNeighbors(bed)) {
            if (mc.theWorld.getBlockState(neighbor).getBlock() == Blocks.bed) return neighbor;
        }
        return bed;
    }

    private static int shellDistance(int x, int y, int z, BlockPos bed) {
        return Math.max(Math.max(Math.abs(x - bed.getX()), Math.abs(z - bed.getZ())), Math.max(0, y - bed.getY()));
    }

    private static int nearestDistanceSquared(int x, int y, int z, BlockPos bed, BlockPos mate) {
        return Math.min(distanceSquared(x, y, z, bed), distanceSquared(x, y, z, mate));
    }

    private static int distanceSquared(int x, int y, int z, BlockPos pos) {
        int dx = x - pos.getX();
        int dy = y - pos.getY();
        int dz = z - pos.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static List<BlockPos> horizontalNeighbors(BlockPos pos) {
        return Arrays.asList(pos.north(), pos.south(), pos.east(), pos.west());
    }

    private static int compare(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
        if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
        return Integer.compare(a.getZ(), b.getZ());
    }

    public static final class Defense {
        public final BlockPos bed;
        public final BlockPos mate;
        public final List<IBlockState> layers;

        private Defense(BlockPos bed, BlockPos mate, List<IBlockState> layers) {
            this.bed = bed;
            this.mate = mate;
            this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
        }
    }

    private static final class Plate {
        private Defense defense;
        private float expansion;
        private long lastAnimationNanos;
        private long destroyedAtMillis = -1L;

        private Plate(Defense defense, long now) {
            this.defense = defense;
            this.lastAnimationNanos = now * 1_000_000L;
        }

        private void refresh(Defense defense) {
            this.defense = defense;
            this.destroyedAtMillis = -1L;
        }

        private void destroy(long now) {
            if (destroyedAtMillis < 0L) destroyedAtMillis = now;
        }

        private void update(boolean expanded, long nowNanos) {
            float delta = Math.max(0.001F, Math.min(0.1F, (nowNanos - lastAnimationNanos) / 1_000_000_000.0F));
            expansion = BedplateState.animateExpansion(expanded ? 1.0F : 0.0F, expansion, delta);
            lastAnimationNanos = nowNanos;
        }
    }

    private static final class MaterialKey {
        private final Block block;
        private final int metadata;

        private MaterialKey(IBlockState state) {
            this.block = state.getBlock();
            this.metadata = block.getMetaFromState(state);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MaterialKey)) return false;
            MaterialKey key = (MaterialKey) other;
            return block == key.block && metadata == key.metadata;
        }

        @Override
        public int hashCode() {
            return 31 * Block.getIdFromBlock(block) + metadata;
        }
    }

    private static final class MaterialCount {
        private static final Comparator<MaterialCount> ORDERING = new Comparator<MaterialCount>() {
            @Override
            public int compare(MaterialCount left, MaterialCount right) {
                int result = Integer.compare(right.count, left.count);
                if (result != 0) return result;
                result = Integer.compare(left.nearestDistanceSquared, right.nearestDistanceSquared);
                if (result != 0) return result;
                result = Integer.compare(Block.getIdFromBlock(left.state.getBlock()), Block.getIdFromBlock(right.state.getBlock()));
                if (result != 0) return result;
                return Integer.compare(left.state.getBlock().getMetaFromState(left.state), right.state.getBlock().getMetaFromState(right.state));
            }
        };

        private final IBlockState state;
        private int nearestDistanceSquared;
        private int count;

        private MaterialCount(IBlockState state, int nearestDistanceSquared) {
            this.state = state;
            this.nearestDistanceSquared = nearestDistanceSquared;
        }
    }
}
