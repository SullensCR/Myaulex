package myau.property.properties;

import com.google.gson.JsonObject;
import myau.property.Property;
import myau.util.KeyBindUtil;

import java.util.function.BooleanSupplier;

/** A configurable keyboard or mouse binding used by an individual module setting. */
public class KeyBindProperty extends Property<Integer> {
    public KeyBindProperty(String name, Integer value) {
        this(name, value, null);
    }

    public KeyBindProperty(String name, Integer value, BooleanSupplier visibleChecker) {
        super(name, value, KeyBindProperty::isValidKey, visibleChecker);
    }

    @Override
    public String getValuePrompt() {
        return "key";
    }

    @Override
    public String formatValue() {
        return "&e" + KeyBindUtil.getKeyName(this.getValue());
    }

    @Override
    public boolean parseString(String string) {
        try {
            return this.setValue(Integer.parseInt(string));
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        return this.setValue(jsonObject.get(this.getName()).getAsInt());
    }

    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), this.getValue());
    }

    private static boolean isValidKey(Integer keyCode) {
        return keyCode != null && keyCode >= -100 && keyCode < 256;
    }
}
