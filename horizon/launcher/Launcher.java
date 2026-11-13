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
            try {
                Files.deleteIfExists(resourceTar);
                System.out.println("[horizon-launch] am sters 26.3.tar.gz (resursele sunt importate)");
            } catch (Exception ignored) {
            }
        }

        String port = detectPort(base);
        System.out.println("[horizon-launch] port: " + port);
        Path toml = base.resolve("server.toml");
        if (!Files.exists(toml)) {
            System.out.println("[horizon-launch] primul start: scriu server.toml");
            run(base, exe.toString(), "init", "--yes", "--no-whitelist", "--port", port);
        }
        if (Files.exists(toml)) {
            String text = Files.readString(toml);
            String fixed = text.replaceAll("(?m)^port\\s*=.*$", "port = " + port)
                    .replaceAll("(?m)^whitelist\\s*=.*$", "whitelist = false");
            if (!fixed.equals(text)) {
                Files.writeString(toml, fixed);
                System.out.println("[horizon-launch] am pus portul " + port + " in server.toml");
            }
        }

        // Engine propriu, cu modul nostru de masini compilat in el (horizon/engine-mod).
        // Il folosim doar daca binarul chiar e ce trebuie: un engine stricat ar lasa serverul jos.
        Path modded = base.resolve("horizon").resolve("horizon-engine-mod");
        boolean moddedUsed = false;
        if (Files.exists(modded)) {
            if (engineLooksRunnable(modded)) {
                System.out.println("[horizon-launch] engine cu modul de masini: " + modded);
                run(base, exe.toString(), "config", "set", "engine.path=horizon/horizon-engine-mod");
                moddedUsed = true;
            } else {
                System.out.println("[horizon-launch] ATENTIE: engine-ul cu modul nu e valid, "
                        + "raman pe engine-ul normal");
            }
        } else {
            // fara fisier nu trebuie sa ramana un engine.path care ar impiedica pornirea
            run(base, exe.toString(), "config", "set", "engine.path=");
        }

        System.out.println("[horizon-launch] arhitectura JVM: " + System.getProperty("os.arch")
                + ", folder: " + base);

        Path log = dir.resolve("console.log");
        long started = System.nanoTime();
        int code = startServer(base, exe, log);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;
        System.out.println("[horizon-launch] horizon-server s-a oprit cu codul " + code
                + " dupa " + seconds + "s");

        // Plasa de siguranta: daca engine-ul nostru moare imediat, revenim la cel normal si mai
        // pornim o data, ca serverul sa nu ramana jos din cauza unui build prost.
        if (moddedUsed && code != 0 && seconds < 120) {
            System.out.println("[horizon-launch] engine-ul cu modul a picat repede; "
                    + "revin la engine-ul normal si repornesc");
            run(base, exe.toString(), "config", "set", "engine.path=");
            code = startServer(base, exe, log);
            System.out.println("[horizon-launch] a doua pornire s-a terminat cu codul " + code);
        }
        System.exit(code);
    }

    /** Porneste `horizon-server run`, copiaza iesirea in console.log si trimite comenzile. */
    private static int startServer(Path base, Path exe, Path log) throws Exception {
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

        Thread output = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                         new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8,
                         StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException ignored) {
            }
        }, "horizon-output");
        output.setDaemon(true);
        output.start();

        // comenzile scrise in consola panelului ajung la horizon-server
        Thread input = new Thread(() -> {
            try (BufferedReader console = new BufferedReader(
                         new InputStreamReader(System.in, StandardCharsets.UTF_8));
                 Writer childIn = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = console.readLine()) != null) {
                    childIn.write(line);
                    childIn.write("\n");
                    childIn.flush();
                }
            } catch (IOException ignored) {
            }
        }, "horizon-input");
        input.setDaemon(true);
        input.start();

        return process.waitFor();
    }

    /** Portul: din mediu (panelul), altfel din server.properties, altfel 25565. */
    private static String detectPort(Path base) {
        String fromEnv = System.getenv("SERVER_PORT");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        Path properties = base.resolve("server.properties");
        if (Files.exists(properties)) {
            try {
                for (String line : Files.readAllLines(properties)) {
                    if (line.startsWith("server-port=")) {
                        return line.substring("server-port=".length()).trim();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "25565";
    }

    /**
     * Verifică binarul fără să-l pornim: engine-ul de joc nu știe `--version` (doar
     * `horizon-server` știe), deci l-am porni degeaba. Ne uităm la antetul ELF: 64 de biți,
     * little-endian, AArch64 — exact ce trebuie să ruleze pe panelul nostru ARM.
     */
    private static boolean engineLooksRunnable(Path engine) {
        try {
            if (!Files.isRegularFile(engine) || Files.size(engine) < 20L * 1024 * 1024) {
                System.out.println("[horizon-launch] engine-ul cu modul e prea mic sau lipseste");
                return false;
            }
            byte[] head = new byte[20];
            try (InputStream in = Files.newInputStream(engine)) {
                if (in.readNBytes(head, 0, head.length) < head.length) {
                    return false;
                }
            }
            boolean elf = head[0] == 0x7F && head[1] == 'E' && head[2] == 'L' && head[3] == 'F';
            boolean elf64LittleEndian = head[4] == 2 && head[5] == 1;
            boolean aarch64 = head[18] == (byte) 0xB7 && head[19] == 0;
            if (!(elf && elf64LittleEndian && aarch64)) {
                System.out.println("[horizon-launch] engine-ul cu modul nu e un ELF aarch64 valid");
                return false;
            }
            engine.toFile().setExecutable(true, false);
            return true;
        } catch (Exception e) {
            System.out.println("[horizon-launch] verificarea engine-ului a picat: " + e);
            return false;
        }
    }

    private static void run(Path base, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(base.toFile());
        builder.inheritIO();
        int code = builder.start().waitFor();
        System.out.println("[horizon-launch] " + command[0] + " -> " + code);
    }
}
