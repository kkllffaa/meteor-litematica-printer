package com.kkllffaa.meteor_litematica_printer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

public class Printer extends Module {
	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRendering = settings.createGroup("Rendering");

	private final Setting<Integer> printing_range = sgGeneral.add(new IntSetting.Builder()
			.name("printing-range")
			.description("The block place range.")
			.defaultValue(2)
			.min(1).sliderMin(1)
			.max(60).sliderMax(6)
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
			.description("How many blocks place per tick.")
			.defaultValue(1)
			.min(1).sliderMin(1)
			.max(100).sliderMax(100)
			.build()
	);

	private final Setting<Boolean> advanced = sgGeneral.add(new BoolSetting.Builder()
			.name("advanced")
			.description("Respect block rotation (places blocks in weird places in singleplayer, multiplayer should work fine).")
			.defaultValue(false)
			.build()
	);

	private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
			.name("air-place")
			.description("Allow the bot to place in the air.")
			.defaultValue(true)
			.build()
	);

	private final Setting<Boolean> placeThroughWall = sgGeneral.add(new BoolSetting.Builder()
			.name("Place Through Wall")
			.description("Allow the bot to place through walls.")
			.defaultValue(true)
			.build()
	);

	private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
			.name("swing")
			.description("Swing hand when placing.")
			.defaultValue(false)
			.build()
	);

    private final Setting<Boolean> returnHand = sgGeneral.add(new BoolSetting.Builder()
			.name("return-slot")
			.description("Return to old slot.")
			.defaultValue(false)
			.build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
			.name("rotate")
			.description("Rotate to the blocks being placed.")
			.defaultValue(false)
			.build()
    );

    private final Setting<Boolean> clientSide = sgGeneral.add(new BoolSetting.Builder()
			.name("Client side Rotation")
			.description("Rotate to the blocks being placed on client side.")
			.defaultValue(false)
			.visible(rotate::get)
			.build()
    );

	private final Setting<Boolean> dirtgrass = sgGeneral.add(new BoolSetting.Builder()
			.name("dirt-as-grass")
			.description("Use dirt instead of grass.")
			.defaultValue(true)
			.build()
	);

    private final Setting<SortAlgorithm> firstAlgorithm = sgGeneral.add(new EnumSetting.Builder<SortAlgorithm>()
			.name("first-sorting-mode")
			.description("The blocks you want to place first.")
			.defaultValue(SortAlgorithm.None)
			.build()
	);

    private final Setting<SortingSecond> secondAlgorithm = sgGeneral.add(new EnumSetting.Builder<SortingSecond>()
			.name("second-sorting-mode")
			.description("Second pass of sorting eg. place first blocks higher and closest to you.")
			.defaultValue(SortingSecond.None)
			.visible(()-> firstAlgorithm.get().applySecondSorting)
			.build()
	);

    private final Setting<Boolean> whitelistenabled = sgWhitelist.add(new BoolSetting.Builder()
			.name("whitelist-enabled")
			.description("Only place selected blocks.")
			.defaultValue(false)
			.build()
	);

	// TODO: Add blacklist option
    private final Setting<List<Block>> whitelist = sgWhitelist.add(new BlockListSetting.Builder()
			.name("whitelist")
			.description("Blocks to place.")
			.visible(whitelistenabled::get)
			.build()
	);

    private final Setting<Boolean> renderBlocks = sgRendering.add(new BoolSetting.Builder()
        .name("render-placed-blocks")
        .description("Renders block placements.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fadeTime = sgRendering.add(new IntSetting.Builder()
        .name("fade-time")
        .description("Time for the rendering to fade, in ticks.")
        .defaultValue(3)
        .min(1).sliderMin(1)
        .max(1000).sliderMax(20)
        .visible(renderBlocks::get)
        .build()
    );

    private final Setting<SettingColor> colour = sgRendering.add(new ColorSetting.Builder()
        .name("colour")
        .description("The cubes colour.")
        .defaultValue(new SettingColor(95, 190, 255))
        .visible(renderBlocks::get)
        .build()
    );

	private final Setting<Boolean> useOffhand = sgGeneral.add(new BoolSetting.Builder()
    	.name("use-offhand")
    	.description("Automatically put block items in the offhand while printing.")
    	.defaultValue(false)
    	.build()
	);


    private int timer;
    private int usedSlot = -1;
    private final List<BlockPos> toSort = new ArrayList<>();
    private final List<Tuple<Integer, BlockPos>> placed_fade = new ArrayList<>();


	// TODO: Add an option for smooth rotation. Make it look legit.
	// Might use liquidbounce RotationUtils to make it happen.
	// https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/kotlin/net/ccbluex/liquidbounce/utils/aiming/RotationsUtil.kt#L257

	public Printer() {
		super(Addon.CATEGORY, "litematica-printer", "Automatically prints open schematics");
	}

    @Override
    public void onActivate() {
        onDeactivate();
    }

	@Override
    public void onDeactivate() {
		placed_fade.clear();
	}

	@EventHandler
	private void onTick(TickEvent.Post event) {
		if (mc.player == null || mc.level == null) {
			placed_fade.clear();
			return;
		}

		placed_fade.forEach(s -> s.setA(s.getA() - 1));
		placed_fade.removeIf(s -> s.getA() <= 0);

		WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
		if (worldSchematic == null) {
			placed_fade.clear();
			toggle();
			return;
		}

		toSort.clear();


		if (timer >= printing_delay.get()) {
			BlockIterator.register(printing_range.get() + 1, printing_range.get() + 1, (pos, blockState) -> {
				BlockState required = worldSchematic.getBlockState(pos);

				if (
						mc.player.blockPosition().closerThan(pos, printing_range.get())
						&& blockState.canBeReplaced()
						// && !required.liquid()
						&& required.getFluidState().isEmpty()
						&& !required.isAir()
						&& blockState.getBlock() != required.getBlock()
						&& DataManager.getRenderLayerRange().isPositionWithinRange(pos)
						&& !mc.player.getBoundingBox().intersects(Vec3.atLowerCornerOf(pos), Vec3.atLowerCornerOf(pos).add(1, 1, 1))
						&& required.canSurvive(mc.level, pos)
					) {
					boolean isBlockInLineOfSight = MyUtils.isBlockInLineOfSight(pos, required);
			    	SlabType wantedSlabType = advanced.get() && required.hasProperty(BlockStateProperties.SLAB_TYPE) ? required.getValue(BlockStateProperties.SLAB_TYPE) : null;
			    	Half wantedBlockHalf = advanced.get() && required.hasProperty(BlockStateProperties.HALF) ? required.getValue(BlockStateProperties.HALF) : null;
			    	Direction wantedHorizontalOrientation = advanced.get() && required.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? required.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
			    	Axis wantedAxies = advanced.get() && required.hasProperty(BlockStateProperties.AXIS) ? required.getValue(BlockStateProperties.AXIS) : null;
			    	Direction wantedHopperOrientation = advanced.get() && required.hasProperty(BlockStateProperties.FACING_HOPPER) ? required.getValue(BlockStateProperties.FACING_HOPPER) : null;

					if(
						airPlace.get()
						&& placeThroughWall.get()
						|| !airPlace.get()
						&& !placeThroughWall.get()
						&&  isBlockInLineOfSight
						&& MyUtils.getVisiblePlaceSide(
							pos,
							required,
							wantedSlabType, 
							wantedBlockHalf,
							wantedHorizontalOrientation != null ? wantedHorizontalOrientation : wantedHopperOrientation,
							wantedAxies,
							printing_range.get(),
							advanced.get() ? dir(required) : null
						) != null
						|| airPlace.get()
						&& !placeThroughWall.get()
						&& isBlockInLineOfSight
						|| !airPlace.get()
						&& placeThroughWall.get()
						&& BlockUtils.getPlaceSide(pos) != null
					) {
						if (!whitelistenabled.get() || whitelist.get().contains(required.getBlock())) {
							toSort.add(new BlockPos(pos));
						}
					}
				}
			});

			BlockIterator.after(() -> {
				//if (!tosort.isEmpty()) info(tosort.toString());

				if (firstAlgorithm.get() != SortAlgorithm.None) {
					if (firstAlgorithm.get().applySecondSorting) {
						if (secondAlgorithm.get() != SortingSecond.None) {
							toSort.sort(secondAlgorithm.get().algorithm);
						}
					}
					toSort.sort(firstAlgorithm.get().algorithm);
				}


				int placed = 0;
				for (BlockPos pos : toSort) {

					BlockState state = worldSchematic.getBlockState(pos);
					Item item = state.getBlock().asItem();

					if (dirtgrass.get() && item == Items.GRASS_BLOCK) item = Items.DIRT;

					boolean placedBlock = false;

					if (useOffhand.get()) {
						// Offhand benutzen
						placedBlock = switchItemOffhand(item, () -> place(state, pos));
					} else {
						// Normale Hand benutzen
						placedBlock = switchItem(item, state, () -> place(state, pos));
					}
					if (placedBlock) {
						timer = 0;
						placed++;
						if (renderBlocks.get()) {
							placed_fade.add(new Tuple<>(fadeTime.get(), new BlockPos(pos)));
						}
						if (placed >= bpt.get()) {
							return;
						}
					}
				}
			});


		} else timer++;
	}

	public boolean place(BlockState required, BlockPos pos) {

		if (mc.player == null || mc.level == null) return false;
		if (!mc.level.getBlockState(pos).canBeReplaced()) return false;

		Direction wantedSide = advanced.get() ? dir(required) : null;
    	SlabType wantedSlabType = advanced.get() && required.hasProperty(BlockStateProperties.SLAB_TYPE) ? required.getValue(BlockStateProperties.SLAB_TYPE) : null;
    	Half wantedBlockHalf = advanced.get() && required.hasProperty(BlockStateProperties.HALF) ? required.getValue(BlockStateProperties.HALF) : null;
    	Direction wantedHorizontalOrientation = advanced.get() && required.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? required.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
    	Axis wantedAxies = advanced.get() && required.hasProperty(BlockStateProperties.AXIS) ? required.getValue(BlockStateProperties.AXIS) : null;
    	Direction wantedHopperOrientation = advanced.get() && required.hasProperty(BlockStateProperties.FACING_HOPPER) ? required.getValue(BlockStateProperties.FACING_HOPPER) : null;
    	//Direction wantedFace = advanced.get() && required.contains(Properties.FACING) ? required.get(Properties.FACING) : null;
    	
    	Direction placeSide = placeThroughWall.get() ?
    						MyUtils.getPlaceSide(
    								pos,
    								required,
    								wantedSlabType, 
    								wantedBlockHalf,
    								wantedHorizontalOrientation != null ? wantedHorizontalOrientation : wantedHopperOrientation,
    								wantedAxies,
    								wantedSide)
    						: MyUtils.getVisiblePlaceSide(
    								pos,
    								required,
    								wantedSlabType, 
    								wantedBlockHalf,
    								wantedHorizontalOrientation != null ? wantedHorizontalOrientation : wantedHopperOrientation,
    								wantedAxies,
    								printing_range.get(),
    								wantedSide
							);
    	

        return MyUtils.place(pos, placeSide, wantedSlabType, wantedBlockHalf, wantedHorizontalOrientation != null ? wantedHorizontalOrientation : wantedHopperOrientation, wantedAxies, airPlace.get(), swing.get(), rotate.get(), clientSide.get(), printing_range.get(), useOffhand.get() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
	}

	private boolean switchItem(Item item, BlockState state, Supplier<Boolean> action) {
		if (mc.player == null) return false;

		int selectedSlot = mc.player.getInventory().getSelectedSlot();
		boolean isCreative = mc.player.isCreative();
		FindItemResult result = InvUtils.find(item);


		// TODO: Check if ItemStack nbt has BlockStateTag == BlockState required when in creative
		// TODO: Fix check nbt
		// TODO: Fix not acquiring blocks in creative mode

		if (
			mc.player.getMainHandItem().getItem() == item
		) {
			if (action.get()) {
				usedSlot = mc.player.getInventory().getSelectedSlot();
				return true;
			} else return false;

		} else if (
			usedSlot != -1 &&
			mc.player.getInventory().getItem(usedSlot).getItem() == item
		) {
			InvUtils.swap(usedSlot, returnHand.get());
			if (action.get()) {
				return true;
			} else {
				InvUtils.swap(selectedSlot, returnHand.get());
				return false;
			}

		} else if (
			result.found()
		) {
			if (result.isHotbar()) {
				InvUtils.swap(result.slot(), returnHand.get());

				if (action.get()) {
					usedSlot = mc.player.getInventory().getSelectedSlot();
					return true;
				} else {
					InvUtils.swap(selectedSlot, returnHand.get());
					return false;
				}

			} else if (result.isMain()) {
				FindItemResult empty = InvUtils.findEmpty();

				if (empty.found() && empty.isHotbar()) {
					InvUtils.move().from(result.slot()).toHotbar(empty.slot());
					InvUtils.swap(empty.slot(), returnHand.get());

					if (action.get()) {
						usedSlot = mc.player.getInventory().getSelectedSlot();
						return true;
					} else {
						InvUtils.swap(selectedSlot, returnHand.get());
						return false;
					}

				} else if (usedSlot != -1) {
					InvUtils.move().from(result.slot()).toHotbar(usedSlot);
					InvUtils.swap(usedSlot, returnHand.get());

					if (action.get()) {
						return true;
					} else {
						InvUtils.swap(selectedSlot, returnHand.get());
						return false;
					}

				} else return false;
			} else return false;
		} else if (isCreative) {
			int slot = 0;
            FindItemResult fir = InvUtils.find(ItemStack::isEmpty, 0, 8);
            if (fir.found()) {
                slot = fir.slot();
            }
			mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + slot, item.getDefaultInstance()));
			InvUtils.swap(slot, returnHand.get());
            return true;
		} else return false;
	}
	private boolean switchItemOffhand(Item item, Supplier<Boolean> action) {
		if (mc.player == null || !useOffhand.get()) return false; 
		
		if (mc.player.getOffhandItem().getItem() == item) {
			return action.get();
		}

		FindItemResult result = InvUtils.find(item);
		if (!result.found()) return false;

		InvUtils.move().from(result.slot()).toOffhand();

		return action.get();
	}
	
	private Direction dir(BlockState state) {
		if (state.hasProperty(BlockStateProperties.FACING)) return state.getValue(BlockStateProperties.FACING);
		else if (state.hasProperty(BlockStateProperties.AXIS)) return Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE);
		else if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) return Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.HORIZONTAL_AXIS), Direction.AxisDirection.POSITIVE);
		else return Direction.UP;
	}

	@EventHandler
	private void onRender(Render3DEvent event) {
		placed_fade.forEach(s -> {
			Color a = new Color(colour.get().r, colour.get().g, colour.get().b, (int) (((float)s.getA() / (float) fadeTime.get()) * colour.get().a));
			event.renderer.box(s.getB(), a, null, ShapeMode.Sides, 0);
		});
	}

	@SuppressWarnings("unused")
	public enum SortAlgorithm {
		None(false, (a, b) -> 0),
		TopDown(true, Comparator.comparingInt(value -> value.getY() * -1)),
		DownTop(true, Comparator.comparingInt(Vec3i::getY)),
		Nearest(false, Comparator.comparingDouble(value -> MeteorClient.mc.player != null ? Utils.squaredDistance(MeteorClient.mc.player.getX(), MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ(), value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5) : 0)),
		Furthest(false, Comparator.comparingDouble(value -> MeteorClient.mc.player != null ? (Utils.squaredDistance(MeteorClient.mc.player.getX(), MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ(), value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5)) * -1 : 0));


		final boolean applySecondSorting;
		final Comparator<BlockPos> algorithm;

		SortAlgorithm(boolean applySecondSorting, Comparator<BlockPos> algorithm) {
			this.applySecondSorting = applySecondSorting;
			this.algorithm = algorithm;
		}
	}

	@SuppressWarnings("unused")
	public enum SortingSecond {
		None(SortAlgorithm.None.algorithm),
		Nearest(SortAlgorithm.Nearest.algorithm),
		Furthest(SortAlgorithm.Furthest.algorithm);

		final Comparator<BlockPos> algorithm;

		SortingSecond(Comparator<BlockPos> algorithm) {
			this.algorithm = algorithm;
		}
	}
}
