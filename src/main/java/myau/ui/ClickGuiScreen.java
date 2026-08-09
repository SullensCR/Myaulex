package myau.ui;

/** Shared contract for both ClickGUI implementations and modules that need to detect them. */
public interface ClickGuiScreen {
    /**
     * Returns true while a text-like editor owns keyboard focus.
     *
     * <p>Movement handlers use this to make the GUI modal only while the user
     * is entering text. Implementations that do not have inline text input
     * can keep the default value.</p>
     */
    default boolean isTextInputFocused() {
        return false;
    }
}
