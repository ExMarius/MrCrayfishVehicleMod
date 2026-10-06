package com.mrcrayfish.vehicle.paper.vehicle;

/**
 * The generated Go Kart properties from {@code VehiclePropertiesGen} expressed
 * in blocks. Keeping these values in one place prevents the Paper renderer and
 * physics port from drifting away from the original vehicle definition.
 */
public final class GoKartProperties {
    public static final float MODEL_UNIT = 1.0F / 16.0F;

    public static final float ENTITY_WIDTH = 1.5F;
    public static final float ENTITY_HEIGHT = 0.5F;
    public static final float STEP_HEIGHT = 1.05F; // Full-block server requirement; original GoKartEntity uses 0.625F.

    public static final float AXLE_OFFSET = -1.0F;
    public static final float WHEEL_OFFSET = 3.2F;
    public static final float BODY_RENDER_Y = 0.5F + (AXLE_OFFSET + WHEEL_OFFSET) * MODEL_UNIT;

    public static final float ENGINE_POWER = 18.0F;
    public static final float MAX_STEERING_ANGLE = 20.0F;
    public static final float FRONT_AXLE_OFFSET = 9.0F * MODEL_UNIT;
    public static final float REAR_AXLE_OFFSET = -9.5F * MODEL_UNIT;
    public static final float MAX_REVERSE_SPEED = 5.0F;

    public static final float ENERGY_CAPACITY = 15_000.0F;
    public static final float ENERGY_PER_TICK = 0.5F;
    public static final float MIN_ENGINE_PITCH = 0.9F;
    public static final float MAX_ENGINE_PITCH = 2.0F;

    public static final float STANDARD_ROAD_FRICTION = 1.1F;
    public static final float STANDARD_DIRT_FRICTION = 1.3F;
    public static final float STANDARD_SNOW_FRICTION = 1.7F;
    public static final float STANDARD_TRACTION = 0.8F;
    public static final float SLIDE_TRACTION = 0.05F;

    public static final Wheel FRONT_LEFT = new Wheel(-1, true, 7.0F, 0.0F, 8.75F,
            1.0F, 0.8F, 0.8F);
    public static final Wheel FRONT_RIGHT = new Wheel(1, true, 7.0F, 0.0F, 8.75F,
            1.0F, 0.8F, 0.8F);
    public static final Wheel REAR_LEFT = new Wheel(-1, false, 7.0F, 0.25F, -9.5F,
            1.0F, 0.8625F, 0.8625F);
    public static final Wheel REAR_RIGHT = new Wheel(1, false, 7.0F, 0.25F, -9.5F,
            1.0F, 0.8625F, 0.8625F);
    public static final Wheel[] WHEELS = {FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT};

    private GoKartProperties() {
    }

    /** Mirrors {@code com.mrcrayfish.vehicle.entity.Wheel}. */
    public record Wheel(int side, boolean front, float offsetX, float offsetY, float offsetZ,
                        float scaleX, float scaleY, float scaleZ) {
        public static final float WIDTH = 4.0F;

        public float axleX() {
            return side * offsetX * MODEL_UNIT;
        }

        public float centerY() {
            return BODY_RENDER_Y - 0.5F - AXLE_OFFSET * MODEL_UNIT + offsetY * MODEL_UNIT;
        }

        public float axleZ() {
            return offsetZ * MODEL_UNIT;
        }

        public float halfWidthOffset() {
            return side * WIDTH * scaleX * 0.5F * MODEL_UNIT;
        }

        public float contactX() {
            return axleX() + halfWidthOffset();
        }

        public float contactY() {
            return WHEEL_OFFSET * MODEL_UNIT + offsetY * MODEL_UNIT - 0.25F * scaleY;
        }

        public float contactZ() {
            return axleZ();
        }
    }
}
