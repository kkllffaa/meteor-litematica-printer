package com.kkllffaa.meteor_litematica_printer;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class RotationStuff {
	public static final double DEG_TO_RAD = Math.PI / 180.0;
	public static final float DEG_TO_RAD_F = (float) DEG_TO_RAD;
	public static final double RAD_TO_DEG = 180.0 / Math.PI;
	public static final float RAD_TO_DEG_F = (float) RAD_TO_DEG;
	public static Rotation calcRotationFromVec3d(Vec3d orig, Vec3d dest, Rotation current) {
		return wrapAnglesToRelative(current, calcRotationFromVec3d(orig, dest));
	}
	private static Rotation calcRotationFromVec3d(Vec3d orig, Vec3d dest) {
		double[] delta = {orig.x - dest.x, orig.y - dest.y, orig.z - dest.z};
		double yaw = MathHelper.atan2(delta[0], -delta[2]);
		double dist = Math.sqrt(delta[0] * delta[0] + delta[2] * delta[2]);
		double pitch = MathHelper.atan2(delta[1], dist);
		return new Rotation(
				(float) (yaw * RAD_TO_DEG),
				(float) (pitch * RAD_TO_DEG)
		);
	}
	public static Rotation wrapAnglesToRelative(Rotation current, Rotation target) {
		if (current.yawIsReallyClose(target)) {
			return new Rotation(current.getYaw(), target.getPitch());
		}
		return target.subtract(current).normalize().add(current);
	}
	public static Vec3d calcLookDirectionFromRotation(Rotation rotation) {
		float flatZ = MathHelper.cos((-rotation.getYaw() * DEG_TO_RAD_F) - (float) Math.PI);
		float flatX = MathHelper.sin((-rotation.getYaw() * DEG_TO_RAD_F) - (float) Math.PI);
		float pitchBase = -MathHelper.cos(-rotation.getPitch() * DEG_TO_RAD_F);
		float pitchHeight = MathHelper.sin(-rotation.getPitch() * DEG_TO_RAD_F);
		return new Vec3d(flatX * pitchBase, pitchHeight, flatZ * pitchBase);
	}


	public static HitResult rayTraceTowards(ClientPlayerEntity entity, Rotation rotation, double blockReachDistance) {
		Vec3d start = entity.getCameraPosVec(1.0F);


		Vec3d direction = calcLookDirectionFromRotation(rotation);
		Vec3d end = start.add(
				direction.x * blockReachDistance,
				direction.y * blockReachDistance,
				direction.z * blockReachDistance
		);
		return entity.getEntityWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
	}
}
