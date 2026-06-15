package myau.module.modules;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoCaptcha extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Pattern SKIN_PATTERN = Pattern.compile("eyJ0Z.*");
    private static final Gson gson = new Gson();

    // Captcha index: animal name -> texture URL
    private static final Map<String, String> CAPTCHA_INDEX = new HashMap<>();

    static {
        // Populate with known texture URLs for different animals
        CAPTCHA_INDEX.put("Vaca", "http://textures.minecraft.net/texture/5d6c6eda942f7f5f71c3161c7306f4aed307d82895f9d2b07ab4525718edc5");
        CAPTCHA_INDEX.put("Galinha", "http://textures.minecraft.net/texture/11a2c3ce5e50b9718cbdb6149827d35a737f71f0136cb3e95a156e489e8da62");
        CAPTCHA_INDEX.put("Sapo", "http://textures.minecraft.net/texture/e155412fd11dcd0aba77a18378583537e7625c6f71fe452f7a717dae3326f22a");
        CAPTCHA_INDEX.put("Pinguim", "http://textures.minecraft.net/texture/d3c57facbb3a4db7fd55b5c0dc7d19c19cb0813c748ccc9710c714727551f5b9");
        CAPTCHA_INDEX.put("Porco", "http://textures.minecraft.net/texture/eaf8b5abd25c2092ac09692b1325df9b6c907cd5bd54587e8aa3a8153cfed");
        CAPTCHA_INDEX.put("Raposa", "http://textures.minecraft.net/texture/24a0347436434eb13d537b9eb6b45b6ef4c5a78f86e91863ef61d2b8a53b82");
        CAPTCHA_INDEX.put("Abelha", "http://textures.minecraft.net/texture/5162dd0b9f65b58a1e70f81d8e03e8ff6c53e4e985bdbe0186558d8a69a81189");
    }

    private int joinTicks = 0;
    private boolean isHibernating = false;
    private static final int HIBERNATION_DELAY = 300; // 15 seconds at 20 ticks/sec

    public AutoCaptcha() {
        super("AutoCaptcha", false, true); // Hidden from array list
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        if (!this.isEnabled()) return;

        // Reset hibernation and start monitoring
        this.isHibernating = false;
        this.joinTicks = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) return;

        joinTicks++;

        // If hibernating, check if we should skip
        if (this.isHibernating) {
            return;
        }

        // If no action within 15 seconds, go into hibernation
        if (joinTicks > HIBERNATION_DELAY) {
            this.isHibernating = true;
            return;
        }

        // Check if we're looking at a captcha chest
        if (mc.currentScreen instanceof GuiChest) {
            GuiChest guiChest = (GuiChest) mc.currentScreen;
            Container container = guiChest.inventorySlots;

            if (container instanceof ContainerChest) {
                ContainerChest chest = (ContainerChest) container;
                IInventory inventory = chest.getLowerChestInventory();
                String inventoryName = inventory.getName();

                // Try to solve the captcha
                if (solveCaptcha(inventoryName, inventory, chest.windowId)) {
                    this.joinTicks = 0; // Reset timer after successful action
                }
            }
        }
    }

    private boolean solveCaptcha(String containerName, IInventory inventory, int windowId) {
        // Extract animal name from container title
        // Format: "Clique no(a): Raposa"
        String animalName = extractAnimalName(containerName);
        if (animalName == null || animalName.isEmpty()) {
            return false;
        }

        // Get the texture URL from index
        String targetUrl = CAPTCHA_INDEX.get(animalName);
        if (targetUrl == null) {
            return false;
        }

        // Search through inventory items for player heads with matching texture
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack itemStack = inventory.getStackInSlot(i);
            if (itemStack == null) continue;

            // Check if it's a player head (skull)
            if (!isPlayerHead(itemStack)) continue;

            // Extract and decode skin value
            String decodedUrl = extractTextureUrl(itemStack);
            if (decodedUrl != null && decodedUrl.equals(targetUrl)) {
                // Found matching item, click it
                performClick(windowId, i);
                return true;
            }
        }

        return false;
    }

    private String extractAnimalName(String containerName) {
        // Extract animal name from "Clique no(a): Raposa" format
        String[] parts = containerName.split(":");
        if (parts.length > 1) {
            return parts[1].trim();
        }
        return null;
    }

    private boolean isPlayerHead(ItemStack itemStack) {
        if (itemStack == null) return false;
        // Check if item is a skull (ID 397 in 1.8.9)
        return itemStack.getItem().getUnlocalizedName().contains("skull") ||
               itemStack.getItem().getUnlocalizedName().equals("tile.skull");
    }

    private String extractTextureUrl(ItemStack itemStack) {
        try {
            NBTTagCompound tag = itemStack.getTagCompound();
            if (tag == null) return null;

            // Navigate to GameProfile tag
            if (!tag.hasKey("SkullOwner")) return null;

            NBTTagCompound skullOwner = tag.getCompoundTag("SkullOwner");
            if (!skullOwner.hasKey("Properties")) return null;

            NBTTagCompound properties = skullOwner.getCompoundTag("Properties");
            if (!properties.hasKey("textures")) return null;

            NBTTagList texturesList = properties.getTagList("textures", 10); // 10 = TAG_COMPOUND
            if (texturesList.tagCount() == 0) return null;

            NBTTagCompound textureTag = texturesList.getCompoundTagAt(0);
            if (!textureTag.hasKey("Value")) return null;

            String skinValueBBSF = textureTag.getString("Value");

            // Decode base64
            if (!skinValueBBSF.matches("eyJ0Z.*")) {
                // If it doesn't match pattern, try to find the pattern
                Matcher matcher = SKIN_PATTERN.matcher(skinValueBBSF);
                if (matcher.find()) {
                    skinValueBBSF = matcher.group();
                } else {
                    return null;
                }
            }

            byte[] decodedBytes = Base64.getDecoder().decode(skinValueBBSF);
            String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

            // Parse JSON to extract URL
            try {
                JsonObject jsonObject = gson.fromJson(decodedJson, JsonObject.class);
                if (jsonObject.has("textures")) {
                    JsonObject textures = jsonObject.getAsJsonObject("textures");
                    if (textures.has("SKIN")) {
                        JsonObject skin = textures.getAsJsonObject("SKIN");
                        if (skin.has("url")) {
                            return skin.get("url").getAsString();
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void performClick(int windowId, int slotId) {
        // Perform click on slot
        try {
            mc.playerController.windowClick(windowId, slotId, 0, 0, mc.thePlayer);
            ChatUtil.sendFormatted(String.format("%s&aAutoCaptcha: Clicked slot %d&r", Myau.clientName, slotId));
        } catch (Exception e) {
            // Silently fail
        }
    }

    @Override
    public void onDisabled() {
        this.isHibernating = false;
        this.joinTicks = 0;
    }
}



