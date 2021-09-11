package com.kkllffaa.meteor_litematica_printer;

import com.kkllffaa.meteor_litematica_printer.Addon;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Printer extends Module {
	
	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	
	//region settings
	
	private final Setting<Integer> printing_range = sgGeneral.add(new IntSetting.Builder()
			.name("printing-range")
			.description("Printing block place range.")
			.defaultValue(2)
			.min(1)
			.max(6)
			.sliderMin(0)
			.sliderMax(6)
			.build()
	);
	
	private final Setting<Integer> printing_delay = sgGeneral.add(new IntSetting.Builder()
			.name("printing-delay")
			.description("Delay between printing blocks in ticks.")
			.defaultValue(2)
			.min(0)
			.max(100)
			.sliderMin(0)
			.sliderMax(40)
			.build()
	);
	
	private final Setting<Boolean> swing_hand = sgGeneral.add(new BoolSetting.Builder()
			.name("swing-hand")
			.description("swing hand when placing blocks.")
			.defaultValue(false)
			.build()
	);
	
	//endregion
	
	
	public Printer() {
		super(Addon.CATEGORY, "litematica-printer", "description");
	}
	
	private int timer = 0;
	
	private int usedslot = -1;
	
	@EventHandler
	private void onTick(TickEvent.Post event) {
		if (mc.player == null) return;
		WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
		if (worldSchematic == null) {
			toggle();
			return;
		}
		if (timer >= printing_delay.get()) {
			BlockIterator.register(printing_range.get(), printing_range.get(), (pos, blockState) -> {
				if (!mc.player.getBlockPos().isWithinDistance(pos, printing_range.get())) return;
				BlockState required = worldSchematic.getBlockState(pos);
				
				if (!required.isAir() && blockState.getBlock() != required.getBlock()) {
					if (place(required, pos)) {
						if (printing_delay.get() <= 0) BlockIterator.disableCurrent();
						timer = 0;
					}
				}
			});
		}else timer++;
	}
	
	private boolean place(BlockState required, BlockPos pos) {
		if (mc.player == null) return false;
		FindItemResult result = InvUtils.find(required.getBlock().asItem());
		if (mc.player.getMainHandStack().getItem() == required.getBlock().asItem()) {
			if (BlockUtils.place(pos, Hand.MAIN_HAND, mc.player.getInventory().selectedSlot, false, 0, swing_hand.get(), true, false)) {
				usedslot = mc.player.getInventory().selectedSlot;
				return true;
			}else return false;
		}else if (result.found()) {
			if (result.isHotbar()) {
				if (BlockUtils.place(pos, result, false, 0, swing_hand.get(), true, true)) {
					usedslot = result.getSlot();
					return true;
				}else return false;
			}else {
				if (usedslot != -1) {
					InvUtils.move().from(result.getSlot()).toHotbar(usedslot);
					return BlockUtils.place(pos, Hand.MAIN_HAND, usedslot, false, 0, swing_hand.get(), true, true);
				}else {
					FindItemResult empty = InvUtils.findEmpty();
					if (empty.found() && empty.isHotbar()) {
						InvUtils.move().from(result.getSlot()).toHotbar(empty.getSlot());
						usedslot = empty.getSlot();
						return BlockUtils.place(pos, Hand.MAIN_HAND, empty.getSlot(), false, 0, swing_hand.get(), true, true);
					}else return false;
				}
			}
		} else return false;
	}
}
