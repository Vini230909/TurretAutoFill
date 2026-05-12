package vini.turretautofill;

import arc.Core;
import arc.Events;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.mod.Mod;
import mindustry.type.Item;
import mindustry.world.blocks.defense.turrets.ItemTurret;

public class TurretAutoFill extends Mod {
    private static final boolean DEBUG = true;

    private static final float TRANSFER_DELAY = 10f;
    private static final float TRANSFER_CONFIRM_TIMEOUT = 12f;
    private static final int MIN_TRANSFER = 1;

    private boolean enabled = false;
    private float transferTimer = 0f;

    private boolean waitingForTransfer = false;
    private float transferConfirmTimer = 0f;
    private int heldAmountBeforeTransfer = 0;

    private int lastTurretCount = 0;
    private int lastCompatibleCount = 0;
    private int lastFilledCount = 0;

    private final Seq<Building> builds = new Seq<>(false);

    private Table debugTable;
    private Label debugLabel;

    public TurretAutoFill() {
        Log.info("TurretAutoFill loaded.");

        TAFKeybinds.load();

        Events.on(EventType.ClientLoadEvent.class, event -> {
            createDebugUi();
        });

        Events.run(EventType.Trigger.update, () -> {
            if(Core.input.keyTap(TAFKeybinds.toggle)){
                enabled = !enabled;

                if(enabled){
                    showToast("Auto Fill: [lightgray]Enabled");
                }else{
                    showToast("Auto Fill: [scarlet]Disabled");
                    lastFilledCount = 0;
                }

                Log.info("TurretAutoFill: " + (enabled ? "Enabled" : "Disabled"));
            }

            updateAutoFill();
            updateDebugUi();
        });
    }

    private void createDebugUi() {
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        debugTable = new Table(Tex.button);
        debugTable.touchable = Touchable.disabled;
        debugTable.visible = false;

        debugLabel = new Label("");
        debugTable.add(debugLabel).pad(6f, 10f, 6f, 10f);

        Vars.ui.hudGroup.addChild(debugTable);
    }

    private void updateAutoFill() {
        if(!enabled || !Vars.state.isGame() || Vars.player == null || Vars.player.dead()){
            return;
        }

        Unit unit = Vars.player.unit();

        if(unit == null){
            return;
        }

        Item heldItem = unit.item();
        int heldAmount = unit.stack.amount;

        if(heldItem == null || heldAmount <= 0){
            return;
        }
        if(waitingForTransfer){
            transferConfirmTimer += Time.delta;

            if(unit.item() == null || unit.stack.amount < heldAmountBeforeTransfer || transferConfirmTimer >= TRANSFER_CONFIRM_TIMEOUT){
                waitingForTransfer = false;
            transferConfirmTimer = 0f;
        }else{
            return;
            }
        }

        if(Vars.player.team() == null || Vars.player.team().data() == null || Vars.player.team().data().buildingTree == null){
            return;
        }

        float range = Vars.itemTransferRange;
        float px = Vars.player.x;
        float py = Vars.player.y;
        
        lastTurretCount = 0;
        lastCompatibleCount = 0;

        builds.clear();

        Vars.player.team().data().buildingTree.intersect(
            px - range,
            py - range,
            range * 2f,
            range * 2f,
            builds
        );

        Building bestTarget = null;
        int bestCurrentAmount = Integer.MAX_VALUE;

        for(Building build : builds){
            if(build == null) continue;
            if(build.team != Vars.player.team()) continue;
            if(!(build.block instanceof ItemTurret)) continue;
            if(!Vars.player.within(build, range)) continue;

            lastTurretCount++;

            if(!build.block.consumesItem(heldItem)) continue;

            int accepted = build.acceptStack(heldItem, heldAmount, unit);
            if(accepted < MIN_TRANSFER) continue;

            lastCompatibleCount++;

            int currentAmount = build.items == null ? 0 : build.items.get(heldItem);

            if(bestTarget == null || currentAmount < bestCurrentAmount){
                bestTarget = build;
                bestCurrentAmount = currentAmount;
            }
        }

        transferTimer += Time.delta;

        if(bestTarget == null){
            return;
        }

        if(transferTimer < TRANSFER_DELAY){
            return;
        }

        transferTimer = 0f;

        Call.transferInventory(Vars.player, bestTarget);
        lastFilledCount++;

        waitingForTransfer = true;
        transferConfirmTimer = 0f;
        heldAmountBeforeTransfer = heldAmount;
    }

    private void updateDebugUi() {
        if(!DEBUG){
            if(debugTable != null) debugTable.visible = false;
            return;
        }

        if(debugTable == null || debugLabel == null) return;

        if(!enabled || !Vars.state.isGame() || Vars.player == null || Vars.player.dead()){
            debugTable.visible = false;
            return;
        }

        Unit unit = Vars.player.unit();

        if(unit == null){
            debugTable.visible = false;
            return;
        }

        Item item = unit.item();
        String held = item == null ? "None" : item.localizedName;
        int amount = unit.stack.amount;

        debugLabel.setText(
            "Auto Fill: [lightgray]ON\n" +
            "Turrets: [lightgray]" + lastTurretCount + "\n" +
            "Compatible: [lightgray]" + lastCompatibleCount + "\n" +
            "Filled: [lightgray]" + lastFilledCount + "\n" +
            "Held: [lightgray]" + held + " x" + amount
        );

        debugTable.pack();

        float screenW = Core.scene.getWidth();
        float screenH = Core.scene.getHeight();

        float rightPadding = 50f;
        float x = screenW - debugTable.getWidth() - rightPadding;
        float y = screenH / 2f - debugTable.getHeight() / 2f;

        debugTable.setPosition(x, y);
        debugTable.visible = true;
        debugTable.toFront();
    }

    private void showToast(String text) {
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        Table toast = new Table(Tex.button);
        toast.touchable = Touchable.disabled;
        toast.add(new Label(text)).pad(4f, 10f, 4f, 10f);
        toast.pack();

        float screenW = Core.scene.getWidth();
        float screenH = Core.scene.getHeight();

        float centerX = screenW / 2f;

        float startCenterY = screenH - 150f;
        float visibleCenterY = screenH - 200f;

        float x = centerX - toast.getWidth() / 2f;
        float startY = startCenterY - toast.getHeight() / 2f;
        float visibleY = visibleCenterY - toast.getHeight() / 2f;

        toast.setPosition(x, startY);
        toast.color.a = 0f;

        Vars.ui.hudGroup.addChild(toast);
        toast.toFront();

        toast.actions(
            Actions.sequence(
                Actions.parallel(
                    Actions.alpha(1f, 0.30f, Interp.pow3Out),
                    Actions.moveTo(x, visibleY, 0.30f, Interp.pow3Out)
                ),
                Actions.delay(0.2f),
                Actions.parallel(
                    Actions.alpha(0f, 0.35f, Interp.pow3In),
                    Actions.moveTo(x, startY, 0.35f, Interp.pow3In)
                ),
                Actions.remove()
            )
        );
    }

    @Override
    public void loadContent() {
        Log.info("TurretAutoFill content loaded.");
    }
}