package myau.management;

import myau.Myau;
import myau.config.Config;
import myau.module.Module;
import myau.module.modules.HUD;
import myau.module.modules.Notifications;
import myau.module.modules.Scaffold;
import myau.module.modules.TargetHUD;
import myau.property.properties.FloatProperty;
import myau.render.HudPosition;
import myau.render.ui.ProgressbarSizes;
import myau.render.ui.UiTransform;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Handles the temporary HUD editor shown while the vanilla chat screen is open. */
public final class HudEditManager {
    private static final float DESIGN_WIDTH = 1920.0F;
    private static final float DESIGN_HEIGHT = 1080.0F;
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final List<Element> ELEMENTS = Arrays.asList(Element.values());

    private Element dragging;
    private DragMode dragMode = DragMode.NONE;
    private UiTransform dragTransform;
    private float dragOffsetX;
    private float dragOffsetY;
    private float resizeAnchorX;
    private float resizeAnchorY;
    private float resizeWidth;
    private float resizeHeight;
    private float resizeStartProjection;
    private float resizeStartScale;
    private boolean sessionOpen;
    private boolean sessionDirty;

    public boolean isEditing() {
        return MC.currentScreen instanceof GuiChat;
    }

    public boolean handleMouseClicked(int mouseX, int mouseY, int button) {
        if (!this.isEditing() || (button != 0 && button != 1)) return false;
        this.sessionOpen = true;
        Layout hit = this.findLayout(mouseX, mouseY);
        if (hit == null) return false;
        if (button == 1) {
            this.reset(hit.element);
            return true;
        }

        this.dragging = hit.element;
        this.dragTransform = hit.transform;
        FloatProperty scaleProperty = this.scaleProperty(hit.element);
        if (hit.containsScaleHandle(mouseX, mouseY) && scaleProperty != null) {
            this.dragMode = DragMode.SCALE;
            this.resizeAnchorX = hit.interactionX;
            this.resizeAnchorY = hit.interactionY;
            this.resizeWidth = hit.width;
            this.resizeHeight = hit.height;
            this.resizeStartScale = scaleProperty.getValue();
            float startX = hit.transform.mouseX(mouseX);
            float startY = hit.transform.mouseY(mouseY);
            this.resizeStartProjection = this.resizeProjection(startX, startY,
                    this.resizeAnchorX, this.resizeAnchorY);
            if (this.resizeStartProjection <= 0.01F) {
                this.resizeStartProjection = this.resizeProjection(
                        this.resizeAnchorX + hit.width,
                        this.resizeAnchorY + hit.height,
                        this.resizeAnchorX,
                        this.resizeAnchorY);
            }
        } else {
            this.dragMode = DragMode.POSITION;
            this.dragOffsetX = hit.transform.mouseX(mouseX) - hit.interactionX;
            this.dragOffsetY = hit.transform.mouseY(mouseY) - hit.interactionY;
        }
        return true;
    }

    public boolean handleMouseDragged(int mouseX, int mouseY) {
        if (!this.isEditing() || this.dragging == null || this.dragTransform == null) return false;
        if (this.dragMode == DragMode.SCALE) {
            this.applyScale(mouseX, mouseY);
        } else {
            float x = this.dragTransform.mouseX(mouseX) - this.dragOffsetX;
            float y = this.dragTransform.mouseY(mouseY) - this.dragOffsetY;
            Layout current = this.layout(this.dragging);
            if (current != null) {
                this.applyPosition(this.dragging, current, x, y);
                this.sessionOpen = true;
                this.sessionDirty = true;
            }
        }
        return true;
    }

    public boolean handleMouseReleased() {
        boolean handled = this.dragging != null;
        this.dragging = null;
        this.dragMode = DragMode.NONE;
        this.dragTransform = null;
        return handled;
    }

    private void applyScale(int mouseX, int mouseY) {
        FloatProperty scaleProperty = this.scaleProperty(this.dragging);
        if (scaleProperty == null || this.resizeStartProjection <= 0.01F) return;
        float currentX = this.dragTransform.mouseX(mouseX);
        float currentY = this.dragTransform.mouseY(mouseY);
        float currentProjection = this.resizeProjection(currentX, currentY,
                this.resizeAnchorX, this.resizeAnchorY);
        float nextScale = this.resizeStartScale * currentProjection / this.resizeStartProjection;
        nextScale = HudPosition.clamp(nextScale, scaleProperty.getMinimum(), scaleProperty.getMaximum());
        scaleProperty.setValue(nextScale);

        Layout resized = this.layout(this.dragging);
        if (resized != null) {
            // Keep the opposite corner fixed while the bottom-right handle changes size.
            this.applyPosition(this.dragging, resized, this.resizeAnchorX, this.resizeAnchorY);
            this.sessionOpen = true;
            this.sessionDirty = true;
        }
    }

    private float resizeProjection(float x, float y, float anchorX, float anchorY) {
        float vectorX = x - anchorX;
        float vectorY = y - anchorY;
        float diagonalX = Math.max(1.0F, this.resizeWidth);
        float diagonalY = Math.max(1.0F, this.resizeHeight);
        return Math.max(0.01F, (vectorX * diagonalX + vectorY * diagonalY)
                / (diagonalX * diagonalX + diagonalY * diagonalY));
    }

    public void finishEditing() {
        this.handleMouseReleased();
        if (this.sessionOpen && this.sessionDirty) Config.savePersistent();
        this.sessionOpen = false;
        this.sessionDirty = false;
    }

    public void renderOverlay(int mouseX, int mouseY) {
        if (!this.isEditing()) {
            this.finishEditing();
            return;
        }
        this.sessionOpen = true;

        List<Layout> layouts = new ArrayList<>();
        for (Element element : ELEMENTS) {
            Layout layout = this.layout(element);
            if (layout != null) layouts.add(layout);
        }

        for (Layout layout : layouts) {
            boolean scaleHovered = layout.containsScaleHandle(mouseX, mouseY);
            if (layout.needsPreview()) this.drawPreview(layout);
            this.drawCornerMarker(layout, scaleHovered);
        }
        MC.fontRendererObj.drawStringWithShadow(
                "HUD EDIT MODE  •  drag to move  •  right-click to reset",
                8.0F, 8.0F, 0xFFD4CFFE);
    }

    private Layout findLayout(int mouseX, int mouseY) {
        List<Layout> layouts = new ArrayList<>();
        for (Element element : ELEMENTS) {
            Layout layout = this.layout(element);
            if (layout != null) layouts.add(layout);
        }
        for (int i = layouts.size() - 1; i >= 0; i--) {
            Layout layout = layouts.get(i);
            if (layout.contains(mouseX, mouseY) || layout.containsScaleHandle(mouseX, mouseY)) return layout;
        }
        return null;
    }

    private Layout layout(Element element) {
        HUD hud = this.module(HUD.class);
        if (hud == null) return null;
        switch (element) {
            case ARRAYLIST: {
                HUD.ArraylistEditorLayout arraylist = hud.getArraylistEditorLayout();
                UiTransform transform = this.transform(1.0F);
                return new Layout(element, transform,
                        arraylist.getX(), arraylist.getY(), arraylist.getWidth(), arraylist.getHeight(),
                        arraylist.getVisualX(), arraylist.getVisualY(),
                        arraylist.getVisualWidth(), arraylist.getVisualHeight(), arraylist.isLive());
            }
            case WATERMARK: {
                float scale = hud.watermarkScale.getValue();
                float width = 293.0F * scale;
                float height = 85.0F * scale;
                UiTransform transform = this.transform(1.0F);
                float x = HudPosition.edgeX(hud.watermarkPosX.getValue(), DESIGN_WIDTH, width,
                        hud.watermarkOffsetX.getValue());
                float y = HudPosition.edgeY(hud.watermarkPosY.getValue(), DESIGN_HEIGHT, height,
                        hud.watermarkOffsetY.getValue());
                return new Layout(element, transform, x, y, width, height,
                        hud.isEnabled() && hud.watermark.getValue());
            }
            case TARGET_HUD: {
                TargetHUD target = this.module(TargetHUD.class);
                if (target == null) return null;
                float width = 250.0F;
                float height = 72.0F;
                UiTransform transform = this.transform(target.scale.getValue());
                float x = HudPosition.anchoredX(target.posX.getValue(), DESIGN_WIDTH, width, target.offX.getValue());
                float y = HudPosition.anchoredY(target.posY.getValue(), DESIGN_HEIGHT, height, target.offY.getValue());
                return new Layout(element, transform, x, y, width, height, target.isHudVisible());
            }
            case NOTIFICATIONS: {
                Notifications notifications = this.module(Notifications.class);
                if (notifications == null) return null;
                float width = 320.0F;
                float height = 76.0F;
                UiTransform transform = this.transform(notifications.scale.getValue());
                float x = HudPosition.edgeX(notifications.positionX.getValue(), DESIGN_WIDTH, width,
                        notifications.offsetX.getValue());
                float y = HudPosition.edgeY(notifications.positionY.getValue(), DESIGN_HEIGHT, height,
                        notifications.offsetY.getValue());
                return new Layout(element, transform, x, y, width, height,
                        notifications.isEnabled() && Myau.notificationManager != null
                                && !Myau.notificationManager.getActive().isEmpty());
            }
            case SCAFFOLD: {
                Scaffold scaffold = this.module(Scaffold.class);
                if (scaffold == null) return null;
                HUD currentHud = hud;
                float userScale = ProgressbarSizes.USER_SCALE * currentHud.progressbarSize.getValue();
                float width = ProgressbarSizes.COMPONENT_WIDTH;
                float height = ProgressbarSizes.COMPONENT_HEIGHT;
                UiTransform transform = this.transform(userScale);
                float x = HudPosition.anchoredX(scaffold.positionX.getValue(), DESIGN_WIDTH, width,
                        scaffold.offsetX.getValue());
                float y = HudPosition.anchoredY(scaffold.positionY.getValue(), DESIGN_HEIGHT, height,
                        scaffold.offsetY.getValue());
                return new Layout(element, transform, x, y, width, height,
                        scaffold.isBlockCounterVisible());
            }
            default:
                return null;
        }
    }

    private void applyPosition(Element element, Layout layout, float x, float y) {
        x = HudPosition.clamp(x, 0.0F, DESIGN_WIDTH - layout.width);
        y = HudPosition.clamp(y, 0.0F, DESIGN_HEIGHT - layout.height);
        HUD hud = this.module(HUD.class);
        switch (element) {
            case ARRAYLIST: {
                float scale = hud.scale.getValue();
                int anchorX = HudPosition.nearestEdgeX(x, layout.width, DESIGN_WIDTH);
                int anchorY = HudPosition.nearestEdgeY(y, layout.height, DESIGN_HEIGHT);
                hud.posX.setValue(anchorX);
                hud.posY.setValue(anchorY);
                hud.offsetX.setValue(Math.round(HudPosition.offsetForEdgeX(anchorX, x, DESIGN_WIDTH, layout.width) / scale));
                hud.offsetY.setValue(Math.round(HudPosition.offsetForEdgeY(anchorY, y, DESIGN_HEIGHT, layout.height) / scale));
                return;
            }
            case WATERMARK: {
                int anchorX = HudPosition.nearestEdgeX(x, layout.width, DESIGN_WIDTH);
                int anchorY = HudPosition.nearestEdgeY(y, layout.height, DESIGN_HEIGHT);
                hud.watermarkPosX.setValue(anchorX);
                hud.watermarkPosY.setValue(anchorY);
                hud.watermarkOffsetX.setValue(Math.round(HudPosition.offsetForEdgeX(anchorX, x, DESIGN_WIDTH, layout.width)));
                hud.watermarkOffsetY.setValue(Math.round(HudPosition.offsetForEdgeY(anchorY, y, DESIGN_HEIGHT, layout.height)));
                return;
            }
            case TARGET_HUD: {
                TargetHUD target = this.module(TargetHUD.class);
                int anchorX = HudPosition.nearestAnchoredX(x, layout.width, DESIGN_WIDTH);
                int anchorY = HudPosition.nearestAnchoredY(y, layout.height, DESIGN_HEIGHT);
                target.posX.setValue(anchorX);
                target.posY.setValue(anchorY);
                target.offX.setValue(Math.round(HudPosition.offsetForAnchoredX(anchorX, x, DESIGN_WIDTH, layout.width)));
                target.offY.setValue(Math.round(HudPosition.offsetForAnchoredY(anchorY, y, DESIGN_HEIGHT, layout.height)));
                return;
            }
            case NOTIFICATIONS: {
                Notifications notifications = this.module(Notifications.class);
                int anchorX = HudPosition.nearestEdgeX(x, layout.width, DESIGN_WIDTH);
                int anchorY = HudPosition.nearestEdgeY(y, layout.height, DESIGN_HEIGHT);
                notifications.positionX.setValue(anchorX);
                notifications.positionY.setValue(anchorY);
                notifications.offsetX.setValue(Math.round(HudPosition.offsetForEdgeX(anchorX, x, DESIGN_WIDTH, layout.width)));
                notifications.offsetY.setValue(Math.round(HudPosition.offsetForEdgeY(anchorY, y, DESIGN_HEIGHT, layout.height)));
                return;
            }
            case SCAFFOLD: {
                Scaffold scaffold = this.module(Scaffold.class);
                int anchorX = HudPosition.nearestAnchoredX(x, layout.width, DESIGN_WIDTH);
                int anchorY = HudPosition.nearestAnchoredY(y, layout.height, DESIGN_HEIGHT);
                scaffold.positionX.setValue(anchorX);
                scaffold.positionY.setValue(anchorY);
                scaffold.offsetX.setValue(Math.round(HudPosition.offsetForAnchoredX(anchorX, x, DESIGN_WIDTH, layout.width)));
                scaffold.offsetY.setValue(Math.round(HudPosition.offsetForAnchoredY(anchorY, y, DESIGN_HEIGHT, layout.height)));
                return;
            }
            default:
        }
    }

    private void reset(Element element) {
        HUD hud = this.module(HUD.class);
        switch (element) {
            case ARRAYLIST:
                hud.posX.setValue(0);
                hud.posY.setValue(0);
                hud.offsetX.setValue(2);
                hud.offsetY.setValue(2);
                break;
            case WATERMARK:
                hud.watermarkPosX.setValue(0);
                hud.watermarkPosY.setValue(0);
                hud.watermarkOffsetX.setValue(8);
                hud.watermarkOffsetY.setValue(8);
                break;
            case TARGET_HUD: {
                TargetHUD target = this.module(TargetHUD.class);
                target.posX.setValue(1);
                target.posY.setValue(1);
                target.offX.setValue(0);
                target.offY.setValue(40);
                break;
            }
            case NOTIFICATIONS: {
                Notifications notifications = this.module(Notifications.class);
                notifications.positionX.setValue(1);
                notifications.positionY.setValue(1);
                notifications.offsetX.setValue(16);
                notifications.offsetY.setValue(16);
                break;
            }
            case SCAFFOLD: {
                Scaffold scaffold = this.module(Scaffold.class);
                scaffold.positionX.setValue(1);
                scaffold.positionY.setValue(1);
                scaffold.offsetX.setValue(0);
                scaffold.offsetY.setValue(-10);
                break;
            }
            default:
                return;
        }
        this.sessionOpen = true;
        this.sessionDirty = true;
    }

    private UiTransform transform(float userScale) {
        return new UiTransform(MC, DESIGN_WIDTH, DESIGN_HEIGHT, userScale, 0.0F);
    }

    private FloatProperty scaleProperty(Element element) {
        HUD hud = this.module(HUD.class);
        if (hud == null) return null;
        switch (element) {
            case ARRAYLIST:
                return hud.scale;
            case WATERMARK:
                return hud.watermarkScale;
            case TARGET_HUD: {
                TargetHUD target = this.module(TargetHUD.class);
                return target == null ? null : target.scale;
            }
            case NOTIFICATIONS: {
                Notifications notifications = this.module(Notifications.class);
                return notifications == null ? null : notifications.scale;
            }
            case SCAFFOLD:
                return hud.progressbarSize;
            default:
                return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Module> T module(Class<T> type) {
        return Myau.moduleManager == null ? null : (T) Myau.moduleManager.modules.get(type);
    }

    private void drawCornerMarker(Layout layout, boolean hovered) {
        float right = layout.screenX + layout.screenWidth;
        float bottom = layout.screenY + layout.screenHeight;
        float length = Math.min(16.0F, Math.min(layout.screenWidth, layout.screenHeight) * 0.24F);
        float thickness = hovered ? 2.5F : 1.75F;
        int color = hovered ? 0xFFB7C5FF : 0xC08FA7FF;
        if (length <= 1.0F) return;

        this.drawRoundedSegment(right - length, bottom - thickness, length, thickness, color);
        this.drawRoundedSegment(right - thickness, bottom - length, thickness, length, color);
    }

    private void drawRoundedSegment(float x, float y, float width, float height, int color) {
        RenderUtil.drawRoundedRect(x, y, width, height, Math.min(width, height) * 0.5F, color);
    }

    private void drawPreview(Layout layout) {
        float x = layout.screenX;
        float y = layout.screenY;
        float width = layout.screenWidth;
        float height = layout.screenHeight;
        int panel = 0x6B1A1A24;
        int accent = 0xB28FA7FF;
        int text = 0xA8FFFFFF;
        switch (layout.element) {
            case ARRAYLIST: {
                float gap = Math.max(2.0F, Math.min(5.0F, height * 0.04F));
                float rowHeight = Math.max(4.0F, (height - gap * 2.0F) / 3.0F);
                for (int row = 0; row < 3; row++) {
                    float rowWidth = width * (1.0F - row * 0.08F);
                    float rowY = y + row * (rowHeight + gap);
                    RenderUtil.drawRoundedRect(x, rowY, rowWidth, rowHeight,
                            Math.min(5.0F, rowHeight * 0.25F), panel);
                    RenderUtil.drawRoundedRect(x + Math.min(4.0F, rowWidth * 0.05F),
                            rowY + rowHeight * 0.25F, Math.max(2.0F, Math.min(4.0F, rowWidth * 0.03F)),
                            rowHeight * 0.5F, 2.0F, accent);
                    RenderUtil.drawRoundedRect(x + rowWidth * 0.18F, rowY + rowHeight * 0.38F,
                            rowWidth * 0.38F, Math.max(2.0F, rowHeight * 0.16F), 2.0F, text);
                }
                return;
            }
            case WATERMARK:
                RenderUtil.drawRoundedRect(x, y, width, height, Math.min(8.0F, height * 0.22F), panel);
                RenderUtil.drawRoundedRect(x + width * 0.04F, y + height * 0.08F,
                        height * 0.7F, height * 0.84F, Math.min(6.0F, height * 0.18F), 0x8A1A1A24);
                MC.fontRendererObj.drawStringWithShadow("Myaulex", x + height * 0.9F,
                        y + height * 0.37F, text);
                return;
            case TARGET_HUD:
                RenderUtil.drawRoundedRect(x, y, width, height, Math.min(8.0F, height * 0.2F), panel);
                RenderUtil.drawRoundedRect(x + 6.0F, y + 8.0F, Math.min(28.0F, height - 16.0F),
                        Math.min(28.0F, height - 16.0F), 5.0F, 0x8A8FA7FF);
                RenderUtil.drawRoundedRect(x + width * 0.2F, y + height * 0.28F,
                        width * 0.42F, Math.max(2.0F, height * 0.1F), 2.0F, text);
                RenderUtil.drawRoundedRect(x + width * 0.2F, y + height * 0.62F,
                        width * 0.62F, Math.max(3.0F, height * 0.14F), 3.0F, 0xB28FA7FF);
                return;
            case NOTIFICATIONS:
                RenderUtil.drawRoundedRect(x, y, width, height, Math.min(8.0F, height * 0.2F), panel);
                RenderUtil.drawRoundedRect(x + width - 5.0F, y + 4.0F, 5.0F,
                        Math.max(2.0F, height - 8.0F), 2.0F, 0xB28FA7FF);
                RenderUtil.drawRoundedRect(x + 14.0F, y + height * 0.28F, width * 0.42F,
                        Math.max(2.0F, height * 0.11F), 2.0F, text);
                RenderUtil.drawRoundedRect(x + 14.0F, y + height * 0.58F, width * 0.62F,
                        Math.max(2.0F, height * 0.1F), 2.0F, 0x8AFFFFFF);
                return;
            case SCAFFOLD:
                RenderUtil.drawRoundedRect(x, y, width, height, Math.min(8.0F, height * 0.35F), panel);
                RenderUtil.drawRoundedRect(x + width * 0.04F, y + height * 0.3F, width * 0.92F,
                        Math.max(2.0F, height * 0.35F), Math.min(4.0F, height * 0.18F), 0x6BFFFFFF);
                RenderUtil.drawRoundedRect(x + width * 0.04F, y + height * 0.3F, width * 0.52F,
                        Math.max(2.0F, height * 0.35F), Math.min(4.0F, height * 0.18F), accent);
                return;
            default:
        }
    }

    private enum Element {
        ARRAYLIST,
        WATERMARK,
        TARGET_HUD,
        NOTIFICATIONS,
        SCAFFOLD
    }

    private enum DragMode {
        NONE,
        POSITION,
        SCALE
    }

    private static final class Layout {
        private final Element element;
        private final UiTransform transform;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float interactionX;
        private final float interactionY;
        private final float screenX;
        private final float screenY;
        private final float screenWidth;
        private final float screenHeight;
        private final boolean live;
        private final boolean onScreen;

        private Layout(Element element, UiTransform transform, float x, float y,
                       float width, float height, boolean live) {
            this(element, transform, x, y, width, height, x, y, width, height, live);
        }

        private Layout(Element element, UiTransform transform, float x, float y,
                       float width, float height, float visualX, float visualY,
                       float visualWidth, float visualHeight, boolean live) {
            this.element = element;
            this.transform = transform;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.interactionX = clampToCanvas(x, width, DESIGN_WIDTH);
            this.interactionY = clampToCanvas(y, height, DESIGN_HEIGHT);
            this.onScreen = visualX < DESIGN_WIDTH && visualX + visualWidth > 0.0F
                    && visualY < DESIGN_HEIGHT && visualY + visualHeight > 0.0F;
            float displayX = clampToCanvas(visualX, visualWidth, DESIGN_WIDTH);
            float displayY = clampToCanvas(visualY, visualHeight, DESIGN_HEIGHT);
            this.screenX = transform.getLogicalX() + displayX * transform.getLogicalScale();
            this.screenY = transform.getLogicalY() + displayY * transform.getLogicalScale();
            this.screenWidth = visualWidth * transform.getLogicalScale();
            this.screenHeight = visualHeight * transform.getLogicalScale();
            this.live = live;
        }

        private boolean needsPreview() {
            return !this.live || !this.onScreen;
        }

        private boolean containsScaleHandle(float mouseX, float mouseY) {
            float right = this.screenX + this.screenWidth;
            float bottom = this.screenY + this.screenHeight;
            float length = Math.min(16.0F, Math.min(this.screenWidth, this.screenHeight) * 0.24F);
            float thickness = 2.5F;
            float padding = Math.max(3.0F, length * 0.35F);
            if (length <= 1.0F) return false;
            boolean horizontal = mouseX >= right - length - padding && mouseX <= right + padding
                    && mouseY >= bottom - thickness - padding && mouseY <= bottom + padding;
            boolean vertical = mouseX >= right - thickness - padding && mouseX <= right + padding
                    && mouseY >= bottom - length - padding && mouseY <= bottom + padding;
            return horizontal || vertical;
        }

        private boolean contains(float mouseX, float mouseY) {
            return mouseX >= screenX && mouseX <= screenX + screenWidth
                    && mouseY >= screenY && mouseY <= screenY + screenHeight;
        }
    }

    private static float clampToCanvas(float value, float size, float canvas) {
        return HudPosition.clamp(value, 0.0F, Math.max(0.0F, canvas - size));
    }
}
