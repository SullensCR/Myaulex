package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.IntProperty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;

import java.util.*;

/** Efficient nearby-bed index and three-layer defense model for the Figma renderer. */
public final class Bedplates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty scanRadius = new IntProperty("scan-radius", 32, 8, 96);
    private final Map<BlockPos, Defense> defenses = new LinkedHashMap<>();
    private final Set<BlockPos> observedBeds = new LinkedHashSet<>();
    private int scanTick;

    public Bedplates() {
        super("Bedplates", false, false, "Displays the first three layers of nearby bed defenses.");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST || mc.thePlayer == null || mc.theWorld == null) return;
        if (++scanTick % 20 != 0) return;
        refreshObserved();
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        defenses.clear();
        observedBeds.clear();
        scanTick = 0;
    }

    public Collection<Defense> getDefenses() {
        return Collections.unmodifiableCollection(new ArrayList<>(defenses.values()));
    }

    public void observeBed(BlockPos position) {
        if (position != null) observedBeds.add(position);
    }

    private void refreshObserved() {
        int radius = Math.max(8, Math.min(96, scanRadius.getValue()));
        Map<BlockPos, Defense> found = new LinkedHashMap<>();
        Iterator<BlockPos> iterator = observedBeds.iterator();
        while (iterator.hasNext()) {
            BlockPos bed = iterator.next();
            IBlockState state = mc.theWorld.getBlockState(bed);
            if (!(state.getBlock() instanceof BlockBed)
                    || mc.thePlayer.getDistanceSq(bed) > radius * radius) {
                iterator.remove();
                continue;
            }
            BlockPos canonical = canonicalBed(bed);
            if (!found.containsKey(canonical)) found.put(canonical, buildDefense(canonical));
        }
        defenses.clear();
        defenses.putAll(found);
    }

    private BlockPos canonicalBed(BlockPos pos) {
        for (BlockPos neighbor : horizontalNeighbors(pos)) {
            if (mc.theWorld.getBlockState(neighbor).getBlock() == Blocks.bed
                    && compare(neighbor, pos) < 0) return neighbor;
        }
        return pos;
    }

    private Defense buildDefense(BlockPos bed) {
        BlockPos mate = findMate(bed);
        Set<Block> previous = new LinkedHashSet<>();
        List<Set<Block>> layers = new ArrayList<>();
        for (int layer = 1; layer <= 3; layer++) {
            Set<Block> materials = new LinkedHashSet<>();
            int minX = Math.min(bed.getX(), mate.getX()) - layer;
            int maxX = Math.max(bed.getX(), mate.getX()) + layer;
            int minZ = Math.min(bed.getZ(), mate.getZ()) - layer;
            int maxZ = Math.max(bed.getZ(), mate.getZ()) + layer;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int dy = 0; dy <= layer; dy++) {
                        int bedDistance = shellDistance(x, bed.getY() + dy, z, bed);
                        int mateDistance = shellDistance(x, bed.getY() + dy, z, mate);
                        if (Math.min(bedDistance, mateDistance) != layer) continue;
                        Block block = mc.theWorld.getBlockState(new BlockPos(x, bed.getY() + dy, z)).getBlock();
                        if (block != Blocks.air && block != Blocks.bed && !previous.contains(block)) materials.add(block);
                    }
                }
            }
            previous.addAll(materials);
            layers.add(materials);
        }
        return new Defense(bed, layers);
    }

    private BlockPos findMate(BlockPos bed) {
        for (BlockPos neighbor : horizontalNeighbors(bed)) {
            if (mc.theWorld.getBlockState(neighbor).getBlock() == Blocks.bed) return neighbor;
        }
        return bed;
    }

    private static int shellDistance(int x, int y, int z, BlockPos bed) {
        return Math.max(Math.max(Math.abs(x - bed.getX()), Math.abs(z - bed.getZ())),
                Math.max(0, y - bed.getY()));
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
        public final List<Set<Block>> layers;

        private Defense(BlockPos bed, List<Set<Block>> layers) {
            this.bed = bed;
            this.layers = layers;
        }
    }
}
