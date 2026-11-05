package com.mrcrayfish.vehicle.paper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Sends the exact configured mandatory pack and is shared by join and /vehicle pack. */
public final class ResourcePackSender {
    /** RFC-4122 URL namespace; identical to Python's uuid.NAMESPACE_URL. */
    private static final UUID URL_NAMESPACE = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");

    private ResourcePackSender() {
    }

    /**
     * The server-level pack is sent during login, before PlayerJoinEvent. Avoid
     * sending the same pack again one second later and forcing a second client
     * resource-reload screen: when the client receives the same UUID+hash it skips
     * the download, and when the server already supplies the pack there is nothing
     * to re-send at all. URL and SHA-1 are both checked because Paper's deprecated
     * Bukkit.getResourcePack() may be empty on hosts that configure the pack solely
     * through server.properties. /vehicle pack still calls send() explicitly.
     */
    public static boolean suppliedByServer(VehiclePlugin plugin) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        return samePack(url, Bukkit.getResourcePack())
                || sameSha(sha1, Bukkit.getResourcePackHash());
    }

    static boolean samePack(String pluginUrl, String serverUrl) {
        return pluginUrl != null && serverUrl != null
                && !pluginUrl.trim().isEmpty()
                && pluginUrl.trim().equals(serverUrl.trim());
    }

    static boolean sameSha(String pluginSha, String serverSha) {
        return pluginSha != null && serverSha != null
                && !pluginSha.trim().isEmpty()
                && pluginSha.trim().equalsIgnoreCase(serverSha.trim());
    }

    /**
     * RFC-4122 version-5 (SHA-1 name-based) UUID over the URL namespace. Matches
     * tools/configure_server_resource_pack.py's uuid.uuid5(NAMESPACE_URL, url), so a
     * plugin-sent pack carries the SAME identifier as the login-time server pack and
     * the client deduplicates instead of downloading and reloading a second time.
     */
    static UUID serverPackId(String url) {
        ByteBuffer namespace = ByteBuffer.allocate(16);
        namespace.putLong(URL_NAMESPACE.getMostSignificantBits());
        namespace.putLong(URL_NAMESPACE.getLeastSignificantBits());
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-1 is part of every Java runtime", error);
        }
        digest.update(namespace.array());
        byte[] hashed = digest.digest(url.getBytes(StandardCharsets.UTF_8));
        hashed[6] = (byte) ((hashed[6] & 0x0f) | 0x50); // version 5
        hashed[8] = (byte) ((hashed[8] & 0x3f) | 0x80); // IETF variant
        ByteBuffer buffer = ByteBuffer.wrap(hashed, 0, 16);
        return new UUID(buffer.getLong(), buffer.getLong());
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
        player.setResourcePack(serverPackId(url), url, hash.isEmpty() ? null : hash,
                Component.text(prompt), required);
        return true;
    }
}
