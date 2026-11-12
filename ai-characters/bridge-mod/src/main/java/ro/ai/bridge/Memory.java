package ro.ai.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Memoria unui personaj: ultimele replici + fapte pe termen lung. Salvata pe disc. */
public final class Memory {
    private static final int MAX_EXCHANGES = 24;

    private final Path file;
    private final List<String> exchanges = new ArrayList<>();
    private final List<String> facts = new ArrayList<>();

    public Memory(String name) {
        this.file = Path.of("config", "aibridge", "memory", name.toLowerCase() + ".json");
        load();
    }

    public synchronized void addExchange(String player, String reply) {
        exchanges.add(player + " -> " + reply);
        while (exchanges.size() > MAX_EXCHANGES) {
            exchanges.remove(0);
        }
        save();
    }

    public synchronized void addFact(String fact) {
        if (!facts.contains(fact)) {
            facts.add(fact);
            save();
        }
    }

    public synchronized String transcript() {
        if (exchanges.isEmpty()) {
            return "(prima conversatie)";
        }
        return String.join("\n", exchanges);
    }

    public synchronized String knownFacts() {
        return facts.isEmpty() ? "(nimic inca)" : String.join("; ", facts);
    }

    public synchronized List<String> facts() {
        return new ArrayList<>(facts);
    }

    private void load() {
        try {
            if (!Files.exists(file)) {
                return;
            }
            JsonObject data = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (data.has("exchanges")) {
                for (JsonElement element : data.getAsJsonArray("exchanges")) {
                    exchanges.add(element.getAsString());
                }
            }
            if (data.has("facts")) {
                for (JsonElement element : data.getAsJsonArray("facts")) {
                    facts.add(element.getAsString());
                }
            }
        } catch (IOException | RuntimeException error) {
            BridgeLog.warn("memoria nu s-a putut citi: " + error.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject data = new JsonObject();
            JsonArray exchangeArray = new JsonArray();
            exchanges.forEach(exchangeArray::add);
            JsonArray factArray = new JsonArray();
            facts.forEach(factArray::add);
            data.add("exchanges", exchangeArray);
            data.add("facts", factArray);
            Files.writeString(file, data.toString(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            BridgeLog.warn("memoria nu s-a putut salva: " + error.getMessage());
        }
    }
}
