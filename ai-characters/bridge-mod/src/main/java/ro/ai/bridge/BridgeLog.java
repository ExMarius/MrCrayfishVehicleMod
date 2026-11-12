package ro.ai.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Log simplu, prefixat, ca sa fie usor de gasit in consola serverului. */
public final class BridgeLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("AIBridge");

    private BridgeLog() {
    }

    public static void info(String message) {
        LOGGER.info("[AIBridge] {}", message);
    }

    public static void warn(String message) {
        LOGGER.warn("[AIBridge] {}", message);
    }

    public static void error(String message) {
        LOGGER.error("[AIBridge] {}", message);
    }
}
