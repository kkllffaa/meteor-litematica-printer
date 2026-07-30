package com.kkllffaa.meteor_litematica_printer;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Shredder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWorkMode = settings.createGroup("Work Mode");
    private final SettingGroup sgRender = settings.createGroup("Render");

    public enum Mode { ALL, WRONG_BLOCK, WRONG_STATE, EXTRA }
    public enum FilterMode { NONE, WHITELIST, BLACKLIST }

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("Nuke range.").defaultValue(5.0)
        .min(1).sliderMin(1).max(20).build());

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range").description("Range through walls.").defaultValue(6.0)
        .min(0).sliderMin(0).max(6).build());

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay").description("Delay between breaks.").defaultValue(0.0)
        .min(0).sliderMin(0).max(100).sliderMax(20).build());

    private final Setting<Integer> bpt = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick").description("Max blocks per tick.").defaultValue(100)
        .min(1).sliderMin(1).max(100).build());

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("What to nuke.").defaultValue(Mode.ALL).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").description("Rotate to target block.").defaultValue(true).build());

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing").description("Swing hand.").defaultValue(true).build());

    private final Setting<Boolean> silkTouch = sgGeneral.add(new BoolSetting.Builder()
        .name("silk-touch").description("Prefer Silk Touch tool.").defaultValue(false).build());

    private final Setting<FilterMode> listMode = sgWorkMode.add(new EnumSetting.Builder<FilterMode>()
        .name("list-mode").description("Block list mode.").defaultValue(FilterMode.NONE).build());

    private final Setting<List<Block>> filterBlocks = sgWorkMode.add(new BlockListSetting.Builder()
        .name("filter-blocks").description("Blocks to whitelist or blacklist.")
        .visible(() -> listMode.get() != FilterMode.NONE).build());

    private final Setting<Boolean> showBroken = sgRender.add(new BoolSetting.Builder()
        .name("broken-blocks").description("Show recently broken blocks.").defaultValue(true).build());

    private final Setting<ShapeMode> nukerBlockMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("nuker-block-mode").description("How broken blocks are rendered.")
        .defaultValue(ShapeMode.Both).visible(showBroken::get).build());

    private final Setting<SettingColor> nukerBlockSideColor = sgRender.add(new ColorSetting.Builder()
        .name("block-side-color").description("Broken block side color.")
        .defaultValue(new SettingColor(255, 0, 0, 80)).visible(showBroken::get).build());

    private final Setting<SettingColor> nukerBlockLineColor = sgRender.add(new ColorSetting.Builder()
        .name("block-line-color").description("Broken block line color.")
        .defaultValue(new SettingColor(255, 0, 0, 255)).visible(showBroken::get).build());

    private int timer;
    private final List<BlockPos> toBreak = new ArrayList<>();
    private final List<BlockPos> brokenBlocks = new ArrayList<>();
    private int brokenBlockLife;

    public Shredder() {
        super(Addon.CATEGORY, "shredder", "Breaks blocks based on Litematica schematic.");
    }

    @Override
    public void onActivate() { timer = 0; toBreak.clear(); brokenBlocks.clear(); brokenBlockLife = 0; }

    @Override
    public void onDeactivate() { toBreak.clear(); brokenBlocks.clear(); }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
        if (ws == null) { toggle(); return; }

        if (brokenBlockLife > 0) brokenBlockLife--;
        if (brokenBlockLife <= 0) brokenBlocks.clear();

        if (timer < delay.get()) { timer++; return; }

        if (silkTouch.get()) equipSilkTool();

        toBreak.clear();
        Mode m = mode.get();
        double r = range.get();
        double wr = wallsRange.get();

        BlockIterator.register((int) Math.ceil(r + 1), (int) Math.ceil(r + 1), (pos, worldState) -> {
            if (worldState.isAir()) return;

            BlockState schemState = ws.getBlockState(pos);
            if (!schemState.isAir()) { /* inside schematic */ }
            else { if (!isInsideAnySchematic(pos)) return; }

            if (!shouldBreak(m, schemState, worldState)) return;

            if (mc.player.getBoundingBox().intersects(Vec3.atLowerCornerOf(pos), Vec3.atLowerCornerOf(pos).add(1,1,1))) return;

            FilterMode fm = listMode.get();
            if (fm != FilterMode.NONE) {
                boolean inList = filterBlocks.get().contains(worldState.getBlock());
                if (fm == FilterMode.BLACKLIST ? inList : !inList) return;
            }

            double dist = mc.player.getEyePosition().distanceTo(pos.getCenter());
            if (!isBlockVisible(pos) && dist > wr) return;

            toBreak.add(new BlockPos(pos));
        });

        BlockIterator.after(() -> {
            int broken = 0;
            for (BlockPos pos : toBreak) {
                boolean canInsta = mc.player.isCreative() || BlockUtils.canInstaBreak(pos);
                if (rotate.get())
                    Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> BlockUtils.breakBlock(pos, swing.get()));
                else BlockUtils.breakBlock(pos, swing.get());

                if (showBroken.get()) {
                    brokenBlocks.add(new BlockPos(pos));
                    brokenBlockLife = 20;
                }

                broken++;
                if (!canInsta || broken >= bpt.get()) break;
            }
        });

        timer = 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (showBroken.get()) {
            for (BlockPos pos : brokenBlocks) {
                event.renderer.box(pos, nukerBlockSideColor.get(), nukerBlockLineColor.get(), nukerBlockMode.get(), 0);
            }
        }
    }

    private boolean isBlockVisible(BlockPos pos) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 blockCenter = pos.getCenter();
        return mc.level.clip(new net.minecraft.world.level.ClipContext(
            eyePos, blockCenter,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            mc.player
        )).getBlockPos().equals(pos);
    }

    private void equipSilkTool() {
        if (Utils.getEnchantmentLevel(mc.player.getMainHandItem(), Enchantments.SILK_TOUCH) > 0) return;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (Utils.getEnchantmentLevel(mc.player.getInventory().getItem(i), Enchantments.SILK_TOUCH) > 0) {
                bestSlot = i;
                break;
            }
        }
        if (bestSlot != -1) InvUtils.swap(bestSlot, false);
    }

    private boolean isInsideAnySchematic(BlockPos pos) {
        SchematicPlacementManager spm = DataManager.getSchematicPlacementManager();
        for (SchematicPlacement sp : spm.getAllSchematicsPlacements()) {
            BlockPos origin = sp.getOrigin();
            var schematic = sp.getSchematic();
            if (schematic == null) continue;
            var size = schematic.getTotalSize();
            if (pos.getX() >= origin.getX() && pos.getX() < origin.getX() + size.getX() &&
                pos.getY() >= origin.getY() && pos.getY() < origin.getY() + size.getY() &&
                pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + size.getZ()) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldBreak(Mode m, BlockState schem, BlockState world) {
        if (schem.isAir()) return m == Mode.EXTRA || m == Mode.ALL;
        if (world.isAir()) return false;
        if (world.getBlock() != schem.getBlock()) return m == Mode.WRONG_BLOCK || m == Mode.ALL;
        if (m == Mode.WRONG_STATE || m == Mode.ALL) {
            for (var prop : schem.getProperties()) {
                if (!world.hasProperty(prop)) return true;
                if (!schem.getValue(prop).equals(world.getValue(prop))) return true;
            }
        }
        return false;
    }
}
