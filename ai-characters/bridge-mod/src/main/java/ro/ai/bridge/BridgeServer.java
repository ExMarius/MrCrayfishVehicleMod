package ro.ai.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Puntea HTTP dintre creierul AI (care ruleaza in afara jocului) si server.
 * Asculta doar pe 127.0.0.1, deci nu e expusa pe internet.
 *
 *   GET  /state   -> lista personajelor cu pozitii
 *   POST /command -> {"npc":"Uke","action":"walk","x":..,"y":..,"z":..}
 *                    actiuni: spawn, remove, walk, stop, look, say
 */
public final class BridgeServer {
    private final MinecraftServer server;
    private final NpcManager npcs;
    private final int port;
    private HttpServer http;

    public BridgeServer(MinecraftServer server, NpcManager npcs, int port) {
        this.server = server;
        this.npcs = npcs;
        this.port = port;
    }

    public void start() {
        try {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            http.createContext("/state", this::handleState);
            http.createContext("/command", this::handleCommand);
            http.setExecutor(null);
            http.start();
            BridgeLog.info("punte HTTP pornita pe 127.0.0.1:" + port + " (doar local)");
        } catch (IOException error) {
            BridgeLog.error("nu pot porni puntea HTTP: " + error.getMessage());
        }
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
            BridgeLog.info("punte HTTP oprita");
        }
    }

    private void handleState(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("{\"npcs\":[");
        boolean first = true;
        for (Npc npc : npcs.all()) {
            Vec3 position = npc.position();
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"name\":\"").append(escape(npc.name()))
                    .append("\",\"x\":").append(round(position.x))
                    .append(",\"y\":").append(round(position.y))
                    .append(",\"z\":").append(round(position.z))
                    .append('}');
        }
        json.append("]}");
        respond(exchange, 200, json.toString());
    }

    private void handleCommand(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"POST only\"}");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response;
        try {
            JsonObject request = JsonParser.parseString(body).getAsJsonObject();
            String name = request.has("npc") ? request.get("npc").getAsString() : "";
            String action = request.has("action") ? request.get("action").getAsString() : "";
            response = dispatch(name, action, request);
        } catch (RuntimeException error) {
            response = "{\"error\":\"" + escape(String.valueOf(error.getMessage())) + "\"}";
        }
        respond(exchange, 200, response);
    }

    /** Orice modificare de lume se face pe thread-ul serverului. */
    private String dispatch(String name, String action, JsonObject request) {
        final String[] result = {"{\"ok\":false}"};
        Runnable task = () -> {
            try {
                result[0] = execute(name, action, request);
            } catch (RuntimeException error) {
                result[0] = "{\"error\":\"" + escape(String.valueOf(error.getMessage())) + "\"}";
            }
        };
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
            // asteptam maxim 3s rezultatul, ca sa raspundem clientului
            for (int attempt = 0; attempt < 60 && "{\"ok\":false}".equals(result[0]); attempt++) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return result[0];
    }

    private String execute(String name, String action, JsonObject request) {
        switch (action) {
            case "spawn" -> {
                ServerLevel level = server.overworld();
                Vec3 position = new Vec3(
                        request.has("x") ? request.get("x").getAsDouble() : 0.0D,
                        request.has("y") ? request.get("y").getAsDouble() : 64.0D,
                        request.has("z") ? request.get("z").getAsDouble() : 0.0D);
                npcs.spawn(server, level, name, position);
                return "{\"ok\":true,\"spawned\":\"" + escape(name) + "\"}";
            }
            case "remove" -> {
                boolean removed = npcs.remove(name);
                return "{\"ok\":" + removed + "}";
            }
            case "walk" -> {
                Npc npc = npcs.get(name);
                if (npc == null) {
                    return "{\"error\":\"npc inexistent\"}";
                }
                npc.walkTo(new Vec3(
                        request.get("x").getAsDouble(),
                        request.get("y").getAsDouble(),
                        request.get("z").getAsDouble()));
                return "{\"ok\":true}";
            }
            case "stop" -> {
                Npc npc = npcs.get(name);
                if (npc == null) {
                    return "{\"error\":\"npc inexistent\"}";
                }
                npc.stop();
                return "{\"ok\":true}";
            }
            case "look" -> {
                Npc npc = npcs.get(name);
                if (npc == null) {
                    return "{\"error\":\"npc inexistent\"}";
                }
                npc.look((float) request.get("yaw").getAsDouble(),
                        (float) request.get("pitch").getAsDouble());
                return "{\"ok\":true}";
            }
            case "say" -> {
                String text = request.has("text") ? request.get("text").getAsString() : "";
                server.getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("<" + name + "> " + text), false);
                return "{\"ok\":true}";
            }
            default -> {
                return "{\"error\":\"actiune necunoscuta: " + escape(action) + "\"}";
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
