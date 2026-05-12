package vini.turretautofill;

import arc.input.KeyBind;
import arc.input.KeyCode;

public class TAFKeybinds {
    public static final KeyBind toggle = KeyBind.add(
        "toggle-turret-auto-fill",
        KeyCode.f4,
        "Turret Auto Fill"
    );

    public static void load() {
        // forces static initialization
    }
}