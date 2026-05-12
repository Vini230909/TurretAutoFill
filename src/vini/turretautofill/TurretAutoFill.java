package vini.turretautofill;

import arc.Core;
import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mod;

public class TurretAutoFill extends Mod {
    private boolean enabled = false;

    public TurretAutoFill() {
        Log.info("TurretAutoFill loaded.");

        TAFKeybinds.load();

        Events.run(EventType.Trigger.update, () -> {
            if(Core.input.keyTap(TAFKeybinds.toggle)){
                enabled = !enabled;

                String state = enabled ? "ON" : "OFF";
                Vars.ui.showInfoFade("Turret Auto Fill: " + state);
                Log.info("TurretAutoFill: " + state);
            }
        });
    }

    @Override
    public void loadContent() {
        Log.info("TurretAutoFill content loaded.");
    }
}