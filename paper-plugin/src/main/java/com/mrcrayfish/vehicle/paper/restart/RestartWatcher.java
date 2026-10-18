package com.mrcrayfish.vehicle.paper.restart;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Watches for a trigger file dropped by the CI deploy pipeline
 * (hidencloud-sftp.yml). When plugins/MrCrayfishVehiclePlugin/.restart-request
 * appears, the plugin deletes it and issues the console restart command,
 * so every deploy self-restarts the server without any panel action.
 */
public final class RestartWatcher {

    private final JavaPlugin plugin;
    private final File triggerFile;
    private BukkitTask task;

    public RestartWatcher(JavaPlugin plugin) {
        this.plugin = plugin;
        this.triggerFile = new File(plugin.getDataFolder(), ".restart-request");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (triggerFile.isFile()) {
                Bukkit.getScheduler().runTask(plugin, RestartWatcher.this::handleRestart);
            }
        }, 60L, 60L);
    }

    private void handleRestart() {
        if (!triggerFile.delete()) {
            return;
        }
        plugin.getLogger().info("[RestartWatcher] Deploy trigger primit - serverul se reincarca in 5 secunde.");
        Bukkit.broadcastMessage("\u00A7d\u00A7lUKEMC \u00A77\u00BB \u00A7fUpdate nou instalat! Serverul se reporneste in 5 secunde...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
        }, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
