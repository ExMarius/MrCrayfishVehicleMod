package ro.ai.bridge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registrul personajelor active. */
public final class NpcManager {
    private final Map<String, Npc> npcs = new ConcurrentHashMap<>();

    public Npc spawn(MinecraftServer server, ServerLevel level, String name, Vec3 position) {
        String key = name.toLowerCase();
        if (npcs.containsKey(key)) {
            return npcs.get(key);
        }
        Npc npc = new Npc(level, name, position);
        ServerPlayer player = npc.player();
        level.addFreshEntity(player);
        // apar in lista de jucatori pentru toti clientii (nume + skin)
        server.getPlayerList().broadcastAll(
                ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
        npcs.put(key, npc);
        BridgeLog.info("NPC spawn: " + name + " la " + position);
        return npc;
    }

    public boolean remove(String name) {
        Npc npc = npcs.remove(name.toLowerCase());
        if (npc == null) {
            return false;
        }
        npc.stop();
        npc.player().discard();
        BridgeLog.info("NPC remove: " + name);
        return true;
    }

    public Npc get(String name) {
        return npcs.get(name.toLowerCase());
    }

    public Collection<Npc> all() {
        return npcs.values();
    }

    public void tick() {
        for (Npc npc : npcs.values()) {
            if (!npc.alive()) {
                continue;
            }
            try {
                npc.tick();
            } catch (RuntimeException error) {
                BridgeLog.error("tick " + npc.name() + ": " + error.getMessage());
            }
        }
    }

    public void removeAll() {
        for (Npc npc : npcs.values()) {
            npc.player().discard();
        }
        npcs.clear();
    }
}
