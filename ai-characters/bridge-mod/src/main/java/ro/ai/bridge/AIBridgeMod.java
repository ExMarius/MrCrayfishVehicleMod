package ro.ai.bridge;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * AI Bridge — personaje AI ca jucatori pe server (fara client).
 *
 * In joc: scrii in chat numele personajului si el raspunde, gandit de AI:
 *   "Uke vino aici"  /  "Mara ce facem?"
 * Comenzi: /npc spawn|remove|list|walk|say|brain
 */
@Mod(AIBridgeMod.MODID)
public final class AIBridgeMod {
    public static final String MODID = "aibridge";
    private static final int BRIDGE_PORT = 8765;

    private static final Path RESTART_FLAG = Path.of("aibridge.restart");

    private final NpcManager npcs = new NpcManager();
    private final Map<String, Memory> memories = new ConcurrentHashMap<>();
    private BridgeServer bridge;
    private MinecraftServer server;
    private int tickCounter;

    public AIBridgeMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BridgeConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onChat);
    }

    private void onServerStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        this.bridge = new BridgeServer(server, npcs, BRIDGE_PORT);
        this.bridge.start();
        BridgeLog.info("gata. /npc spawn <nume> pentru primul personaj.");
        if (!BridgeConfig.ready()) {
            BridgeLog.warn("creierul AI e oprit: pune cheia Groq in config/aibridge-common.toml (brain.apiKey)");
        }
        runSelfTest();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (bridge != null) {
            bridge.stop();
        }
        npcs.removeAll();
    }

    private void onServerTick(ServerTickEvent.Post event) {
        npcs.tick();
        if (++tickCounter % 100 == 0) {
            checkRestartFlag();
        }
    }

    // ---------------------------------------------------------------- creierul AI

    /** Cand cineva scrie numele personajului in chat, personajul raspunde. */
    private void onChat(ServerChatEvent event) {
        if (!BridgeConfig.ready() || server == null) {
            return;
        }
        String raw = event.getRawText();
        if (raw == null || raw.isBlank()) {
            return;
        }
        String lower = raw.toLowerCase();
        ServerPlayer sender = event.getPlayer();
        for (Npc npc : npcs.all()) {
            if (!lower.contains(npc.name().toLowerCase())) {
                continue;
            }
            String text = raw.replaceAll("(?i)" + Pattern.quote(npc.name()), "")
                    .replaceAll("^[\\s,:;-]+", "")
                    .trim();
            if (text.isEmpty()) {
                text = "salut";
            }
            String userText = text;
            Memory memory = memories.computeIfAbsent(npc.name().toLowerCase(), Memory::new);
            String prompt = sender.getName().getString() + " iti spune: " + userText;
            Brain.ask(Personality.systemPrompt(npc.name(), memory), prompt)
                    .thenAccept(reply -> {
                        if (reply == null || reply.isBlank()) {
                            return;
                        }
                        MinecraftServer current = this.server;
                        if (current == null) {
                            return;
                        }
                        current.execute(() -> handleReply(npc, memory, sender, userText, reply));
                    });
        }
    }

    /** Personajul vorbeste si, daca AI-ul a cerut, executa o actiune. */
    private void handleReply(Npc npc, Memory memory, ServerPlayer sender, String userText, String reply) {
        StringBuilder spoken = new StringBuilder();
        String action = "";
        for (String line : reply.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("ACTIUNE:")) {
                action = trimmed.substring(8).trim().toLowerCase();
            } else if (!trimmed.isEmpty()) {
                if (spoken.length() > 0) {
                    spoken.append(' ');
                }
                spoken.append(trimmed);
            }
        }
        String text = spoken.toString().replaceAll("\\*[^*]*\\*", "").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return;
        }
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("<" + npc.name() + "> " + text), false);
        }
        memory.addExchange(userText, text);
        performAction(npc, sender, action);
    }

    private void performAction(Npc npc, ServerPlayer sender, String action) {
        switch (action) {
            case "vino", "vino-aici", "vino aici" -> npc.walkTo(sender.position());
            case "urmeaza-ma", "urmeaza", "urmeaza-ma!" -> npc.follow(sender, 3.0D);
            case "stai", "opreste-te" -> npc.stop();
            case "priveste-ma" -> {
                Vec3 npcPos = npc.position();
                Vec3 target = sender.position();
                float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - npcPos.x), target.z - npcPos.z));
                npc.look(yaw, 0.0F);
            }
            default -> {
                // fara actiune
            }
        }
    }

    // ---------------------------------------------------------------- comenzi

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("npc")
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    String name = StringArgumentType.getString(context, "name");
                                    ServerLevel level = source.getLevel();
                                    Vec3 position = source.getPosition();
                                    try {
                                        npcs.spawn(source.getServer(), level, name, position);
                                        source.sendSuccess(() -> Component.literal(
                                                "Personaj creat: " + name), false);
                                        return 1;
                                    } catch (RuntimeException error) {
                                        BridgeLog.error("spawn " + name + " esuat: " + error);
                                        source.sendFailure(Component.literal("Spawn esuat: " + error));
                                        return 0;
                                    }
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
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> {
                                                            Npc npc = npcs.get(StringArgumentType.getString(context, "name"));
                                                            if (npc == null) {
                                                                return 0;
                                                            }
                                                            npc.walkTo(new Vec3(
                                                                    DoubleArgumentType.getDouble(context, "x"),
                                                                    DoubleArgumentType.getDouble(context, "y"),
                                                                    DoubleArgumentType.getDouble(context, "z")));
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
                                        }))))
                .then(Commands.literal("brain")
                        .executes(context -> {
                            String status = BridgeConfig.ready()
                                    ? "AI activ, model " + BridgeConfig.MODEL.get()
                                    : "AI oprit (lipseste cheia in config/aibridge-common.toml)";
                            context.getSource().sendSuccess(() -> Component.literal(status), false);
                            return 1;
                        })));
    }

    // ---------------------------------------------------------------- mentenanta

    /**
     * Test automat, pornit doar daca exista fisierul aibridge.selftest in folderul
     * serverului: creeaza un personaj si il ticheaza, ca erorile sa apara in log.
     */
    private void runSelfTest() {
        Path flag = Path.of("aibridge.selftest");
        if (!Files.exists(flag)) {
            return;
        }
        try {
            Files.deleteIfExists(flag);
        } catch (IOException ignored) {
        }
        server.execute(() -> {
            try {
                ServerLevel level = server.overworld();
                BlockPos spawn = level.getSharedSpawnPos();
                Npc npc = npcs.spawn(server, level, "TestNPC",
                        new Vec3(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D));
                BridgeLog.info("SELFTEST: spawn OK, poz=" + npc.position());
                for (int index = 0; index < 5; index++) {
                    npcs.tick();
                }
                BridgeLog.info("SELFTEST: tick OK");
            } catch (RuntimeException error) {
                BridgeLog.error("SELFTEST ESUAT: " + error);
            }
        });
    }

    /** Daca apare fisierul aibridge.restart, opresc serverul curat (panelul il reporneste). */
    private void checkRestartFlag() {
        if (!Files.exists(RESTART_FLAG)) {
            return;
        }
        try {
            Files.deleteIfExists(RESTART_FLAG);
        } catch (IOException error) {
            BridgeLog.warn("nu pot sterge aibridge.restart: " + error.getMessage());
        }
        BridgeLog.info("restart cerut prin fisier - salvez si opresc serverul curat.");
        MinecraftServer current = this.server;
        if (current == null) {
            return;
        }
        current.execute(() -> current.getCommands()
                .performPrefixedCommand(current.createCommandSourceStack(), "stop"));
    }
}
