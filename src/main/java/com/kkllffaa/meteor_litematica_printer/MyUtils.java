package com.kkllffaa.meteor_litematica_printer;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
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

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MyUtils {

	public static boolean place(BlockPos blockPos, Direction direction, boolean swingHand, boolean rotate) {
		if (mc.player == null) return false;
		if (!canPlace(blockPos)) return false;

		Vec3d hitPos = new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);

		BlockPos neighbour;

		if (direction == null) {
            direction = Direction.UP;
			neighbour = blockPos;
		} else {
			neighbour = blockPos.offset(direction.getOpposite());
			hitPos.add(direction.getOffsetX() * 0.5, direction.getOffsetY() * 0.5, direction.getOffsetZ() * 0.5);
		}


		Direction s = direction;
        if (rotate) {
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), 50,
                () -> place(new BlockHitResult(hitPos, s, neighbour, false), swingHand));
        } else {
            place(new BlockHitResult(hitPos, s, neighbour, false), swingHand);
        }

		return true;
	}

	private static void place(BlockHitResult blockHitResult, boolean swing) {
		if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
		boolean wasSneaking = mc.player.input.sneaking;
		mc.player.input.sneaking = false;

		ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHitResult);

		if (result.shouldSwingHand()) {
			if (swing) mc.player.swingHand(Hand.MAIN_HAND);
			else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
		}

		mc.player.input.sneaking = wasSneaking;
	}


	private static Direction getPlaceSide(BlockPos blockPos) {
		if (mc.world == null) return null;
		for (Direction side : Direction.values()) {
			BlockPos neighbor = blockPos.offset(side);
			Direction side2 = side.getOpposite();

			BlockState state = mc.world.getBlockState(neighbor);

			// Check if neighbour isn't empty
			if (state.isAir() || isClickable(state.getBlock())) continue;

			// Check if neighbour is a fluid
			if (!state.getFluidState().isEmpty()) continue;

			return side2;
		}

		return null;
	}
}
