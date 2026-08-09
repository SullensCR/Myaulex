package myau.render.ui;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertTrue;

public class SvgRasterizerTest {
    @Test
    public void suppliedNotificationVectorsRasterizeFromClasspath() {
        String[] icons = {
                "Info", "Warning", "Error", "Analyze", "Config-Error",
                "Config-Success", "Config-Edit", "Enabled", "Disabled"
        };
        for (String icon : icons) {
            BufferedImage image = SvgRasterizer.render("notifications/Icons/Icon=" + icon + ".svg");
            assertTrue(icon, image.getWidth() > 0 && image.getHeight() > 0);
        }
        BufferedImage track = SvgRasterizer.render("notifications/throbber/Filled Track.svg");
        assertTrue(track.getWidth() > 0 && track.getHeight() > 0);
    }
}
