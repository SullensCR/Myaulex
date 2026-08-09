package myau.render.ui;

import net.minecraft.client.renderer.texture.DynamicTexture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public final class UiTexture {
    private final DynamicTexture texture;
    private final int width;
    private final int height;

    public UiTexture(String resourcePath) {
        try {
            BufferedImage image;
            if (resourcePath.toLowerCase(java.util.Locale.ROOT).endsWith(".svg")) {
                image = SvgRasterizer.render(resourcePath);
            } else {
                try (InputStream input = UiResource.open(resourcePath)) {
                    image = ImageIO.read(input);
                }
            }
            if (image == null) throw new IOException("Unsupported UI image: /assets/myau/" + resourcePath);
            width = image.getWidth();
            height = image.getHeight();
            texture = new DynamicTexture(image);
            UiTextureSampling.configure(texture);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load UI texture /assets/myau/" + resourcePath, e);
        }
    }

    public UiTexture(BufferedImage image) {
        if (image == null) throw new IllegalArgumentException("image");
        width = image.getWidth();
        height = image.getHeight();
        texture = new DynamicTexture(image);
        UiTextureSampling.configure(texture);
    }

    public int id() {
        return texture.getGlTextureId();
    }

    public float aspect() {
        return width / (float) height;
    }

    public void delete() {
        texture.deleteGlTexture();
    }
}
