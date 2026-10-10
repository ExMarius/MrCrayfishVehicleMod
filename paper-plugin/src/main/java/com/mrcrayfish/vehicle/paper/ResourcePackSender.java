package com.mrcrayfish.vehicle.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Sends the exact configured mandatory pack and is shared by join and /vehicle pack. */
public final class ResourcePackSender {
    private ResourcePackSender() {
    }

    /**
     * The server-level pack is sent during login, before PlayerJoinEvent. Avoid
     * sending the same URL again one second later and forcing a second client
     * resource-reload screen. /vehicle pack still calls send() explicitly.
     */
    public static boolean suppliedByServer(VehiclePlugin plugin) {
        return samePack(plugin.getConfig().getString("resource-pack.url", ""), Bukkit.getResourcePack());
    }

    static boolean samePack(String pluginUrl, String serverUrl) {
        return pluginUrl != null && serverUrl != null
                && !pluginUrl.trim().isEmpty()
                && pluginUrl.trim().equals(serverUrl.trim());
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
