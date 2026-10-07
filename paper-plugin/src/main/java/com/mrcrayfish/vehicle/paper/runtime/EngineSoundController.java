package com.mrcrayfish.vehicle.paper.runtime;

import com.mrcrayfish.vehicle.paper.render.LandVehicleRig;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * Vanilla-client approximation of MovingEngineSound. The original client mod
 * changes a looping sound instance every tick; the vanilla protocol cannot do
 * that. This controller preserves its pitch/volume interpolation and plays the
 * original sample again at its actual pitch-adjusted duration, attached to the
 * moving seat entity instead of emitting overlapping fixed-position impulses.
 */
public final class EngineSoundController {
    private static final float INTERPOLATION = 0.2F;
    private static final double HEARING_DISTANCE_SQUARED = 48.0D * 48.0D;

    private final LandVehicleSpec spec;
    private final LandVehicleRig rig;
    private float volume;
    private float pitch;
    private double ticksUntilReplay;
    private boolean playing;

    public EngineSoundController(LandVehicleSpec spec, LandVehicleRig rig) {
        this.spec = spec;
        this.rig = rig;
        this.pitch = spec.minEnginePitch();
    }

    public void tick(Location location, boolean active, float targetPitch, Collection<UUID> riders) {
        float targetVolume = active ? 1.0F : 0.0F;
        volume += (targetVolume - volume) * INTERPOLATION;
        pitch += (clampPitch(targetPitch) - pitch) * INTERPOLATION;

        if (!active) {
            ticksUntilReplay = 0.0D;
            if (playing) {
                stop(location);
                playing = false;
            }
            return;
        }

        ticksUntilReplay -= 1.0D;
        if (ticksUntilReplay > 0.0D) {
            return;
        }
        play(location, riders);
        playing = true;
        ticksUntilReplay = replayTicks(spec.engineSound(), pitch);
    }

    public void stop(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        SoundStop stop = SoundStop.namedOnSource(Key.key(spec.engineSound()), Sound.Source.NEUTRAL);
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= HEARING_DISTANCE_SQUARED) {
                player.stopSound(stop);
            }
        }
        playing = false;
        ticksUntilReplay = 0.0D;
    }

    private void play(Location location, Collection<UUID> riders) {
        if (location.getWorld() == null || !rig.valid()) {
            return;
        }
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) > HEARING_DISTANCE_SQUARED) {
                continue;
            }
            float listenerVolume = riders.contains(player.getUniqueId()) ? volume * 0.4F : volume;
            Sound sound = Sound.sound(Key.key(spec.engineSound()), Sound.Source.NEUTRAL,
                    Math.max(0.01F, listenerVolume), pitch);
            player.playSound(sound, rig.driverSeat());
        }
    }

    public static float targetPitch(LandVehicleSpec spec, double speed, boolean charging,
                                    float chargingAmount, boolean sliding, boolean boosting,
                                    float throttle, boolean handbraking) {
        float progress = (float) Math.abs(speed / 25.0D);
        if (charging) {
            progress = 0.75F * chargingAmount;
        } else if ((sliding && throttle > 0.0F && !handbraking) || boosting) {
            progress = throttle;
        }
        return spec.minEnginePitch() + (spec.maxEnginePitch() - spec.minEnginePitch()) * progress;
    }

    /** Original OGG sample duration divided by pitch; one tick overlap avoids an audible gap. */
    public static double replayTicks(String sound, float pitch) {
        double ticksAtPitchOne;
        if (sound.endsWith("go_kart.engine")) {
            ticksAtPitchOne = 74.43083900226758D;
        } else if (sound.endsWith("tractor.engine")) {
            ticksAtPitchOne = 36.833958333333335D;
        } else if (sound.endsWith("dirt_bike.engine")) {
            ticksAtPitchOne = 25.310833333333335D;
        } else if (sound.endsWith("moped.engine")) {
            ticksAtPitchOne = 20.074376417233562D;
        } else {
            ticksAtPitchOne = 4.022675736961451D;
        }
        return Math.max(1.0D, ticksAtPitchOne / Math.max(0.01F, pitch) - 1.0D);
    }

    private float clampPitch(float value) {
        return Math.max(spec.minEnginePitch(), Math.min(spec.maxEnginePitch(), value));
    }
}
