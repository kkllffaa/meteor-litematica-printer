package com.kkllffaa.meteor_litematica_printer;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class LitematicaBreaker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> breakingRange = sgGeneral.add(new IntSetting.Builder()
        .name("breaking-range")
        .description("The block break range.")
        .defaultValue(2)
        .min(1).sliderMin(1)
        .max(6).sliderMax(6)
        .build()
    );

    private final Setting<Integer> breakingDelay = sgGeneral.add(new IntSetting.Builder()
        .name("breaking-delay")
        .description("Delay between breaking blocks in ticks.")
        .defaultValue(2)
        .min(0).sliderMin(0)
        .max(100).sliderMax(40)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing hand when breaking.")
        .defaultValue(false)
        .build()
    );

    private int timer;

    public LitematicaBreaker() {
        super(Addon.CATEGORY, "litematica-breaker", "Automatically breaks blocks that are incorrect or wrongly oriented according to the schematic.");
    }

    @Override
    public void onActivate() {
        onDeactivate();
    }

    @Override
    public void onDeactivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        if (worldSchematic == null) {
            toggle();
            return;
        }

        if (timer >= breakingDelay.get()) {
            BlockPos playerPos = mc.player.getBlockPos();
            for (int x = -breakingRange.get(); x <= breakingRange.get(); x++) {
                for (int y = -breakingRange.get(); y <= breakingRange.get(); y++) {
                    for (int z = -breakingRange.get(); z <= breakingRange.get(); z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        BlockState currentState = mc.world.getBlockState(pos);
                        BlockState requiredState = worldSchematic.getBlockState(pos);

                        if (!currentState.isAir() && !requiredState.isAir() && !currentState.equals(requiredState)) {
                            mc.interactionManager.attackBlock(pos, mc.player.getHorizontalFacing());
                            if (swing.get()) {
                                mc.player.swingHand(mc.player.getActiveHand());
                            }
                            timer = 0;
                            return;
                        }
                    }
                }
            }
        } else {
            timer++;
        }
    }
}
