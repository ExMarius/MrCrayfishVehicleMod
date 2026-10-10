package com.mrcrayfish.vehicle.paper.physics;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * AABB movement for the display-entity vehicle. This is the Paper equivalent
 * of vanilla Entity#move: resolve vertical and horizontal axes, try maxUpStep
 * when horizontal movement is clipped, and report ground/vertical collisions.
 */
public final class VehicleCollisionMover {
    private static final double COLLISION_EPSILON = 1.0E-7D;
    private static final double AXIS_SLICE = 0.20D;
    private static final int BINARY_SEARCH_STEPS = 14;

    private VehicleCollisionMover() {
    }

    public static Result move(World world, Location origin, Vector requested, boolean wasOnGround,
                              double entityWidth, double entityHeight, double stepHeight) {
        BoundingBox start = vehicleBox(origin, entityWidth, entityHeight);
        Candidate normal = moveWithoutStep(world, start, requested);

        boolean clippedHorizontal = different(normal.movement.getX(), requested.getX())
                || different(normal.movement.getZ(), requested.getZ());
        boolean landedDuringNormalMove = requested.getY() < 0.0D
                && different(normal.movement.getY(), requested.getY());

        Candidate selected = normal;
        if (clippedHorizontal && (wasOnGround || landedDuringNormalMove)) {
            Candidate stepped = moveWithStep(world, start, requested, stepHeight);
            if (horizontalLengthSquared(stepped.movement) > horizontalLengthSquared(normal.movement) + 1.0E-9D) {
                selected = stepped;
            }
        }

        boolean verticalCollision = different(selected.movement.getY(), requested.getY());
        boolean onGround = requested.getY() < 0.0D && verticalCollision;
        return new Result(selected.movement, onGround, verticalCollision);
    }

    private static Candidate moveWithoutStep(World world, BoundingBox start, Vector requested) {
        BoundingBox box = start.clone();
        Vector moved = new Vector();

        double y = resolveAxis(world, box, Axis.Y, requested.getY());
        box.shift(0.0D, y, 0.0D);
        moved.setY(y);

        if (Math.abs(requested.getX()) < Math.abs(requested.getZ())) {
            double z = resolveAxis(world, box, Axis.Z, requested.getZ());
            box.shift(0.0D, 0.0D, z);
            moved.setZ(z);
            double x = resolveAxis(world, box, Axis.X, requested.getX());
            box.shift(x, 0.0D, 0.0D);
            moved.setX(x);
        } else {
            double x = resolveAxis(world, box, Axis.X, requested.getX());
            box.shift(x, 0.0D, 0.0D);
            moved.setX(x);
            double z = resolveAxis(world, box, Axis.Z, requested.getZ());
            box.shift(0.0D, 0.0D, z);
            moved.setZ(z);
        }
        return new Candidate(box, moved);
    }

    private static Candidate moveWithStep(World world, BoundingBox start, Vector requested, double stepHeight) {
        BoundingBox box = start.clone();
        Vector moved = new Vector();

        double up = resolveAxis(world, box, Axis.Y, stepHeight);
        if (up <= COLLISION_EPSILON) {
            return new Candidate(box, moved);
        }
        box.shift(0.0D, up, 0.0D);

        if (Math.abs(requested.getX()) < Math.abs(requested.getZ())) {
            double z = resolveAxis(world, box, Axis.Z, requested.getZ());
            box.shift(0.0D, 0.0D, z);
            moved.setZ(z);
            double x = resolveAxis(world, box, Axis.X, requested.getX());
            box.shift(x, 0.0D, 0.0D);
            moved.setX(x);
        } else {
            double x = resolveAxis(world, box, Axis.X, requested.getX());
            box.shift(x, 0.0D, 0.0D);
            moved.setX(x);
            double z = resolveAxis(world, box, Axis.Z, requested.getZ());
            box.shift(0.0D, 0.0D, z);
            moved.setZ(z);
        }

        double down = resolveAxis(world, box, Axis.Y, requested.getY() - up);
        box.shift(0.0D, down, 0.0D);
        moved.setY(up + down);
        return new Candidate(box, moved);
    }

    private static double resolveAxis(World world, BoundingBox start, Axis axis, double requested) {
        if (Math.abs(requested) <= COLLISION_EPSILON) {
            return 0.0D;
        }

        int slices = Math.max(1, (int) Math.ceil(Math.abs(requested) / AXIS_SLICE));
        double slice = requested / slices;
        double moved = 0.0D;
        BoundingBox box = start.clone();

        for (int index = 0; index < slices; index++) {
            BoundingBox target = shifted(box, axis, slice);
            if (!collides(world, target)) {
                box = target;
                moved += slice;
                continue;
            }

            double clear = 0.0D;
            double blocked = slice;
            for (int iteration = 0; iteration < BINARY_SEARCH_STEPS; iteration++) {
                double middle = (clear + blocked) * 0.5D;
                if (collides(world, shifted(box, axis, middle))) {
                    blocked = middle;
                } else {
                    clear = middle;
                }
            }
            moved += clear;
            break;
        }
        return moved;
    }

    private static boolean collides(World world, BoundingBox box) {
        BoundingBox query = box.clone().expand(-COLLISION_EPSILON);
        return world.hasCollisionsIn(query);
    }

    private static BoundingBox shifted(BoundingBox box, Axis axis, double amount) {
        return switch (axis) {
            case X -> box.clone().shift(amount, 0.0D, 0.0D);
            case Y -> box.clone().shift(0.0D, amount, 0.0D);
            case Z -> box.clone().shift(0.0D, 0.0D, amount);
        };
    }

    private static BoundingBox vehicleBox(Location location, double entityWidth, double entityHeight) {
        double halfWidth = entityWidth * 0.5D;
        return new BoundingBox(
                location.getX() - halfWidth,
                location.getY(),
                location.getZ() - halfWidth,
                location.getX() + halfWidth,
                location.getY() + entityHeight,
                location.getZ() + halfWidth
        );
    }

    private static boolean different(double first, double second) {
        return Math.abs(first - second) > 1.0E-5D;
    }

    private static double horizontalLengthSquared(Vector vector) {
        return vector.getX() * vector.getX() + vector.getZ() * vector.getZ();
    }

    public record Result(Vector movement, boolean onGround, boolean verticalCollision) {
    }

    private record Candidate(BoundingBox box, Vector movement) {
    }

    private enum Axis {
        X, Y, Z
    }
}
