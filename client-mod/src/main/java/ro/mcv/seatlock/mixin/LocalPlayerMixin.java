package ro.mcv.seatlock.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.registry.Registries;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCV Seat Lock.
 *
 * Serverul (pluginul Paper) aseaza scaunul vehiculului si ii impune yaw-ul
 * masinii in fiecare tick, dar corpul jucatorului este desenat de CLIENT dupa
 * camera, deci "se invarte cu mouse-ul". Acest mod forteaza, doar pe client,
 * yaw-ul CORPULUI (yBodyRot) pe yaw-ul vehiculului pe care sta jucatorul.
 * Capul (yHeadRot / camera) ramane neatins, deci poti privi liber cu mouse-ul -
 * exact comportamentul modului original.
 *
 * Se aplica doar cand stai pe un vehicul de-al nostru (barca / raft / armor
 * stand / cal), deci nu afecteaza nicio alta calarie din joc.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void mcvSeatLock$lockBodyToVehicle(CallbackInfo info) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        Entity vehicle = self.getVehicle();
        if (vehicle == null) {
            return;
        }
        String path;
        try {
            path = Registries.ENTITY_TYPE.getId(vehicle.getType()).getPath();
        } catch (Throwable ignored) {
            return;
        }
        boolean seat = path.contains("boat") || path.contains("raft")
                || path.contains("armor_stand") || path.contains("horse");
        if (!seat) {
            return;
        }
        float yaw = vehicle.getYRot();
        self.yBodyRot = yaw;
        self.yBodyRotO = yaw;
    }
}
