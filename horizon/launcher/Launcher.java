import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Puntea dintre panel si Horizon Server.
 *
 * Panelul ruleaza `java -jar <SERVER_JARFILE>`, iar noi punem acest jar in locul
 * serverului NeoForge. El despacheteaza binarul (o singura data), scrie server.toml
 * la primul start si porneste `horizon-server`, cu tot ce iese pe ecran scris si in
 * horizon/console.log (ca sa pot citi log-ul prin SFTP).
 */
public final class Launcher {
    public static void main(String[] args) throws Exception {
        Path base = Paths.get("").toAbsolutePath();
        Path dir = base.resolve("horizon");
        Files.createDirectories(dir);

        Path exe = dir.resolve("horizon-server");
        Path archive = dir.resolve("horizon-server-linux-aarch64.tar.gz");

        if (!Files.exists(exe) && Files.exists(archive)) {
            System.out.println("[horizon-launch] despachetez " + archive.getFileName());
            run(base, "tar", "-xzf", archive.toString(), "-C", dir.toString());
        }
        if (!Files.exists(exe)) {
            System.out.println("[horizon-launch] EROARE: lipseste " + exe);
            System.exit(1);
        }
        exe.toFile().setExecutable(true);
        Path engine = dir.resolve("horizon-engine");
        if (Files.exists(engine)) {
            engine.toFile().setExecutable(true);
        }

        // Mod intretinere: daca exista horizon/exec.txt, rulez comanda din el si ies.
        Path execFile = dir.resolve("exec.txt");
        if (Files.exists(execFile)) {
            String command = Files.readString(execFile).trim();
            Files.delete(execFile);
            System.out.println("[horizon-launch] exec: " + command);
            ProcessBuilder shell = new ProcessBuilder("sh", "-c", command);
            shell.directory(base.toFile());
            shell.redirectErrorStream(true);
            Path log = dir.resolve("console.log");
            Files.createDirectories(log.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         shell.start().getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                }
            }
            System.out.println("[horizon-launch] exec terminat");
            System.exit(0);
        }

        // Datele jocului: daca exista 26.3.tar.gz si nu e inca importat, il desfac si il import.
        Path resourceTar = base.resolve("26.3.tar.gz");
        Path imported = base.resolve("resources").resolve("26.3");
        if (Files.exists(resourceTar) && !Files.exists(imported)) {
            System.out.println("[horizon-launch] desfac datele jocului din 26.3.tar.gz");
            run(base, "tar", "-xzf", resourceTar.toString(), "-C", base.toString());
            System.out.println("[horizon-launch] import-resources");
            run(base, exe.toString(), "import-resources", base.resolve("26.3").toString());
        }

        String port = System.getenv().getOrDefault("SERVER_PORT", "25565");
        Path toml = base.resolve("server.toml");
        if (!Files.exists(toml)) {
            System.out.println("[horizon-launch] primul start: scriu server.toml (port " + port + ")");
            List<String> initArgs = new ArrayList<>();
            initArgs.add(exe.toString());
            initArgs.add("init");
            initArgs.add("--yes");
            initArgs.add("--port");
            initArgs.add(port);
            run(base, initArgs.toArray(new String[0]));
        }

        System.out.println("[horizon-launch] arhitectura JVM: " + System.getProperty("os.arch")
                + ", folder: " + base);
        System.out.println("[horizon-launch] pornesc horizon-server run");
        ProcessBuilder builder = new ProcessBuilder(exe.toString(), "run");
        builder.directory(base.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("HORIZON_NO_AUDIO", "1");
        // EULA: daca e acceptat deja pe server (eula.txt de la NeoForge), il dam mai departe
        Path eulaFile = base.resolve("eula.txt");
        if (Files.exists(eulaFile)) {
            String text = Files.readString(eulaFile).replace(" ", "").toLowerCase();
            if (text.contains("eula=true")) {
                builder.environment().put("HORIZON_EULA", "true");
                System.out.println("[horizon-launch] EULA preluat din eula.txt");
            }
        }

        Process process = builder.start();
        Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
        Path log = dir.resolve("console.log");
        try (BufferedWriter writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        }
        int code = process.waitFor();
        System.out.println("[horizon-launch] horizon-server s-a oprit cu codul " + code);
        System.exit(code);
    }

    private static void run(Path base, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(base.toFile());
        builder.inheritIO();
        int code = builder.start().waitFor();
        System.out.println("[horizon-launch] " + command[0] + " -> " + code);
    }
}
