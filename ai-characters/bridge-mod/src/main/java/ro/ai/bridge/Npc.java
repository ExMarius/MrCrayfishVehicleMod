package ro.ai.bridge;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Un personaj = un jucator fara client, inregistrat in lista de jucatori a
 * serverului. Pentru ceilalti jucatori si pentru celelalte moduri este un jucator
 * normal: are inventar, poate folosi iteme, poate sapa si pune blocuri.
 */
public final class Npc {
    private final String name;
    private final ServerLevel level;
    private final ServerPlayer player;
    private Vec3 target;
    private ServerPlayer followTarget;
    private double followDistance = 3.0D;
    private float walkYaw;
    private int tickCounter;
    private int tickErrors;

    public Npc(MinecraftServer server, ServerLevel level, String name, Vec3 position, float yaw) {
        this.name = name;
        this.level = level;
        UUID id = UUID.nameUUIDFromBytes(("aibridge:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(id, name);
        this.player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        this.player.setPos(position.x, position.y, position.z);
        this.walkYaw = yaw;
        this.player.setYRot(yaw);
        this.player.setYHeadRot(yaw);
    }

    /** Inregistreaza jucatorul in server: de aici incolo apare in lista si in lume. */
    public void place(MinecraftServer server) {
        CommonListenerCookie cookie = new CommonListenerCookie(
                player.getGameProfile(), 0, ClientInformation.createDefault(), false);
        server.getPlayerList().placeNewPlayer(
                new FakeClientConnection(PacketFlow.SERVERBOUND), player, cookie);
        player.teleportTo(level, player.getX(), player.getY(), player.getZ(), Set.of(),
                walkYaw, player.getXRot());
    }

    public String name() {
        return name;
    }

    public ServerPlayer player() {
        return player;
    }

    public boolean alive() {
        return !player.isRemoved() && player.isAlive();
    }

    public Vec3 position() {
        return player.position();
    }

    public void walkTo(Vec3 destination) {
        this.target = destination;
    }

    public void stop() {
        this.target = null;
        this.followTarget = null;
        this.player.setDeltaMovement(Vec3.ZERO);
    }

    /** Personajul merge dupa jucator, oprindu-se la distanta data. */
    public void follow(ServerPlayer player, double distance) {
        this.followTarget = player;
        this.followDistance = distance;
        this.target = null;
    }

    public ServerPlayer followTarget() {
        return followTarget;
    }

    public void look(float yaw, float pitch) {
        this.walkYaw = yaw;
        this.player.setYRot(yaw);
        this.player.setYHeadRot(yaw);
        this.player.setXRot(pitch);
    }

    /** Apelat o data pe tick-ul serverului (entitatea se tickuieste separat de vanilla). */
    public void tick() {
        if (tickErrors > 5) {
            return;
        }
        try {
            tickCounter++;
            if (tickCounter % 10 == 0) {
                // tinem personajul "incarcat" in chunk-uri; altfel nu se misca
                player.connection.resetPosition();
                level.getChunkSource().move(player);
            }
            step();
        } catch (RuntimeException error) {
            tickErrors++;
            if (tickErrors == 1) {
                BridgeLog.warn("tick " + name + " esuat: " + error);
            }
        }
    }

    private void step() {
        if (followTarget != null) {
            if (!followTarget.isAlive() || followTarget.isRemoved()) {
                followTarget = null;
            } else if (player.distanceTo(followTarget) > followDistance) {
                target = followTarget.position();
            } else {
                target = null;
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
        if (target == null) {
            return;
        }
        Vec3 position = player.position();
        double distance = position.distanceTo(target);
        if (distance < 0.8D) {
            target = null;
            player.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 step = target.subtract(position).normalize().scale(0.2D);
        walkYaw = (float) Math.toDegrees(Math.atan2(-step.x, step.z));
        player.setYRot(walkYaw);
        player.setYHeadRot(walkYaw);
        player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
        player.move(MoverType.SELF, player.getDeltaMovement());
        player.setDeltaMovement(Vec3.ZERO);
    }
}
