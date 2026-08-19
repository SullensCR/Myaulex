package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AppliedMotionEvent;
import myau.events.LoadWorldEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.SteppedFloatProperty;
import myau.util.TeamUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MotionPath extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long FADE_NANOS = 250_000_000L;
    private static final double MOTION_THRESHOLD = 0.015D;
    private static final double SURFACE_PROBE = 0.08D;

    public final ModeProperty selfTrigger = new ModeProperty(
            "self-trigger", 0, new String[]{"KNOCKBACK", "AIRBORNE", "ALWAYS"}
    );
    public final IntProperty predictionTicks = new IntProperty("prediction-ticks", 20, 5, 40);
    public final SteppedFloatProperty lineWidth = new SteppedFloatProperty(
            "line-width", 1.5F, 0.5F, 5.0F, 0.5F, null
    );
    public final PercentProperty opacity = new PercentProperty("opacity", 85);
    public final ModeProperty colorMode = new ModeProperty("color-mode", 0, new String[]{"HUD", "CUSTOM"});
    public final ColorProperty customColor = new ColorProperty(
            "custom-color", new Color(85, 255, 255).getRGB(), () -> this.colorMode.getValue() == 1
    );
    public final BooleanProperty landingMarker = new BooleanProperty("landing-marker", true);
    public final BooleanProperty throughWalls = new BooleanProperty("through-walls", false);
    public final BooleanProperty firstPerson = new BooleanProperty("first-person", true);
    public final BooleanProperty thirdPerson = new BooleanProperty("third-person", true);
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final SteppedFloatProperty playerRadius = new SteppedFloatProperty(
            "player-radius", 8.0F, 2.0F, 16.0F, 1.0F, this.players::getValue
    );

    private final Map<UUID, Track> tracks = new HashMap<>();
    private long predictionTick;

    public MotionPath() {
        super(
                "MotionPath",
                false,
                false,
                "Shows a fading, collision-aware forecast of player movement."
        );
    }

    @EventTarget
    public void onAppliedMotion(AppliedMotionEvent event) {
        if (!this.isEnabled() || event.getPlayer() == null || mc.thePlayer == null) return;
        EntityPlayer player = event.getPlayer();
        if (player != mc.thePlayer) {
            if (!this.players.getValue()
                    || !withinRadius(player.getDistanceSqToEntity(mc.thePlayer), this.playerRadius.getValue())) {
                return;
            }
        }
        Track track = this.track(player);
        track.impulseActive = true;
        track.impulseAge = 0;
        track.impulseSawAirborne = false;
        track.impulseTick = this.predictionTick;
        track.impulseX = event.getMotionX();
        track.impulseY = event.getMotionY();
        track.impulseZ = event.getMotionZ();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        this.predictionTick++;
        long now = System.nanoTime();
        Set<UUID> retained = new HashSet<>();

        this.updateLocal(now);
        retained.add(mc.thePlayer.getUniqueID());

        if (this.players.getValue()) {
            double radiusSq = this.playerRadius.getValue() * this.playerRadius.getValue();
            List<EntityPlayer> snapshot = new ArrayList<>(mc.theWorld.playerEntities);
            for (EntityPlayer player : snapshot) {
                if (!this.shouldTrack(player, radiusSq)) continue;
                retained.add(player.getUniqueID());
                this.updateRemote(player, now);
            }
        }

        this.tracks.entrySet().removeIf(entry -> !retained.contains(entry.getKey()));
    }

    private void updateLocal(long now) {
        EntityPlayer player = mc.thePlayer;
        Track track = this.track(player);
        WorldEnvironment environment = new WorldEnvironment(player);
        MotionPathPredictor.Surface support = environment.supportingSurface(player.getEntityBoundingBox());
        boolean supported = support != null;

        boolean active = shouldShowSelf(
                this.selfTrigger.getValue(),
                supported,
                track.impulseActive,
                this.hasMeaningfulLocalMotion(player, supported)
        );

        if (active) {
            MotionPathPredictor.Input input = this.localInput();
            MotionPathPredictor.Request request = new MotionPathPredictor.Request(
                    player.getEntityBoundingBox(),
                    player.motionX,
                    player.motionY,
                    player.motionZ,
                    supported,
                    0.6D,
                    this.predictionTicks.getValue(),
                    input,
                    false
            );
            track.activate(MotionPathPredictor.predict(request, environment), this.baseColor(), now);
        } else {
            track.deactivate();
        }
        this.advanceImpulse(track, supported, player.motionX, player.motionY, player.motionZ);
    }

    private void updateRemote(EntityPlayer player, long now) {
        Track track = this.track(player);
        Vec3 observed = track.observe(player.posX, player.posY, player.posZ);
        boolean recentImpulse = track.impulseActive && this.predictionTick - track.impulseTick <= 3L;
        double motionX = recentImpulse ? track.impulseX : observed.xCoord;
        double motionY = recentImpulse ? track.impulseY : observed.yCoord;
        double motionZ = recentImpulse ? track.impulseZ : observed.zCoord;
        boolean moving = recentImpulse || Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ)
                > MOTION_THRESHOLD;

        WorldEnvironment environment = new WorldEnvironment(player);
        MotionPathPredictor.Surface support = environment.supportingSurface(player.getEntityBoundingBox());
        boolean supported = support != null;
        if (moving) {
            MotionPathPredictor.Request request = new MotionPathPredictor.Request(
                    player.getEntityBoundingBox(),
                    motionX,
                    motionY,
                    motionZ,
                    supported,
                    0.6D,
                    this.predictionTicks.getValue(),
                    null,
                    !recentImpulse
            );
            track.activate(MotionPathPredictor.predict(request, environment), this.playerColor(player), now);
        } else {
            track.deactivate();
        }
        this.advanceImpulse(track, supported, motionX, motionY, motionZ);
    }

    private void advanceImpulse(Track track, boolean supported, double motionX, double motionY, double motionZ) {
        if (!track.impulseActive) return;
        track.impulseAge++;
        if (!supported) track.impulseSawAirborne = true;
        boolean landed = track.impulseSawAirborne && supported && track.impulseAge > 1;
        boolean settled = supported
                && Math.hypot(motionX, motionZ) < 0.02D
                && Math.abs(motionY) < 0.09D
                && track.impulseAge > 2;
        if (landed || settled || track.impulseAge >= this.predictionTicks.getValue()) {
            track.impulseActive = false;
        }
    }

    private MotionPathPredictor.Input localInput() {
        float strafe = mc.thePlayer.movementInput == null ? 0.0F : mc.thePlayer.movementInput.moveStrafe;
        float forward = mc.thePlayer.movementInput == null ? 0.0F : mc.thePlayer.movementInput.moveForward;
        boolean jump = mc.thePlayer.movementInput != null && mc.thePlayer.movementInput.jump;
        double jumpBoost = mc.thePlayer.isPotionActive(Potion.jump)
                ? (mc.thePlayer.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1D
                : 0.0D;
        return new MotionPathPredictor.Input(
                strafe,
                forward,
                mc.thePlayer.rotationYaw,
                mc.thePlayer.getAIMoveSpeed(),
                mc.thePlayer.jumpMovementFactor,
                jump,
                mc.thePlayer.isSprinting(),
                jumpBoost
        );
    }

    private boolean hasMeaningfulLocalMotion(EntityPlayer player, boolean supported) {
        boolean input = mc.thePlayer.movementInput != null
                && (Math.abs(mc.thePlayer.movementInput.moveForward) > 0.001F
                || Math.abs(mc.thePlayer.movementInput.moveStrafe) > 0.001F
                || mc.thePlayer.movementInput.jump);
        return input
                || Math.hypot(player.motionX, player.motionZ) > MOTION_THRESHOLD
                || (!supported && Math.abs(player.motionY) > MOTION_THRESHOLD);
    }

    private boolean shouldTrack(EntityPlayer player, double radiusSq) {
        return player != null
                && player != mc.thePlayer
                && player.isEntityAlive()
                && player.deathTime <= 0
                && withinRadius(player.getDistanceSqToEntity(mc.thePlayer), (float) Math.sqrt(radiusSq))
                && !TeamUtil.isBot(player);
    }

    static boolean withinRadius(double distanceSq, float radius) {
        return distanceSq <= (double) radius * radius;
    }

    static boolean shouldShowSelf(int trigger, boolean supported, boolean impulseActive, boolean meaningfulMotion) {
        switch (trigger) {
            case 1:
                return !supported || impulseActive;
            case 2:
                return meaningfulMotion;
            case 0:
            default:
                return impulseActive;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null) return;
        boolean thirdPersonView = mc.gameSettings.thirdPersonView != 0;
        if ((!thirdPersonView && !this.firstPerson.getValue())
                || (thirdPersonView && !this.thirdPerson.getValue())) {
            return;
        }

        long now = System.nanoTime();
        RenderState state = RenderState.capture();
        try {
            this.prepareRenderState();
            for (Track track : this.tracks.values()) {
                float fade = track.fade(now);
                if (fade <= 0.0F || track.current == null) continue;
                this.renderTrack(track, event.getPartialTicks(), fade);
            }
        } finally {
            state.restore();
        }
    }

    private void prepareRenderState() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        if (this.throughWalls.getValue()) GlStateManager.disableDepth();
        else GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(this.lineWidth.getValue());
    }

    private void renderTrack(Track track, float partialTicks, float fade) {
        MotionPathPredictor.Prediction previous = track.previous == null ? track.current : track.previous;
        MotionPathPredictor.Prediction current = track.current;
        int samples = Math.max(previous.points().size(), current.points().size());
        if (samples < 2) return;

        RenderManager renderManager = mc.getRenderManager();
        double cameraX = ((IAccessorRenderManager) renderManager).getRenderPosX();
        double cameraY = ((IAccessorRenderManager) renderManager).getRenderPosY();
        double cameraZ = ((IAccessorRenderManager) renderManager).getRenderPosZ();
        int red = track.color >> 16 & 0xFF;
        int green = track.color >> 8 & 0xFF;
        int blue = track.color & 0xFF;
        float configuredOpacity = this.opacity.getValue() / 100.0F;

        WorldRenderer renderer = Tessellator.getInstance().getWorldRenderer();
        renderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int index = 0; index < samples; index++) {
            double pathProgress = samples == 1 ? 0.0D : (double) index / (samples - 1);
            Vec3 before = previous.sample(pathProgress);
            Vec3 after = current.sample(pathProgress);
            double x = lerp(before.xCoord, after.xCoord, partialTicks) - cameraX;
            double y = lerp(before.yCoord, after.yCoord, partialTicks) - cameraY;
            double z = lerp(before.zCoord, after.zCoord, partialTicks) - cameraZ;
            int alpha = clampColor(255.0F * configuredOpacity * fade * (1.0F - 0.8F * (float) pathProgress));
            renderer.pos(x, y, z).color(red, green, blue, alpha).endVertex();
        }
        Tessellator.getInstance().draw();

        if (this.landingMarker.getValue() && current.surface() != null) {
            MotionPathPredictor.Surface surface = MotionPathPredictor.Surface.interpolate(
                    previous.surface(), current.surface(), partialTicks
            );
            this.renderSurface(surface, cameraX, cameraY, cameraZ, red, green, blue, configuredOpacity * fade);
        }
    }

    private void renderSurface(
            MotionPathPredictor.Surface surface,
            double cameraX,
            double cameraY,
            double cameraZ,
            int red,
            int green,
            int blue,
            float opacity
    ) {
        if (surface == null) return;
        double minX = surface.minX - cameraX;
        double maxX = surface.maxX - cameraX;
        double y = surface.y - cameraY + 0.002D;
        double minZ = surface.minZ - cameraZ;
        double maxZ = surface.maxZ - cameraZ;
        WorldRenderer renderer = Tessellator.getInstance().getWorldRenderer();

        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        int fillAlpha = clampColor(opacity * 255.0F * 0.22F);
        renderer.pos(minX, y, minZ).color(red, green, blue, fillAlpha).endVertex();
        renderer.pos(minX, y, maxZ).color(red, green, blue, fillAlpha).endVertex();
        renderer.pos(maxX, y, maxZ).color(red, green, blue, fillAlpha).endVertex();
        renderer.pos(maxX, y, minZ).color(red, green, blue, fillAlpha).endVertex();
        Tessellator.getInstance().draw();

        renderer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        int outlineAlpha = clampColor(opacity * 255.0F * 0.72F);
        renderer.pos(minX, y, minZ).color(red, green, blue, outlineAlpha).endVertex();
        renderer.pos(minX, y, maxZ).color(red, green, blue, outlineAlpha).endVertex();
        renderer.pos(maxX, y, maxZ).color(red, green, blue, outlineAlpha).endVertex();
        renderer.pos(maxX, y, minZ).color(red, green, blue, outlineAlpha).endVertex();
        Tessellator.getInstance().draw();
    }

    private int baseColor() {
        if (this.colorMode.getValue() == 1) return this.customColor.getValue();
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        return hud == null ? Color.WHITE.getRGB() : hud.getColor(System.currentTimeMillis()).getRGB();
    }

    private int playerColor(EntityPlayer player) {
        ScorePlayerTeam team = player.getTeam() instanceof ScorePlayerTeam ? (ScorePlayerTeam) player.getTeam() : null;
        if (team != null) {
            String prefix = FontRenderer.getFormatFromString(team.getColorPrefix());
            if (prefix.length() >= 2) {
                return mc.fontRendererObj.getColorCode(prefix.charAt(1)) | 0xFF000000;
            }
        }
        return this.baseColor();
    }

    private Track track(EntityPlayer player) {
        return this.tracks.computeIfAbsent(player.getUniqueID(), ignored -> new Track(player));
    }

    @EventTarget
    public void onWorldChange(LoadWorldEvent event) {
        this.clearState();
    }

    @Override
    public void onEnabled() {
        this.clearState();
    }

    @Override
    public void onDisabled() {
        this.clearState();
    }

    private void clearState() {
        this.tracks.clear();
        this.predictionTick = 0L;
    }

    private static int clampColor(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private static double lerp(double previous, double current, double progress) {
        return previous + (current - previous) * progress;
    }

    static float fade(long now, long lastActive, boolean active) {
        if (active) return 1.0F;
        long elapsed = Math.max(0L, now - lastActive);
        return Math.max(0.0F, 1.0F - (float) elapsed / FADE_NANOS);
    }

    private static final class Track {
        MotionPathPredictor.Prediction previous;
        MotionPathPredictor.Prediction current;
        int color = Color.WHITE.getRGB();
        boolean active;
        long lastActive;
        boolean impulseActive;
        int impulseAge;
        boolean impulseSawAirborne;
        long impulseTick = Long.MIN_VALUE;
        double impulseX;
        double impulseY;
        double impulseZ;
        final MotionPathObservedMotion observedMotion;

        Track(EntityPlayer player) {
            this.observedMotion = new MotionPathObservedMotion(player.posX, player.posY, player.posZ);
        }

        void activate(MotionPathPredictor.Prediction prediction, int color, long now) {
            if (!this.active && now - this.lastActive >= FADE_NANOS) this.previous = null;
            else this.previous = this.current;
            this.current = prediction;
            this.color = color;
            this.active = true;
            this.lastActive = now;
        }

        void deactivate() {
            this.active = false;
        }

        float fade(long now) {
            return MotionPath.fade(now, this.lastActive, this.active);
        }

        Vec3 observe(double x, double y, double z) {
            return this.observedMotion.observe(x, y, z);
        }
    }

    private static final class WorldEnvironment implements MotionPathPredictor.Environment {
        private final EntityPlayer player;

        WorldEnvironment(EntityPlayer player) {
            this.player = player;
        }

        @Override
        public List<AxisAlignedBB> collisions(AxisAlignedBB query) {
            if (mc.theWorld == null) return new ArrayList<>();
            return mc.theWorld.getCollidingBoundingBoxes(this.player, query);
        }

        @Override
        public MotionPathPredictor.Surface supportingSurface(AxisAlignedBB box) {
            AxisAlignedBB probe = new AxisAlignedBB(
                    box.minX + 1.0E-4D,
                    box.minY - SURFACE_PROBE,
                    box.minZ + 1.0E-4D,
                    box.maxX - 1.0E-4D,
                    box.minY + 0.01D,
                    box.maxZ - 1.0E-4D
            );
            AxisAlignedBB best = null;
            double bestHeight = -Double.MAX_VALUE;
            double bestOverlap = -1.0D;
            for (AxisAlignedBB collision : this.collisions(probe)) {
                if (collision.maxY > box.minY + 0.011D) continue;
                double overlapX = Math.max(0.0D, Math.min(box.maxX, collision.maxX) - Math.max(box.minX, collision.minX));
                double overlapZ = Math.max(0.0D, Math.min(box.maxZ, collision.maxZ) - Math.max(box.minZ, collision.minZ));
                double overlap = overlapX * overlapZ;
                if (overlap <= 0.0D) continue;
                if (collision.maxY > bestHeight + 1.0E-5D
                        || (Math.abs(collision.maxY - bestHeight) <= 1.0E-5D && overlap > bestOverlap)) {
                    best = collision;
                    bestHeight = collision.maxY;
                    bestOverlap = overlap;
                }
            }
            return best == null ? null : MotionPathPredictor.Surface.fromBox(best);
        }

        @Override
        public float slipperiness(AxisAlignedBB box) {
            MotionPathPredictor.Surface surface = this.supportingSurface(box);
            if (surface == null || mc.theWorld == null) return 0.6F;
            BlockPos position = new BlockPos(
                    (surface.minX + surface.maxX) * 0.5D,
                    surface.y - 0.01D,
                    (surface.minZ + surface.maxZ) * 0.5D
            );
            return mc.theWorld.getBlockState(position).getBlock().slipperiness;
        }

        @Override
        public boolean isWater(AxisAlignedBB box) {
            return mc.theWorld != null && mc.theWorld.isMaterialInBB(box.contract(0.001D, 0.001D, 0.001D), Material.water);
        }

        @Override
        public boolean isLava(AxisAlignedBB box) {
            return mc.theWorld != null && mc.theWorld.isMaterialInBB(box.contract(0.001D, 0.001D, 0.001D), Material.lava);
        }

        @Override
        public boolean isWeb(AxisAlignedBB box) {
            return this.containsBlock(box, false);
        }

        @Override
        public boolean isLadder(AxisAlignedBB box) {
            return this.containsBlock(box, true);
        }

        private boolean containsBlock(AxisAlignedBB box, boolean ladder) {
            if (mc.theWorld == null) return false;
            int minX = MathHelper.floor_double(box.minX + 1.0E-4D);
            int maxX = MathHelper.floor_double(box.maxX - 1.0E-4D);
            int minY = MathHelper.floor_double(box.minY + 1.0E-4D);
            int maxY = MathHelper.floor_double(box.maxY - 1.0E-4D);
            int minZ = MathHelper.floor_double(box.minZ + 1.0E-4D);
            int maxZ = MathHelper.floor_double(box.maxZ - 1.0E-4D);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos position = new BlockPos(x, y, z);
                        IBlockState state = mc.theWorld.getBlockState(position);
                        Block block = state.getBlock();
                        if (!ladder && block == Blocks.web) return true;
                        if (ladder && block.isLadder(mc.theWorld, position, this.player)) return true;
                    }
                }
            }
            return false;
        }
    }

    private static final class RenderState {
        private final boolean texture;
        private final boolean blend;
        private final boolean alpha;
        private final boolean depth;
        private final boolean cull;
        private final boolean lineSmooth;
        private final boolean depthMask;
        private final int blendSrc;
        private final int blendDst;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final int lineHint;
        private final float lineWidth;
        private final float[] color = new float[4];

        private RenderState() {
            this.texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            this.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            this.alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            this.depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            this.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            this.lineSmooth = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
            this.depthMask = GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != GL11.GL_FALSE;
            this.blendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
            this.blendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
            this.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            this.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            this.lineHint = GL11.glGetInteger(GL11.GL_LINE_SMOOTH_HINT);
            this.lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, buffer);
            buffer.get(this.color);
        }

        static RenderState capture() {
            return new RenderState();
        }

        void restore() {
            GL11.glLineWidth(this.lineWidth);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, this.lineHint);
            if (this.lineSmooth) GL11.glEnable(GL11.GL_LINE_SMOOTH);
            else GL11.glDisable(GL11.GL_LINE_SMOOTH);
            if (this.texture) GlStateManager.enableTexture2D();
            else GlStateManager.disableTexture2D();
            if (this.blend) GlStateManager.enableBlend();
            else GlStateManager.disableBlend();
            if (this.alpha) GlStateManager.enableAlpha();
            else GlStateManager.disableAlpha();
            if (this.depth) GlStateManager.enableDepth();
            else GlStateManager.disableDepth();
            if (this.cull) GlStateManager.enableCull();
            else GlStateManager.disableCull();
            GlStateManager.depthMask(this.depthMask);
            GlStateManager.tryBlendFuncSeparate(
                    this.blendSrc,
                    this.blendDst,
                    this.blendSrcAlpha,
                    this.blendDstAlpha
            );
            GlStateManager.color(this.color[0], this.color[1], this.color[2], this.color[3]);
        }
    }
}
