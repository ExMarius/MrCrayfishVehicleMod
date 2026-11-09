package ro.mcv.seatlock.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCV Seat Lock.
 *
 * Serverul (pluginul Paper) aseaza scaunul vehiculului si ii impune yaw-ul
 * masinii in fiecare tick, dar CORPUL jucatorului este desenat de client dupa
 * camera, deci "se invarte cu mouse-ul" desi sta pe scaun. Acest mod forteaza,
 * doar pe client, yaw-ul corpului (yBodyRot / yBodyRotO) pe yaw-ul vehiculului
 * pe care sta jucatorul. Capul (yHeadRot) si camera raman neatinse, deci privesti
 * liber cu mouse-ul - exact comportamentul modului original.
 *
 * Se aplica doar pe vehiculele noastre (barca / raft / armor stand / cal), deci
 * nu afecteaza nicio alta calarie din joc.
 *
 * Implementare prin reflexie + target pe nume: modul se compileaza fara
 * dependenta de numele obfuscate/mapate ale claselor Minecraft.
 */
@Mixin(targets = "net.minecraft.client.player.LocalPlayer")
public class LocalPlayerMixin {

    private static java.lang.reflect.Method getVehicleMethod;
    private static java.lang.reflect.Method getYRotMethod;
    private static java.lang.reflect.Field yBodyRotField;
    private static java.lang.reflect.Field yBodyRotOField;
    private static boolean failed;

    @Inject(method = "tick", at = @At("TAIL"))
    private void mcvSeatLock$lockBodyToVehicle(CallbackInfo info) {
        if (failed) {
            return;
        }
        try {
            if (getVehicleMethod == null) {
                Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                Class<?> livingClass = Class.forName("net.minecraft.world.entity.LivingEntity");
                getVehicleMethod = entityClass.getMethod("getVehicle");
                getYRotMethod = entityClass.getMethod("getYRot");
                yBodyRotField = livingClass.getField("yBodyRot");
                yBodyRotOField = livingClass.getField("yBodyRotO");
            }
            Object self = this;
            Object vehicle = getVehicleMethod.invoke(self);
            if (vehicle == null) {
                return;
            }
            String name = vehicle.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            boolean seat = name.contains("boat") || name.contains("raft")
                    || name.contains("armorstand") || name.contains("horse");
            if (!seat) {
                return;
            }
            float yaw = ((Number) getYRotMethod.invoke(vehicle)).floatValue();
            yBodyRotField.setFloat(self, yaw);
            yBodyRotOField.setFloat(self, yaw);
        } catch (Throwable error) {
            failed = true;
        }
    }
}
