package myau.module.modules;

import myau.event.EventTarget;
import myau.events.KeyEvent;
import myau.module.Module;
import myau.property.properties.KeyBindProperty;
import myau.property.properties.ModeProperty;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;

public class FastQueue extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int HYLEX = 0;

    public final ModeProperty mode = new ModeProperty("mode", HYLEX, new String[]{"Hylex"});
    public final KeyBindProperty solos = new KeyBindProperty("solos", 0,
            () -> this.mode.getValue() == HYLEX);
    public final KeyBindProperty duos = new KeyBindProperty("duos", 0,
            () -> this.mode.getValue() == HYLEX);

    public FastQueue() {
        super("FastQueue", false);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (!this.isEnabled()
                || event.getKey() == this.getKey()
                || mc.currentScreen != null
                || mc.theWorld == null
                || mc.thePlayer == null) {
            return;
        }

        if (event.getKey() == this.solos.getValue() && this.solos.getValue() != 0) {
            ChatUtil.sendMessage("/play bw_solo");
        } else if (event.getKey() == this.duos.getValue() && this.duos.getValue() != 0) {
            ChatUtil.sendMessage("/play bw_duo");
        }
    }
}
