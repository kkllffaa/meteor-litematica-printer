package com.kkllffaa.meteor_litematica_printer;

import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.utils.world.BlockUtils.canPlace;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.isClickable;

public class MyUtils {
	
	public static boolean place(BlockPos blockPos, Direction direction, boolean swingHand) {
		if (Utils.mc.player == null) return false;
		if (!canPlace(blockPos)) return false;
		
		Vec3d hitPos = new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
		
		BlockPos neighbour;
		Direction side = direction;
		
		if (side == null) {
			side = Direction.UP;
			neighbour = blockPos;
		} else {
			neighbour = blockPos.offset(side.getOpposite());
			hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
		}
		
		Direction s = side;
		
		
		
		place(new BlockHitResult(hitPos, s, neighbour, false), swingHand);
		
		
		
		return true;
	}
	
	
	private static void place(BlockHitResult blockHitResult, boolean swing) {
		if (Utils.mc.player == null || Utils.mc.interactionManager == null || Utils.mc.getNetworkHandler() == null) return;
		boolean wasSneaking = Utils.mc.player.input.sneaking;
		Utils.mc.player.input.sneaking = false;
		
		ActionResult result = Utils.mc.interactionManager.interactBlock(Utils.mc.player, Utils.mc.world, Hand.MAIN_HAND, blockHitResult);
		
		if (result.shouldSwingHand()) {
			if (swing) Utils.mc.player.swingHand(Hand.MAIN_HAND);
			else Utils.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
		}
		
		Utils.mc.player.input.sneaking = wasSneaking;
	}
	
	
	private static Direction getPlaceSide(BlockPos blockPos) {
		if (Utils.mc.world == null) return null;
		for (Direction side : Direction.values()) {
			BlockPos neighbor = blockPos.offset(side);
			Direction side2 = side.getOpposite();
			
			BlockState state = Utils.mc.world.getBlockState(neighbor);
			
			// Check if neighbour isn't empty
			if (state.isAir() || isClickable(state.getBlock())) continue;
			
			// Check if neighbour is a fluid
			if (!state.getFluidState().isEmpty()) continue;
			
			return side2;
		}
		
		return null;
	}
	
	
	
}
