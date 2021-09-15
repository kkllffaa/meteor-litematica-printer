package com.kkllffaa.meteor_litematica_printer;

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
import net.minecraft.item.Item;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

public class Printer extends Module {
	
	
	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	
	//region settings
	
	private final Setting<Integer> printing_range = sgGeneral.add(new IntSetting.Builder()
			.name("printing-range")
			.description("Printing block place range.")
			.defaultValue(2)
			.min(1).sliderMin(0)
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
			.name("advenced")
			.description("respect block rotation (exterimental).")
			.defaultValue(false)
			.build()
	);
	
	//endregion
	
	
	public Printer() {
		super(Addon.CATEGORY, "litematica-printer", "description");
	}
	
	private int timer, placed = 0;
	private int usedslot = -1;
	
	@EventHandler @SuppressWarnings("unused")
	private void onTick(TickEvent.Post event) {
		if (mc.player == null) return;
		WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
		if (worldSchematic == null) {
			toggle();
			return;
		}
		if (timer >= printing_delay.get()) {
			BlockIterator.register(printing_range.get(), printing_range.get(), (pos, blockState) -> {
				if (!mc.player.getBlockPos().isWithinDistance(pos, printing_range.get()) || !blockState.isAir()) return;
				BlockState required = worldSchematic.getBlockState(pos);
				
				if (!required.isAir() && blockState.getBlock() != required.getBlock()) {
					if (advenced.get() ?
							swichitem(required.getBlock().asItem(), () -> betterPlace(required, pos)) :
							swichitem(required.getBlock().asItem(), () -> oldPlace(pos))
					) {
						placed++;
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
	
	public boolean betterPlace (BlockState required, BlockPos pos) {
		if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
		if (!BlockUtils.canPlace(pos)) return false;
		
		if (mc.world.isAir(pos) || mc.world.getBlockState(pos).getMaterial().isLiquid()) {
			Direction direction = dir(required);
			
			if (direction == Direction.UP) {
				return BlockUtils.place(pos, Hand.MAIN_HAND, mc.player.getInventory().selectedSlot, false, 0, false, true, false);
			}
			
			BlockHitResult result = new BlockHitResult(
					//fromblockpos(pos/*.offset(Direction.UP)*/),
					new Vec3d(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5).add(direction.getOffsetX()*0.5, direction.getOffsetY()*0.5, direction.getOffsetZ()*0.5),
					direction,
					pos.offset(direction.getOpposite()),
					false
			);

			//BlockUtilsAccessor.place(result, Hand.MAIN_HAND, false);
			
			mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, result);
			
			return true;
		}else return false;
	}
	
	private Direction dir(BlockState state) {
		if (state.contains(Properties.FACING)) return state.get(Properties.FACING);
		else if (state.contains(Properties.AXIS)) return Direction.from(state.get(Properties.AXIS), Direction.AxisDirection.POSITIVE);
		else if (state.contains(Properties.HORIZONTAL_AXIS)) return Direction.from(state.get(Properties.HORIZONTAL_AXIS), Direction.AxisDirection.POSITIVE);
		else return Direction.UP;
	}
	
	public boolean oldPlace(BlockPos pos) {
		if (mc.player == null) return false;
		return BlockUtils.place(pos, Hand.MAIN_HAND, mc.player.getInventory().selectedSlot, false, 0, false, true, false);
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
			InvUtils.swap(usedslot);
			if (action.get()) {
				InvUtils.swap(a);
				return true;
			}else {
				InvUtils.swap(a);
				return false;
			}
		}else if (result.found()) {
			if (result.isHotbar()) {
				InvUtils.swap(result.getSlot());
				if (action.get()) {
					usedslot = mc.player.getInventory().selectedSlot;
					InvUtils.swap(a);
					return true;
				}else {
					InvUtils.swap(a);
					return false;
				}
			}else if (result.isMain()){
				FindItemResult empty = InvUtils.findEmpty();
				if (empty.found() && empty.isHotbar()) {
					InvUtils.move().from(result.getSlot()).toHotbar(empty.getSlot());
					InvUtils.swap(empty.getSlot());
					if (action.get()) {
						usedslot = mc.player.getInventory().selectedSlot;
						InvUtils.swap(a);
						return true;
					}else {
						InvUtils.swap(a);
						return false;
					}
				} else if (usedslot != -1) {
					InvUtils.move().from(result.getSlot()).toHotbar(usedslot);
					InvUtils.swap(usedslot);
					if (action.get()) {
						InvUtils.swap(a);
						return true;
					}else {
						InvUtils.swap(a);
						return false;
					}
				}else return false;
			}else return false;
		}else return false;
	}
}
