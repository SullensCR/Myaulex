package myau.render.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads UI assets from the mod jar without depending on Forge 1.8.9 registering
 * this coremod as a resource-pack domain.
 */
final class UiResource {
    private UiResource() {
    }

    static InputStream open(String path) throws IOException {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String classpathPath = "/assets/myau/" + normalized;

        InputStream stream = UiResource.class.getResourceAsStream(classpathPath);
        if (stream != null) return stream;

        ClassLoader loader = UiResource.class.getClassLoader();
        if (loader != null) {
            stream = loader.getResourceAsStream(classpathPath.substring(1));
            if (stream != null) return stream;
        }

        try {
            return Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation("myau", normalized))
                    .getInputStream();
        } catch (IOException resourceManagerFailure) {
            FileNotFoundException failure = new FileNotFoundException(
                    "UI resource was not found in the mod jar or Minecraft resource manager: " + classpathPath
            );
            failure.initCause(resourceManagerFailure);
            throw failure;
        }
    }
}
