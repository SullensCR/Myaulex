package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SoundUtil {
    public static final String TOGGLE_ON_SOUND = "myau:toggle-on";
    public static final String TOGGLE_OFF_SOUND = "myau:toggle-off";
    public static final String BED_WHITELIST_SOUND = "myau:bed-whitelist";
    public static final String NOTIFICATION_SOUND = "myau:notification";

    private static final Logger LOGGER = LogManager.getLogger("Myaulex-Sound");
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void playSound(String soundName) {
        SoundHandler soundHandler = mc.getSoundHandler();
        if (soundHandler == null) {
            LOGGER.warn("Cannot play sound {} because Minecraft has no sound handler", soundName);
            return;
        }

        ResourceLocation soundLocation = new ResourceLocation(soundName);
        if (soundHandler.getSound(soundLocation) == null) {
            LOGGER.warn("Sound event {} is not registered", soundName);
            return;
        }

        PositionedSoundRecord positionedSoundRecord = PositionedSoundRecord.create(soundLocation);
        soundHandler.playSound(positionedSoundRecord);
    }
}
