package ro.ai.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Apelul catre AI (Groq, gratuit). Ruleaza in fundal, nu blocheaza serverul. */
public final class Brain {
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Brain() {
    }

    public static CompletableFuture<String> ask(String system, String user) {
        String key = BridgeConfig.API_KEY.get();
        if (key == null || key.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", BridgeConfig.MODEL.get());
        body.addProperty("temperature", 0.8);
        body.addProperty("max_tokens", BridgeConfig.MAX_TOKENS.get());
        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", system);
        messages.add(systemMessage);
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", user);
        messages.add(userMessage);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> extract(response.statusCode(), response.body()))
                .exceptionally(error -> {
                    BridgeLog.warn("AI indisponibil: " + error.getMessage());
                    return "";
                });
    }

    private static String extract(int status, String payload) {
        if (status != 200) {
            BridgeLog.warn("AI a raspuns " + status + ": " + payload);
            return "";
        }
        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString().trim();
        } catch (RuntimeException error) {
            BridgeLog.warn("raspuns AI neasteptat: " + error.getMessage());
            return "";
        }
    }
}
