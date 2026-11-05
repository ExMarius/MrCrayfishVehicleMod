package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * CICLUL AU - siguranta vitezometrului pe bara de XP:
 *  - la DELGARE in timpul condusului, XP-ul real se restaureaza imediat;
 *  - la (re)CONECTARE dupa un crash/restart al serverului, markerul din PDC al
 *    jucatorului declanseaza restaurarea si dispare;
 *  - orice ENABLE/reload care trece pe langa coborare curata face acelasi lucru
 *    (metoda e idempotenta si nu atinge jucatorul ne-afectat).
 */
public final class XpSpeedoListener implements Listener {
    private final VehicleManager vehicles;

    public XpSpeedoListener(VehicleManager vehicles) {
        this.vehicles = vehicles;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (vehicles.isXpHijacked(player.getUniqueId())) {
            vehicles.restoreXp(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        // doar jucatorii care au ramas cu markerul din PDC dupa un crash primesc
        // restaurarea (restoreXp() iese silentios la oricine altcineva)
        vehicles.restoreXp(event.getPlayer());
    }
}
