package ro.ai.bridge;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * AI Bridge — personaje AI ca jucatori-fantoma pe server.
 *
 * Comenzi in joc:
 *   /npc spawn <nume>     creeaza personajul la pozitia ta
 *   /npc remove <nume>
 *   /npc list
 *   /npc walk <nume> <x> <y> <z>
 *   /npc say <nume> <text>
 *
 * Punte HTTP (doar local): 127.0.0.1:8765
 */
@Mod(AIBridgeMod.MODID)
public final class AIBridgeMod {
    public static final String MODID = "aibridge";
    private static final int BRIDGE_PORT = 8765;

    private final NpcManager npcs = new NpcManager();
    private BridgeServer bridge;
    private MinecraftServer server;

    public AIBridgeMod(IEventBus modEventBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onServerStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        this.bridge = new BridgeServer(server, npcs, BRIDGE_PORT);
        this.bridge.start();
        BridgeLog.info("gata. /npc spawn <nume> pentru primul personaj.");
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (bridge != null) {
            bridge.stop();
        }
        npcs.removeAll();
    }

    private void onServerTick(ServerTickEvent.Post event) {
        npcs.tick();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("npc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    String name = StringArgumentType.getString(context, "name");
                                    ServerLevel level = source.getLevel();
                                    Vec3 position = source.getPosition();
                                    npcs.spawn(source.getServer(), level, name, position);
                                    source.sendSuccess(() -> Component.literal(
                                            "Personaj creat: " + name), false);
                                    return 1;
                                })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    boolean removed = npcs.remove(name);
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            removed ? "Sters: " + name : "Nu exista: " + name), false);
                                    return removed ? 1 : 0;
                                })))
                .then(Commands.literal("list")
                        .executes(context -> {
                            StringBuilder text = new StringBuilder("Personaje: ");
                            if (npcs.all().isEmpty()) {
                                text.append("(niciunul)");
                            }
                            for (Npc npc : npcs.all()) {
                                Vec3 position = npc.position();
                                text.append(npc.name()).append(" @ ")
                                        .append(String.format("%.1f %.1f %.1f",
                                                position.x, position.y, position.z))
                                        .append("  ");
                            }
                            String message = text.toString();
                            context.getSource().sendSuccess(() -> Component.literal(message), false);
                            return npcs.all().size();
                        }))
                .then(Commands.literal("walk")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                                        .executes(context -> {
                                                            Npc npc = npcs.get(StringArgumentType.getString(context, "name"));
                                                            if (npc == null) {
                                                                return 0;
                                                            }
                                                            npc.walkTo(new Vec3(
                                                                    com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "x"),
                                                                    com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "y"),
                                                                    com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "z")));
                                                            return 1;
                                                        }))))))
                .then(Commands.literal("say")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            String text = StringArgumentType.getString(context, "text");
                                            context.getSource().getServer().getPlayerList()
                                                    .broadcastSystemMessage(Component.literal(
                                                            "<" + name + "> " + text), false);
                                            return 1;
                                        })))));
    }
}
