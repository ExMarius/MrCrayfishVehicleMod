package ro.ai.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Personalitatea fiecarui personaj: un fisier text usor de editat. */
public final class Personality {
    private static final String DEFAULT = """
            Esti %s, un tovaras de aventura intr-o lume Minecraft.
            Vorbesti romana, scurt si natural, ca un prieten - nu ca un asistent.
            Reguli: 1-2 propozitii; fara markdown, emoji sau asteriscuri; nu inventa fapte; poti intreba inapoi.
            Poti cere o actiune scriind la final, pe linie noua, una din:
            ACTIUNE: vino | ACTIUNE: urmeaza-ma | ACTIUNE: stai | ACTIUNE: priveste-ma
            """;

    private Personality() {
    }

    public static String systemPrompt(String name, Memory memory) {
        String base = load(name);
        return base.formatted(name)
                + "\n\nCe stii despre lume si despre jucator: " + memory.knownFacts()
                + "\nUltimele replici (cele mai vechi primele):\n" + memory.transcript();
    }

    private static String load(String name) {
        Path file = Path.of("config", "aibridge", "personalities", name.toLowerCase() + ".txt");
        try {
            if (Files.exists(file)) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
            Files.createDirectories(file.getParent());
            String text = DEFAULT.formatted(name);
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return text;
        } catch (IOException error) {
            BridgeLog.warn("personalitatea nu s-a putut citi: " + error.getMessage());
            return DEFAULT.formatted(name);
        }
    }
}
