package ro.ai.bridge;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Un personaj = un jucator-fantoma pe server. Pentru ceilalti jucatori si pentru
 * celelalte moduri este un jucator normal: are inventar, poate folosi iteme,
 * poate sapa si pune blocuri. Creierul lui (AI) ruleaza in afara jocului si
 * trimite decizii prin puntea HTTP.
 */
public final class Npc {
    private final String name;
    private final ServerPlayer player;
    private Vec3 target;
    private float walkYaw;
    private long spawnedAt = System.currentTimeMillis();

    public Npc(ServerLevel level, String name, Vec3 position) {
        this.name = name;
        UUID id = UUID.nameUUIDFromBytes(("aibridge:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(id, name);
        this.player = new FakePlayer(level, profile);
        this.player.setPos(position.x, position.y, position.z);
    }

    public String name() {
        return name;
    }

    public ServerPlayer player() {
        return player;
    }

    public boolean alive() {
        return player != null && !player.isRemoved();
    }

    public Vec3 position() {
        return player.position();
    }

    public void walkTo(Vec3 destination) {
        this.target = destination;
    }

    public void stop() {
        this.target = null;
    }

    public void look(float yaw, float pitch) {
        this.player.setYRot(yaw);
        this.player.setYHeadRot(yaw);
        this.player.setXRot(pitch);
    }

    /** Un pas de mers, apelat din tick-ul serverului. */
    public void tick() {
        player.tick();

        if (target == null) {
            return;
        }
        Vec3 position = player.position();
        Vec3 delta = target.subtract(position);
        double distance = delta.length();
        if (distance < 0.6D) {
            target = null;
            return;
        }
        Vec3 step = delta.normalize().scale(0.21D);
        walkYaw = (float) Math.toDegrees(Math.atan2(-step.x, step.z));
        player.setYRot(walkYaw);
        player.setYHeadRot(walkYaw);
        player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
        player.move(net.minecraft.world.entity.MoverType.SELF, player.getDeltaMovement());
    }

    public long spawnedAt() {
        return spawnedAt;
    }
}
