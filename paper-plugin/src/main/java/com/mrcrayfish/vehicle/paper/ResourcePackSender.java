package com.mrcrayfish.vehicle.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Sends the exact configured mandatory pack and is shared by join and /vehicle pack. */
public final class ResourcePackSender {
    private ResourcePackSender() {
    }

    public static boolean send(VehiclePlugin plugin, Player player) {
        String url = plugin.getConfig().getString("resource-pack.url", "").trim();
        if (url.isEmpty()) {
            if (player.isOp()) {
                player.sendRichMessage("<yellow>[Vehicle] Configurează resource-pack.url înainte de testarea modelelor.</yellow>");
            }
            return false;
        }
        String hash = plugin.getConfig().getString("resource-pack.sha1", "").trim();
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", true);
        String prompt = plugin.getConfig().getString("resource-pack.prompt",
                "Acest resource pack este necesar pentru vehicule.");
        player.setResourcePack(url, hash.isEmpty() ? null : hash, required, Component.text(prompt));
        return true;
    }
}
