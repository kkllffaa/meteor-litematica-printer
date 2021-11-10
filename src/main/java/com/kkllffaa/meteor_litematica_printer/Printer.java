package com.kkllffaa.meteor_litematica_printer;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.SchematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.SchematicUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Printer extends Module {


	private final SettingGroup sgGeneral = settings.getDefaultGroup();

	//region settings

	private final Setting<Integer> printing_range = sgGeneral.add(new IntSetting.Builder()
			.name("printing-range")
			.description("Printing block place range.")
			.defaultValue(2)
			.min(1).sliderMin(1)
			.max(6).sliderMax(6)
			.build()
	);

	private final Setting<Integer> printing_delay = sgGeneral.add(new IntSetting.Builder()
			.name("printing-delay")
			.description("Delay between printing blocks in ticks.")
			.defaultValue(2)
			.min(0).sliderMin(0)
			.max(100).sliderMax(40)
			.build()
	);

	private final Setting<Integer> bpt = sgGeneral.add(new IntSetting.Builder()
			.name("blocks/tick")
			.description("how many blocks place in 1 tick.")
			.defaultValue(10)
			.min(1).sliderMin(1)
			.max(100).sliderMax(100)
			.build()
	);

	private final Setting<Boolean> advenced = sgGeneral.add(new BoolSetting.Builder()
			.name("advanced")
			.description("respect block rotation (places blocks in weird places only in singleplayer, multiplayer works fine).")
			.defaultValue(false)
			.build()
	);

	private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
			.name("swing")
			.description("swing hand when placing.")
			.defaultValue(false)
			.build()
	);

    private final Setting<Boolean> returnhand = sgGeneral.add(new BoolSetting.Builder()
        .name("Return slot")
        .description("Return to old slot.")
        .defaultValue(false)
        .build()
    );

	private final Setting<Boolean> renderblocks = sgGeneral.add(new BoolSetting.Builder()
			.name("Render placed blocks")
			.description("Render cube when placing block.")
			.defaultValue(false)
			.build()
	);

	private final Setting<Integer> fadetime = sgGeneral.add(new IntSetting.Builder()
			.name("Fade time")
			.description("in ticks.")
			.defaultValue(2)
			.min(1).sliderMin(1)
			.max(1000).sliderMax(100)
			.visible(renderblocks::get)
			.build()
	);

	private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
			.name("Color")
			.description("cubes color.")
			.defaultValue(new SettingColor(100, 100, 100))
			.visible(renderblocks::get)
			.build()
	);

    private final Setting<Boolean> rotateblocks = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Look at the blocks being placed")
        .defaultValue(false)
        .build()
    );

	//endregion


	public Printer() {
		super(Addon.CATEGORY, "litematica-printer", "description");
	}

	private int timer, placed = 0;
	private int usedslot = -1;



	private final List<Pair<Integer, BlockPos>> placed_fade = new ArrayList<>();

	@Override
	public void onDeactivate() {
		placed_fade.clear();
	}

	@EventHandler @SuppressWarnings("unused")
	private void onTick(TickEvent.Post event) {
		if (mc.player == null || mc.world == null) {
			placed_fade.clear();
			return;
		}

		placed_fade.forEach(s -> s.setLeft(s.getLeft()-1));
		placed_fade.removeIf(s -> s.getLeft() <= 0);

		WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
		if (worldSchematic == null) {
			placed_fade.clear();
			toggle();
			return;
		}
		if (timer >= printing_delay.get()) {
			BlockIterator.register(printing_range.get(), printing_range.get(), (pos, blockState) -> {
				if (!mc.player.getBlockPos().isWithinDistance(pos, printing_range.get()) || !blockState.isAir()) return;
				BlockState required = worldSchematic.getBlockState(pos);

				if (!required.isAir() && blockState.getBlock() != required.getBlock()) {
					if (swichitem(required.getBlock().asItem(), () -> place(required, pos, advenced.get(), swing.get()))) {
						placed++;
						if (renderblocks.get())
							placed_fade.add(new Pair<>(fadetime.get(), new BlockPos(pos)));
						if (placed >= bpt.get()) {
							BlockIterator.disableCurrent();
							placed = 0;
						}
						timer = 0;
					}
				}
			});
		}else timer++;
	}

	public boolean place(BlockState required, BlockPos pos, boolean adv, boolean _swing) {
		if (mc.player == null || mc.world == null) return false;

		if (mc.world.isAir(pos) || mc.world.getBlockState(pos).getMaterial().isLiquid()) {
			Direction direction = dir(required);

			if (!adv || direction == Direction.UP) {
				return BlockUtils.place(pos, Hand.MAIN_HAND, mc.player.getInventory().selectedSlot, false, 50, _swing, true, false);
			}else {
				return MyUtils.place(pos, direction, _swing, rotateblocks.get());
			}

		}else return false;
	}

	private boolean swichitem(Item item, Supplier<Boolean> action) {
		if (mc.player == null) return false;
		int a = mc.player.getInventory().selectedSlot;
		FindItemResult result = InvUtils.find(item);
		if (mc.player.getMainHandStack().getItem() == item) {
			if (action.get()) {
				usedslot = mc.player.getInventory().selectedSlot;
				return true;
			}else return false;
		} else if (usedslot != -1 && mc.player.getInventory().getStack(usedslot).getItem() == item) {
			InvUtils.swap(usedslot, returnhand.get());
			if (action.get()) {
				InvUtils.swap(a, returnhand.get());
				return true;
			}else {
				InvUtils.swap(a, returnhand.get());
				return false;
			}
		}else if (result.found()) {
			if (result.isHotbar()) {
				InvUtils.swap(result.getSlot(), returnhand.get());
				if (action.get()) {
					usedslot = mc.player.getInventory().selectedSlot;
					InvUtils.swap(a, returnhand.get());
					return true;
				}else {
					InvUtils.swap(a, returnhand.get());
					return false;
				}
			}else if (result.isMain()){
				FindItemResult empty = InvUtils.findEmpty();
				if (empty.found() && empty.isHotbar()) {
					InvUtils.move().from(result.getSlot()).toHotbar(empty.getSlot());
					InvUtils.swap(empty.getSlot(), returnhand.get());
					if (action.get()) {
						usedslot = mc.player.getInventory().selectedSlot;
						InvUtils.swap(a, returnhand.get());
						return true;
					}else {
						InvUtils.swap(a, returnhand.get());
						return false;
					}
				} else if (usedslot != -1) {
					InvUtils.move().from(result.getSlot()).toHotbar(usedslot);
					InvUtils.swap(usedslot, returnhand.get());
					if (action.get()) {
						InvUtils.swap(a, returnhand.get());
						return true;
					}else {
						InvUtils.swap(a, returnhand.get());
						return false;
					}
				}else return false;
			}else return false;
		}else return false;
	}

	private Direction dir(BlockState state) {
		if (state.contains(Properties.FACING)) return state.get(Properties.FACING);
		else if (state.contains(Properties.AXIS)) return Direction.from(state.get(Properties.AXIS), Direction.AxisDirection.POSITIVE);
		else if (state.contains(Properties.HORIZONTAL_AXIS)) return Direction.from(state.get(Properties.HORIZONTAL_AXIS), Direction.AxisDirection.POSITIVE);
		else return Direction.UP;
	}

	@EventHandler @SuppressWarnings("unused")
	private void onRender(Render3DEvent event) {
		placed_fade.forEach(s -> {
			Color a = new Color(color.get().r, color.get().g, color.get().b, (int) (((float)s.getLeft() / (float)fadetime.get())*color.get().a));
			event.renderer.box(s.getRight(), a, null, ShapeMode.Sides, 0);
		});
	}
}
