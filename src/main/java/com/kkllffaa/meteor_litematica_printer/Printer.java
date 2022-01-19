package com.kkllffaa.meteor_litematica_printer;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
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
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class Printer extends Module {


	private final SettingGroup sgGeneral = settings.getDefaultGroup();

	//region settings

	private final Setting<Integer> printing_range = sgGeneral.add(new IntSetting.Builder()
			.name("printing-range")
			.description("printing block place range.")
			.defaultValue(2)
			.min(1).sliderMin(1)
			.max(6).sliderMax(6)
			.build()
	);

	private final Setting<Integer> printing_delay = sgGeneral.add(new IntSetting.Builder()
			.name("printing-delay")
			.description("delay between printing blocks in ticks.")
			.defaultValue(2)
			.min(0).sliderMin(0)
			.max(100).sliderMax(40)
			.build()
	);

	private final Setting<Integer> bpt = sgGeneral.add(new IntSetting.Builder()
			.name("blocks/tick")
			.description("how many blocks place in 1 tick.")
			.defaultValue(1)
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
			.name("return slot")
			.description("return to old slot.")
			.defaultValue(false)
			.build()
    );

	private final Setting<Boolean> renderblocks = sgGeneral.add(new BoolSetting.Builder()
			.name("render placed blocks")
			.description("render cube when placing block.")
			.defaultValue(false)
			.build()
	);

	private final Setting<Integer> fadetime = sgGeneral.add(new IntSetting.Builder()
			.name("fade time")
			.description("in ticks.")
			.defaultValue(2)
			.min(1).sliderMin(1)
			.max(1000).sliderMax(100)
			.visible(renderblocks::get)
			.build()
	);

	private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
			.name("color")
			.description("cubes color.")
			.defaultValue(new SettingColor(100, 100, 100))
			.visible(renderblocks::get)
			.build()
	);

    private final Setting<Boolean> rotateblocks = sgGeneral.add(new BoolSetting.Builder()
			.name("rotate")
			.description("look at the blocks being placed.")
			.defaultValue(false)
			.build()
    );
    
    private final Setting<SortAlgoritm> algoritm = sgGeneral.add(new EnumSetting.Builder<SortAlgoritm>()
			.name("sorting mode")
			.description("The blocks you want to place first.")
			.defaultValue(SortAlgoritm.None)
			.build()
	);
    
    private final Setting<SortingSecond> secondalgoritn = sgGeneral.add(new EnumSetting.Builder<SortingSecond>()
			.name("second sorting mode")
			.description("second pass of sorting eg. place first blocks higher and closest to you.")
			.defaultValue(SortingSecond.None)
			.visible(()->algoritm.get().applysecondsorting)
			.build()
	);
	

	//endregion
	

	public Printer() {
		super(Addon.CATEGORY, "litematica-printer", "description");
	}

	private int timer;
	private int usedslot = -1;
	
	private final List<BlockPos> tosort = new ArrayList<>();

	private final List<Pair<Integer, BlockPos>> placed_fade = new ArrayList<>();

	@Override public void onDeactivate() {
		placed_fade.clear();
	}
	@Override public void onActivate() {
		onDeactivate();
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
		
		tosort.clear();
		
		if (timer >= printing_delay.get()) {
			
			BlockIterator.register(printing_range.get()+1, printing_range.get()+1, (pos, blockState) -> {
				BlockState required = worldSchematic.getBlockState(pos);

				
				if (mc.player.getBlockPos().isWithinDistance(pos, printing_range.get()) &&
						blockState.isAir() && !required.isAir() && blockState.getBlock() != required.getBlock() &&
						DataManager.getRenderLayerRange().isPositionWithinRange(pos)) {
					tosort.add(new BlockPos(pos));
				}
			});
			
			
			BlockIterator.after(() -> {
				//if (!tosort.isEmpty()) info(tosort.toString());
				
				
				
				if (algoritm.get() != SortAlgoritm.None) {
					if (algoritm.get().applysecondsorting) {
						if (secondalgoritn.get() != SortingSecond.None) {
							tosort.sort(secondalgoritn.get().al);
						}
					}
					tosort.sort(algoritm.get().al);
				}
				
				
				int placed = 0;
				for (var pos : tosort) {
					
					BlockState state = worldSchematic.getBlockState(pos);
					if (swichitem(state.getBlock().asItem(), () -> place(state, pos, advenced.get(), swing.get()))) {
						timer = 0;
						placed++;
						if (renderblocks.get()) {
							placed_fade.add(new Pair<>(fadetime.get(), new BlockPos(pos)));
						}
						if (placed >= bpt.get()) {
							return;
						}
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
				InvUtils.swap(result.slot(), returnhand.get());
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
					InvUtils.move().from(result.slot()).toHotbar(empty.slot());
					InvUtils.swap(empty.slot(), returnhand.get());
					if (action.get()) {
						usedslot = mc.player.getInventory().selectedSlot;
						InvUtils.swap(a, returnhand.get());
						return true;
					}else {
						InvUtils.swap(a, returnhand.get());
						return false;
					}
				} else if (usedslot != -1) {
					InvUtils.move().from(result.slot()).toHotbar(usedslot);
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
	
	@SuppressWarnings("unused")
	public enum SortAlgoritm {
		None(false, (a, b) -> 0),
		TopDown(true, Comparator.comparingInt(value -> value.getY()*-1)),
		DownTop(true, Comparator.comparingInt(Vec3i::getY)),
		Closest(false, Comparator.comparingDouble(value -> MeteorClient.mc.player != null ? Utils.squaredDistance(MeteorClient.mc.player.getX(), MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ(), value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5) : 0)),
		Furthest(false, Comparator.comparingDouble(value -> MeteorClient.mc.player != null ? (Utils.squaredDistance(MeteorClient.mc.player.getX(), MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ(), value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5))*-1 : 0));
		
		
		final boolean applysecondsorting;
		final Comparator<BlockPos> al;
		
		SortAlgoritm(boolean applysecondsorting, Comparator<BlockPos> al) {
			this.applysecondsorting = applysecondsorting;
			this.al = al;
		}
	}
	
	@SuppressWarnings("unused")
	public enum SortingSecond {
		None(SortAlgoritm.None.al),
		Nearest(SortAlgoritm.Closest.al),
		Furthest(SortAlgoritm.Furthest.al);
		
		final Comparator<BlockPos> al;
		
		SortingSecond(Comparator<BlockPos> al) {
			this.al = al;
		}
	}
	
}
