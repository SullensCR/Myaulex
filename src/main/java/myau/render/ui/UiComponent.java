package myau.render.ui;

public interface UiComponent {
    void render(UiRenderer renderer, float mouseX, float mouseY);

    default boolean mouseClicked(float mouseX, float mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(float mouseX, float mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(float mouseX, float mouseY, int button) {
        return false;
    }

    default boolean keyTyped(char character, int keyCode) {
        return false;
    }
}
