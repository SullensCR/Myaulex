package myau.ui.components;

import myau.Myau;
import myau.module.modules.HUD;
import myau.property.properties.KeyBindProperty;
import myau.ui.Component;
import myau.ui.dataset.BindStage;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicInteger;

/** Legacy ClickGUI row for binding an individual module property. */
public class PropertyBindComponent implements Component {
    private final KeyBindProperty property;
    private final ModuleComponent parentModule;
    private boolean binding;
    private int offsetY;
    private int x;
    private int y;

    public PropertyBindComponent(KeyBindProperty property, ModuleComponent parentModule, int offsetY) {
        this.property = property;
        this.parentModule = parentModule;
        this.offsetY = offsetY;
        this.x = parentModule.category.getX();
        this.y = parentModule.category.getY() + offsetY;
    }

    @Override
    public void draw(AtomicInteger offset) {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String text = binding ? BindStage.binding : "Keybind to Whitelist: " + KeyBindUtil.getKeyName(property.getValue());
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text,
                (float) ((parentModule.category.getX() + 4) * 2),
                (float) ((parentModule.category.getY() + offsetY + 3) * 2),
                ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis(), offset.get()).getRGB());
        GL11.glPopMatrix();
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
        this.x = parentModule.category.getX();
        this.y = parentModule.category.getY() + offsetY;
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0 && parentModule.panelExpand) {
            binding = !binding;
        } else if (binding && parentModule.panelExpand && button != 0) {
            property.setValue(button - 100);
            binding = false;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
        if (!binding) return;
        property.setValue(keyCode == 1 || keyCode == 11 ? 0 : keyCode);
        binding = false;
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return 12;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }

    private boolean isHovered(int mouseX, int mouseY) {
        return mouseX > x && mouseX < x + parentModule.category.getWidth()
                && mouseY > y - 1 && mouseY < y + 12;
    }
}
