package ro.ai.bridge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Setarile modului: cheia AI (gratuita) si modelul. Fisier: config/aibridge-common.toml */
public final class BridgeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;
    public static final ModConfigSpec.ConfigValue<String> MODEL;
    public static final ModConfigSpec.IntValue MAX_TOKENS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Creierul personajelor (API gratuit Groq - console.groq.com)").push("brain");
        ENABLED = builder.comment("Daca este false, personajele nu raspund in chat.")
                .define("enabled", true);
        API_KEY = builder.comment("Cheia API Groq, incepe cu gsk_ ...")
                .define("apiKey", "");
        MODEL = builder.comment("Modelul folosit")
                .define("model", "llama-3.3-70b-versatile");
        MAX_TOKENS = builder.comment("Cat de lung poate fi raspunsul")
                .defineInRange("maxTokens", 160, 30, 800);
        builder.pop();
        SPEC = builder.build();
    }

    private BridgeConfig() {
    }

    public static boolean ready() {
        return ENABLED.get() && !API_KEY.get().isBlank();
    }
}
