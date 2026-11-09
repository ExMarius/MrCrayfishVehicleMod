package com.mrcrayfish.vehicle.paper.command;

import com.mrcrayfish.vehicle.paper.ResourcePackSender;
import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.gaspump.GasPumpItem;
import com.mrcrayfish.vehicle.paper.jerrycan.JerryCanItem;
import com.mrcrayfish.vehicle.paper.gaspump.GasPumpManager;
import com.mrcrayfish.vehicle.paper.runtime.PaperTrailer;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.TrailerSpec;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.Locale;

public final class VehicleCommand implements CommandExecutor, TabCompleter {
    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final GasPumpManager gasPumps;

    public VehicleCommand(VehiclePlugin plugin, VehicleManager vehicles, GasPumpManager gasPumps) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.gasPumps = gasPumps;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(sender, args);
            case "remove" -> remove(sender);
            case "refuel" -> refuel(sender);
            case "gaspump" -> gaspump(sender);
            case "jerrycan" -> jerrycan(sender, args);
            case "trust" -> trust(sender, args);
            case "untrust" -> untrust(sender, args);
            case "trusted" -> trusted(sender);
            case "perf", "consum", "stats" -> perf(sender);
            case "magazin", "shop", "dealer" -> dealer(sender);
            case "podium" -> podium(sender, args);
            case "list" -> sender.sendRichMessage("<gold>Vehicule:</gold> <white>"
                    + vehicles.vehicles().size() + " active</white><gray>, "
                    + vehicles.pendingVehicleCount() + " inactive/în așteptare, "
                    + vehicles.trailers().trailers().size() + " remorci, "
                    + gasPumps.pumps().size() + " pompe de benzină</gray>");
            case "save" -> {
                if (!(sender instanceof Player pl && pl.isOp()) && !sender.hasPermission("vehicle.admin")) {
                    sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
                    return true;
                }
                vehicles.save();
                sender.sendRichMessage("<green>Vehicule salvate.</green>");
            }
            case "pack" -> pack(sender);
            case "creative" -> creative(sender);
            // /vehicle garaj = aceeasi logica hibrida cu /garaj (hintul de la join
            // trimitea la o subcomanda INEXISTENTA - masinile parea ca „dispar").
            case "garaj", "garage", "parcari" -> garaj(sender, command, label);
            case "paint", "vopsea", "vopseste" -> paint(sender);
            case "give" -> give(sender, args);
            case "propsdebug" -> propsDebug(sender);
            case "seattest" -> seatTest(sender, args);
            case "seatkind" -> seatKindCmd(sender, args);
            case "seatyaw" -> seatYawCmd(sender, args);
            case "seatlook" -> seatLookCmd(sender, args);
            default -> help(sender, label);
        }
        return true;
    }

    /** /vehicle seatlook <grade>: toleranta blocarii camerei pe directia
     *  vehiculului (0 = blocaj total, 45 = liber, 999 = dezactivat). */
    private void seatLookCmd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>seatlook actual: "
                    + com.mrcrayfish.vehicle.paper.render.LandVehicleRig.lookLockTolerance()
                    + " grade | folosire: /vehicle seatlook <0..999></yellow>");
            return;
        }
        try {
            float degrees = Float.parseFloat(args[1].replace(',', '.'));
            com.mrcrayfish.vehicle.paper.render.LandVehicleRig.setLookLockTolerance(degrees);
            sender.sendRichMessage("<green>seatlook = "
                    + com.mrcrayfish.vehicle.paper.render.LandVehicleRig.lookLockTolerance()
                    + " grade (999 = dezactivat)</green>");
        } catch (NumberFormatException error) {
            sender.sendRichMessage("<red>Folosire: /vehicle seatlook <0..999></red>");
        }
    }

    /** /vehicle seatyaw <anchor|mount|both|off>: alege CINE poarta rotatia
     *  scaunului (test live pentru "corpul sa se invarta cu masina"). */
    private void seatYawCmd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>seatyaw actual: "
                    + com.mrcrayfish.vehicle.paper.render.LandVehicleRig.seatYawMode()
                    + " | folosire: /vehicle seatyaw <anchor|mount|both|off></yellow>");
            return;
        }
        String mode = args[1].toLowerCase(java.util.Locale.ROOT);
        if (!List.of("anchor", "mount", "both", "off").contains(mode)) {
            sender.sendRichMessage("<red>Folosire: /vehicle seatyaw <anchor|mount|both|off></red>");
            return;
        }
        com.mrcrayfish.vehicle.paper.render.LandVehicleRig.setSeatYawMode(mode);
        sender.sendRichMessage("<green>seatyaw = " + mode + "</green>");
    }

    /** /vehicle seatkind <cal|armorstand>: alege entitatea-scaun si o aplica
     *  imediat tuturor vehiculelor (te da jos; urci din nou). */
    private void seatKindCmd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>seatkind actual: "
                    + com.mrcrayfish.vehicle.paper.render.LandVehicleRig.seatKind()
                    + " | folosire: /vehicle seatkind <porc|cal|armorstand|barca></yellow>");
            return;
        }
        String kind = args[1].toLowerCase(java.util.Locale.ROOT);
        if (kind.equals("armura") || kind.equals("horse")) {
            kind = kind.equals("armura") ? "armorstand" : "cal";
        }
        if (kind.equals("boat") || kind.equals("barcuta")) {
            kind = "barca";
        }
        if (!List.of("porc", "cal", "armorstand", "barca").contains(kind)) {
            sender.sendRichMessage("<red>Folosire: /vehicle seatkind <porc|cal|armorstand></red>");
            return;
        }
        com.mrcrayfish.vehicle.paper.render.LandVehicleRig.setSeatKind(kind);
        int reset = plugin.vehicles().resetAllSeats();
        sender.sendRichMessage("<green>seatkind = " + kind
                + " (scaune recreate: " + reset + "). Urcă-te din nou în mașină.</green>");
    }

    /** /vehicle creative (mcv.creative): meniul complet al sistemului mecanic. */
    private void creative(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Doar din joc.");
            return;
        }
        if (!com.mrcrayfish.vehicle.paper.mechanic.MechanicManager.has(player, "mcv.creative")) {
            sender.sendRichMessage("<red>Nu ai permisiunea mcv.creative.</red>");
            return;
        }
        com.mrcrayfish.vehicle.paper.mechanic.CreativeGui.open(player);
    }

    /** /vehicle paint: paleta noastra de vopsea, GRATUITA (fara consum de iteme), pe
     *  cea mai apropiata masina pe care jucatorul are voie s-o conduca (proprietar,
     *  trusted via /vehicle trust, sau masina fara proprietar). Sistem propriu -
     *  inlocuieste fluxul cu spray can din mod pentru vopsirea de zi cu zi. */
    private void paint(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Doar din joc.");
            return;
        }
        if (plugin.mechanic() == null || !plugin.mechanic().paintMenuEnabled()) {
            player.sendRichMessage("<red>Vopsirea libera este dezactivata in config.</red>");
            return;
        }
        double radius = plugin.mechanic().paintMenuRadius();
        LandVehicle best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LandVehicle vehicle : vehicles.vehicles()) {
            if (vehicle.location().getWorld() != player.getWorld()
                    || !vehicle.canDrive(player)) {
                continue;
            }
            double distance = vehicle.location().distanceSquared(player.getLocation());
            if (distance <= radius * radius && distance < bestDistance) {
                bestDistance = distance;
                best = vehicle;
            }
        }
        if (best == null) {
            player.sendRichMessage("<red>Nicio masina pe care ai voie s-o vopsesti la "
                    + "mai putin de " + (int) radius + " blocuri - stai langa masina ta.</red>");
            return;
        }
        // Paleta a fost eliminata (refacere 2026-10-24): vopsirea = capsula de spray
        // din mana, pe masina ridicata la mecanic. Comanda doar indruma jucatorul.
        player.sendRichMessage("<gray>Vopseste cu o <gold>capsula de spray</gold> in mana, "
                + "direct pe masina <gold>ridicata pe cric</gold> (la mecanic). "
                + "Apropie-te de bancul de lucru pentru cric si unelte.</gray>");
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (!(sender instanceof Player pl && pl.isOp()) && !sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
            return;
        }
        String id = args.length < 2 ? "" : args[1];
        LandVehicleSpec vehicleSpec = LandVehicleSpec.byId(id);
        TrailerSpec trailerSpec = TrailerSpec.byId(id);
        if (vehicleSpec == null && trailerSpec == null) {
            sender.sendRichMessage("<yellow>Utilizare: /vehicle spawn <go_kart|lawn_mower|quad_bike|dune_buggy|tractor|dirt_bike|moped|off_roader|sports_car|mini_bus|golf_cart|jet_ski|sports_plane|compact_helicopter|sofacopter|atv|mini_bike|smart_car|speed_boat|aluminum_boat|couch|bumper_car|shopping_cart|fertilizer|seeder|storage_trailer|fluid_trailer|vehicle_trailer></yellow>");
            return;
        }
        if (vehicleSpec != null) {
            LandVehicle vehicle = vehicles.spawn(vehicleSpec, player.getLocation());
            vehicle.setOwner(player.getUniqueId());
            vehicles.save();
            player.sendRichMessage("<green>" + vehicleSpec.displayName() + " creat.</green> <gray>ID: "
                    + vehicle.id() + "</gray>");
        } else {
            PaperTrailer trailer = vehicles.spawn(trailerSpec, player.getLocation());
            vehicles.save();
            player.sendRichMessage("<green>" + trailerSpec.displayName() + " creat.</green> <gray>ID: "
                    + trailer.id() + "</gray>");
        }
    }

    /** Forward spre comanda /garaj (aceeasi instanta de logica, fara duplicare). */
    private void garaj(CommandSender sender, org.bukkit.command.Command command, String label) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        new GarajCommand(plugin, vehicles).onCommand(player, command, label, new String[0]);
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (!(sender instanceof Player pl && pl.isOp()) && !sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
            return;
        }
        // Masinile de podium sunt DOAR de vizual: nu pot fi tinta /vehicle remove.
        LandVehicle vehicle = vehicles.nearest(player.getLocation(), 6.0D, false).orElse(null);
        PaperTrailer trailer = vehicles.trailers().nearest(player.getLocation(), 6.0D).orElse(null);
        if (vehicle == null && trailer == null) {
            LandVehicle any = vehicles.nearest(player.getLocation(), 6.0D).orElse(null);
            if (any != null && any.showcase()) {
                player.sendRichMessage("<red>Masina de pe podium e doar de <gold>VIZUAL</gold> "
                        + "- nu se sterge cu /vehicle remove. Sterge tot podiumul cu "
                        + "<gold>/vehicle podium remove</gold> langa el.</red>");
            } else {
                player.sendRichMessage("<red>Nu există niciun vehicul sau remorcă la mai puțin de 6 blocuri.</red>");
            }
        } else if (trailer != null && (vehicle == null
                || trailer.location().distanceSquared(player.getLocation())
                < vehicle.location().distanceSquared(player.getLocation()))) {
            vehicles.trailers().remove(trailer);
            player.sendRichMessage("<green>Cea mai apropiată remorcă a fost eliminată.</green>");
        } else {
            vehicles.remove(vehicle);
            player.sendRichMessage("<green>Cel mai apropiat vehicul a fost eliminat.</green>");
        }
    }

    private void refuel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (!(sender instanceof Player pl && pl.isOp()) && !sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
            return;
        }
        vehicles.nearest(player.getLocation(), 6.0D).ifPresentOrElse(vehicle -> {
            vehicle.setFuel(vehicle.spec().energyCapacity());
            vehicles.save();
            player.sendRichMessage("<green>Rezervorul vehiculului a fost umplut.</green>");
        }, () -> player.sendRichMessage("<red>Nu există niciun vehicul la mai puțin de 6 blocuri.</red>"));
    }

    /** Any player may run this, no permission gate: a gas pump is a placeable item, not an admin spawn. */
    private void gaspump(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        ItemStack item = GasPumpItem.create();
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendRichMessage("<green>Ai primit o pompă de benzină.</green> "
                + "<gray>Plaseaz-o cu click dreapta, ca pe un bloc normal.</gray>");
    }

    /** Jerry cans are fuel containers any player may get, exactly like the gas pump item. */
    private void jerrycan(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        JerryCanItem.Type type = args.length > 1 && args[1].equalsIgnoreCase("industrial")
                ? JerryCanItem.Type.INDUSTRIAL : JerryCanItem.Type.NORMAL;
        ItemStack item = JerryCanItem.create(type);
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendRichMessage("<green>Ai primit o " + type.label.toLowerCase(Locale.ROOT) + ".</green> "
                + "<gray>Umple-o la o pompă (click dreapta), apoi toarnă în vehicul (click dreapta).</gray>");
    }

    /** /vehicle garage: legacy alias - everything moved to the hybrid /garaj command. */
    private void garage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        sender.sendRichMessage("<gray>Totul despre garaj s-a mutat pe <gold>/garaj</gold>: "
                + "same list everywhere - acasă scoți GRATUIT, dintr-un cerc PUBLIC plătești "
                + "taxa din config. Adminii creează parcări publice cu <gold>/garaj set public</gold>.</gray>");
    }

    /** /vehicle magazin - the FiveM dealer (podium / catalog). */
    private void dealer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executata de un jucator.</red>");
            return;
        }
        plugin.shopGui().open(player);
    }

    /** /vehicle podium set <vehicul> <pret> | /vehicle podium remove - static showrooms. */
    private void podium(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executata de un jucator.</red>");
            return;
        }
        if (!player.isOp() && !sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Doar administratorii pot administra podiumurile.</red>");
            return;
        }
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Utilizare: /vehicle podium set <vehicul> <pret>"
                    + " | /vehicle podium remove</yellow>");
            return;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            if (plugin.dealerStations().removeAt(player)) {
                player.sendRichMessage("<gold>Podium sters de aici: platforma, masina statica "
                        + "si holograma au disparut.</gold>");
            } else {
                player.sendRichMessage("<red>Niciun podium in jur (maxim 6 blocuri).</red>");
            }
            return;
        }
        if (!args[1].equalsIgnoreCase("set") || args.length < 4) {
            sender.sendRichMessage("<yellow>Utilizare: /vehicle podium set <vehicul> <pret>"
                    + " (ex: sports_car 60000).</yellow>");
            return;
        }
        double price;
        try {
            price = Double.parseDouble(args[3]);
            if (price < 0.0D) {
                throw new NumberFormatException("negative");
            }
        } catch (NumberFormatException badPrice) {
            sender.sendRichMessage("<red>Pret invalid: " + args[3] + " (foloseste un numar, ex: 25000).</red>");
            return;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(6,
                org.bukkit.FluidCollisionMode.NEVER);
        if (target == null || target.getType().isAir()) {
            sender.sendRichMessage("<yellow>Uita-te la pamant in locul unde vrei showroomul "
                    + "(maxim 6 blocuri) si reia comanda.</yellow>");
            return;
        }
        Location foot = target.getRelative(org.bukkit.block.BlockFace.UP).getLocation();
        plugin.dealerStations().place(player, foot, args[2], price);
    }

    /** The vehicle at hand for TRUST commands: the nearest one you OWN within 6 blocks. */
    private static LandVehicle targetOwnedVehicle(VehicleManager vehicles, Player player) {
        List<LandVehicle> owned = vehicles.ownedWithin(player, 6.0D);
        LandVehicle best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LandVehicle vehicle : owned) {
            double distance = vehicle.location().distanceSquared(player.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = vehicle;
            }
        }
        return best;
    }

    /** /vehicle trust <name> - hands the wheel of your nearest owned vehicle to that player. */
    private void trust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Utilizare: /vehicle trust <nume-jucător></yellow>");
            return;
        }
        LandVehicle vehicle = targetOwnedVehicle(vehicles, player);
        if (vehicle == null) {
            sender.sendRichMessage("<red>Niciuna dintre mașinile tale nu e aproape (6 blocuri).</red>");
            return;
        }
        java.util.UUID targetUuid = resolvePlayer(args[1]);
        if (targetUuid == null) {
            sender.sendRichMessage("<red>Jucătorul nu există (trebuie să fi intrat măcar o dată).</red>");
            return;
        }
        if (targetUuid.equals(player.getUniqueId())) {
            sender.sendRichMessage("<yellow>Tu ești deja proprietarul!</yellow>");
            return;
        }
        vehicle.trust(targetUuid);
        vehicles.save();
        sender.sendRichMessage("<green>" + args[1] + " poate acum conduce mașina ta: "
                + vehicle.spec().displayName() + ".</green>");
        Player targetOnline = Bukkit.getPlayer(targetUuid);
        if (targetOnline != null) {
            targetOnline.sendRichMessage("<gold>" + player.getName() + " ți-a dat voie să conduci: "
                    + vehicle.spec().displayName() + ". Te poți urca la volan!</gold>");
        }
    }

    /** /vehicle untrust <name> - takes the wheel back. */
    private void untrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Utilizare: /vehicle untrust <nume-jucător></yellow>");
            return;
        }
        LandVehicle vehicle = targetOwnedVehicle(vehicles, player);
        if (vehicle == null) {
            sender.sendRichMessage("<red>Niciuna dintre mașinile tale nu e aproape (6 blocuri).</red>");
            return;
        }
        java.util.UUID targetUuid = resolvePlayer(args[1]);
        if (targetUuid == null) {
            sender.sendRichMessage("<red>Jucătorul nu există (trebuie să fi intrat măcar o dată).</red>");
            return;
        }
        if (!vehicle.untrust(targetUuid)) {
            sender.sendRichMessage("<yellow>" + args[1] + " nu avea permisiune la această mașină.</yellow>");
            return;
        }
        vehicles.save();
        sender.sendRichMessage("<red>I-ai retras accesul: " + args[1]
                + " nu mai poate conduce mașina ta.</red>");
        // Ciclul AC (spec-ul userului, modulul 3): revocarea in timpul mersului =
        // jucatorul e coborat INSTANT de la volan (nu asteapta sa coboare singur).
        Player drivingNow = Bukkit.getPlayer(targetUuid);
        if (drivingNow != null && vehicle.driver().map(d -> d.equals(drivingNow)).orElse(false)) {
            drivingNow.leaveVehicle();
            drivingNow.sendRichMessage("<red>Proprietarul tocmai ți-a retras accesul - "
                    + "ai fost coborât din " + vehicle.spec().displayName() + ".</red>");
            sender.sendRichMessage("<gray>Era la volan chiar acum - l-am coborât pe loc.</gray>");
        }
    }

    /** /vehicle trusted - who can drive your nearest owned vehicle right now. */
    private void trusted(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        LandVehicle vehicle = targetOwnedVehicle(vehicles, player);
        if (vehicle == null) {
            sender.sendRichMessage("<red>Niciuna dintre mașinile tale nu e aproape (6 blocuri).</red>");
            return;
        }
        if (vehicle.trustedDrivers().isEmpty()) {
            sender.sendRichMessage("<gray>Doar TU poți conduce: " + vehicle.spec().displayName()
                    + ". Adaugă cineva: /vehicle trust <nume>.</gray>");
            return;
        }
        String names = vehicle.trustedDrivers().stream()
                .map(id -> Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName())
                        .orElse(id.toString().substring(0, 8)))
                .collect(java.util.stream.Collectors.joining(", "));
        sender.sendRichMessage("<gold>La volan poate: TU + " + names + "</gold>");
    }

    /** Resolves a user-provided NAME to the offline player uuid (they must have joined once). */
    private static java.util.UUID resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (org.bukkit.OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return offline.getUniqueId();
            }
        }
        return null;
    }

    private static List<String> onTabPlayers() {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
        return names;
    }

    /** USER RULE: without LuckPerms on the server, OP = full power (no permission drama). */
    private void pack(CommandSender sender) {
        if (sender instanceof Player player) {
            if (ResourcePackSender.send(plugin, player)) {
                player.sendRichMessage("<green>Resource pack-ul a fost retrimis. Acceptă descărcarea.</green>");
            }
            return;
        }
        String url = plugin.getConfig().getString("resource-pack.url", "");
        sender.sendRichMessage(url.isBlank()
                ? "<yellow>Resource pack-ul nu are încă un URL configurat.</yellow>"
                : "<green>Resource pack configurat:</green> <gray>" + url + "</gray>");
    }


    /** /vehicle perf (alias consum/stats) - ciclul AB: cat consuma pluginul ACUM.
     *  Citeste doar contori mentinuti oricum (vl TPS/JVM/JMX + cronometrul de tick
     *  al pluginului); singura numaratoare one-shot (entitatile mcv_) se face
     *  strict la invocare, niciodata in fundal. */
    private void perf(CommandSender sender) {
        if (!sender.hasPermission("mcv.perf")) {
            sender.sendRichMessage("<red>Nu ai permisiunea <gold>mcv.perf</gold>.</red>");
            return;
        }
        double[] tps = Bukkit.getTPS();
        double mspt = -1.0D;
        try {
            Object arr = Bukkit.class.getMethod("getAverageTickTime").invoke(null);
            if (arr instanceof double[] doubles && doubles.length > 0) {
                mspt = doubles[0];
            } else if (arr instanceof long[] longs && longs.length > 0) {
                mspt = longs[0];
            }
        } catch (Throwable noPaperApi) {
            // Paper nu expune MSPT aici - lasam ascuns
        }
        double cpu = -1.0D;
        try {
            java.lang.management.OperatingSystemMXBean os =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                cpu = sun.getProcessCpuLoad() * 100.0D;
            }
        } catch (Throwable ignored) {
            // JVM fara sun MXBean - lasam ascuns
        }
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        long maxMb = runtime.maxMemory() / 1048576L;
        double emaMs = com.mrcrayfish.vehicle.paper.runtime.PerfTracker.emaMillis();
        double avgMs = com.mrcrayfish.vehicle.paper.runtime.PerfTracker.avgPerTickLastMinuteMillis();
        double maxMs = com.mrcrayfish.vehicle.paper.runtime.PerfTracker.maxTickLastMinuteMillis();
        int onJack = 0;
        for (LandVehicle vehicle : vehicles.vehicles()) {
            if (vehicle.onJack()) {
                onJack++;
            }
        }
        int taskCount = 0;
        for (org.bukkit.scheduler.BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            if (task.getOwner() == plugin) {
                taskCount++;
            }
        }
        int displays = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                for (String tag : entity.getScoreboardTags()) {
                    if (tag.startsWith("mcv_")) {
                        displays++;
                        break;
                    }
                }
            }
        }
        sender.sendRichMessage("<gold>Consum MCV (live)</gold> <gray>- citire de contori, nu adauga sarcina</gray>");
        sender.sendRichMessage("<gray>Server:</gray> TPS <aqua>" + fmt(tps[0]) + " / " + fmt(tps[1]) + " / " + fmt(tps[2])
                + "</aqua> <gray>(1m/5m/15m)</gray>"
                + (mspt >= 0.0D ? " · tick mediu <aqua>" + fmt(mspt) + " ms</aqua> <gray>(buget 50)</gray>" : ""));
        sender.sendRichMessage("<gray>JVM:</gray> "
                + (cpu >= 0.0D ? "CPU proces <aqua>" + fmt(cpu) + "%</aqua> · " : "")
                + "RAM <aqua>" + usedMb + " / " + maxMb + " MB</aqua>");
        sender.sendRichMessage("<gray>Tick vehicule (pluginul nostru):</gray> instant <yellow>" + fmt(emaMs) + " ms</yellow>"
                + " · media 60s <yellow>" + fmt(avgMs) + " ms</yellow>"
                + " · varf 60s <yellow>" + fmt(maxMs) + " ms</yellow>"
                + " <gray>(= " + fmt(avgMs / 50.0D * 100.0D) + "% din bugetul unui tick)</gray>");
        sender.sendRichMessage("<gray>Numaratori:</gray> <white>" + vehicles.vehicles().size() + "</white> vehicule active"
                + " · <white>" + vehicles.pendingVehicleCount() + "</white> in asteptare/hibernate"
                + " · <white>" + vehicles.garageSize() + "</white> in garaje"
                + " · <white>" + vehicles.trailers().trailers().size() + "</white> remorci"
                + " · <white>" + displays + "</white> entitati mcv_");
        sender.sendRichMessage("<gray>Service:</gray> <white>" + (plugin.mechanic() == null ? 0 : plugin.mechanic().jacks().jackCount())
                + "</white> cricuri (" + onJack + " cu masina sus)"
                + " · <white>" + (plugin.mechanic() == null ? 0 : plugin.mechanic().workstations().stationCount())
                + "</white> banci de lucru · <white>" + gasPumps.pumps().size() + "</white> pompe"
                + " · <white>" + taskCount + "</white> taskuri in scheduler");
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private void help(CommandSender sender, String label) {
        sender.sendRichMessage("<gold>Vehicle Plugin</gold> <gray>vehicule pentru clienți vanilla</gray>");
        sender.sendRichMessage("<yellow>/" + label + " spawn <tip></yellow> <gray>- creează un vehicul sau una dintre cele 5 remorci</gray>");
        sender.sendRichMessage("<yellow>/" + label + " remove</yellow> <gray>- elimină vehiculul apropiat</gray>");
        sender.sendRichMessage("<yellow>/" + label + " refuel</yellow> <gray>- umple rezervorul vehiculului apropiat</gray>");
        sender.sendRichMessage("<yellow>/" + label + " gaspump</yellow> <gray>- primești o pompă de benzină plasabilă</gray>");
        sender.sendRichMessage("<yellow>/" + label + " list</yellow> <gray>- număr vehicule active</gray>");
        sender.sendRichMessage("<yellow>/" + label + " paint</yellow> <gray>- paleta noastră de vopsea GRATUITĂ (stai lângă mașina ta)</gray>");
        sender.sendRichMessage("<yellow>/" + label + " give <cone|boostpad|pipe> [n]</yellow> <gray>- con / placă de boost / țeavă de fluid, blocuri reale plasabile (admin)</gray>");
        sender.sendRichMessage("<yellow>/" + label + " podium set <vehicul> <pret></yellow> <gray>- showroom static (admin); remove = șterge podiumul de aici</gray>");
        sender.sendRichMessage("<yellow>/" + label + " save</yellow> <gray>- salvează vehiculele</gray>");
        sender.sendRichMessage("<yellow>/" + label + " perf</yellow> <gray>- cât consumă pluginul acum (tick/RAM/contoare), fără să consume (op)</gray>");
        sender.sendRichMessage("<yellow>/garaj</yellow> <gray>- garajul global (set/remove, liste în chat; acasă gratuit, public cu taxă)</gray>");
    }

    /** CICLUL AJ.5: diagnostica blocului-tinta pentru problemele de randare a
     *  props-urilor (con/placa/teava). Arata CE crede serverul ca e acolo + daca
     *  mai bantui entitati-fantoma mcv_prop in jur. */
    /**
     * Ciclul BL diagnostic: "corpul se rasuceste cu mouse-ul in scaun" nu s-a
     * rezolvat cu porcul-cu-saua (vechi) nici cu calul-fara-saua (BL). Vanilla
     * decide client-side cum leaga corpul calareului de vehicul si depinde de
     * TIPUL entitatii (barci, cai, porci se comporta diferit) - asta nu se
     * poate simula offline. spawneaza un rand de 4 scaune test vizibile si
     * monteaza jucatorul programatic pe varianta ceruta, ca sa identificam
     * empiric pe serverul real ce tipuletz de scaun tine corpul blocat si
     * capul liber. Auto-curatare dupa 5 minute.
     */
    private final java.util.List<java.util.UUID> seatTestEntities = new java.util.ArrayList<>();

    private void seatTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Doar din joc.</red>");
            return;
        }
        if (args.length < 2) {
            sender.sendRichMessage("<red>Folosire: /" + "vehicle seattest <b|c></red>"
                    + " b=cal fara saua (acum), c=cal cu saua (vanilla clasic).");
            return;
        }
        // curata randul vechi (daca exista)
        for (java.util.UUID id : seatTestEntities) {
            Entity old = Bukkit.getEntity(id);
            if (old != null) {
                old.remove();
            }
        }
        seatTestEntities.clear();

        Location eye = player.getLocation();
        float yaw = eye.getYaw();
        double radians = Math.toRadians(yaw);
        // randul sta perpendicular pe privire, la 4 blocuri in fata jucatorului
        double fx = -Math.sin(radians), fz = Math.cos(radians);
        Location base = eye.clone().add(fx * 4.0D, 0.0D, fz * 4.0D);
        base.setYaw((float) Math.toDegrees(Math.atan2(-fx, fz)));
        double rx = Math.cos(radians), rz = Math.sin(radians);

        java.util.List<Entity> spawned = new java.util.ArrayList<>();
        spawned.add(spawnSeatTest(base.clone().add(rx * -1.5D, 0, rz * -1.5D), "B cal fara saua", SeatKind.HORSE_BARE));
        spawned.add(spawnSeatTest(base.clone().add(rx * 1.5D, 0, rz * 1.5D), "C cal cu saua", SeatKind.HORSE_SADDLED));
        for (Entity entity : spawned) {
            seatTestEntities.add(entity.getUniqueId());
        }

        int index = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "b" -> 0;
            case "c" -> 1;
            default -> -1;
        };
        if (index < 0) {
            sender.sendRichMessage("<red>Varianta necunoscuta; foloseste a, b, c sau d.</red>");
            return;
        }
        Entity seat = spawned.get(index);
        player.leaveVehicle();
        if (!seat.addPassenger(player)) {
            sender.sendRichMessage("<red>Nu te-a putut urca (permisiuni/protectie?).</red>");
            return;
        }
        sender.sendRichMessage("<gold>[SeatTest]</gold> " + spawnedLabels()[index]
                + " - apasa F5, lasa tastele si invarte doar mouse-ul: uita-te daca corpul sta blocat si doar capul se roteste.");
        // auto-cleanup dupa 5 minute ca sa nu ramana teste ratacite
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (java.util.UUID id : new java.util.ArrayList<>(seatTestEntities)) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
            seatTestEntities.clear();
        }, 20L * 300);
    }

    private enum SeatKind { HORSE_BARE, HORSE_SADDLED }

    private static String[] spawnedLabels() {
        return new String[] {"B cal fara saua", "C cal cu saua (vanilla clasic)"};
    }

    private Horse spawnSeatTest(Location location, String label, SeatKind kind) {
        /* Tipizat Horse de la cap la coada - exact constructia din
         * LandVehicleRig # seatCarrier care se stie ca compileaza in CI. */
        Horse seat = location.getWorld().spawn(location, Horse.class, carrier -> {
            carrier.setTamed(true);
            carrier.setAI(false);
            carrier.setGravity(false);
            carrier.setCollidable(false);
            carrier.setInvulnerable(true);
            carrier.setSilent(true);
            carrier.setPersistent(false);
        });
        if (kind == SeatKind.HORSE_SADDLED) {
            seat.getInventory().setSaddle(new ItemStack(org.bukkit.Material.SADDLE));
        }
        seat.customName(net.kyori.adventure.text.Component.text(label));
        seat.setCustomNameVisible(true);
        seat.addScoreboardTag("mcv_seat_test");
        return seat;
    }

    private void propsDebug(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Doar un jucator poate rula asta.</red>");
            return;
        }
        if (!player.isOp() && !sender.hasPermission("vehicle.admin")) {
            player.sendRichMessage("<red>Nu ai permisiunea.</red>");
            return;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(8);
        if (target == null || target.getType() == org.bukkit.Material.AIR) {
            player.sendRichMessage("<gray>[PropsDebug] nu tintesti niciun bloc (maxim 8m).</gray>");
        } else {
            com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind kind =
                    com.mrcrayfish.vehicle.paper.world.TrafficProps.kindOf(target);
            player.sendRichMessage("<gray>[PropsDebug] bloc: <yellow>" + target.getType()
                    + "</yellow> stare=<aqua>" + target.getBlockData().getAsString() + "</aqua>"
                    + " kind=" + (kind == null ? "<red>null (nu e prop al nostru)</red>"
                        : "<green>" + kind + "</green>")
                    + " @ " + target.getX() + " " + target.getY() + " " + target.getZ());
        }
        int ghosts = 0;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(6, 6, 6)) {
            if (entity.getScoreboardTags().contains("mcv_prop")) {
                ghosts++;
                entity.remove();
            }
        }
        player.sendRichMessage("<gray>[PropsDebug] fantome mcv_prop in 6m: " + ghosts
                + (ghosts > 0 ? " (le-am sters acum)" : ""));
    }

    /** CICLUL AH: obiectele de drum reale (con/placa) - admin-only, stil audit Y:
     *  ce nu incape in inventar nu se pierde, se arunca la picioare. */
    private void give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Doar un jucator poate primi obiecte de drum.</red>");
            return;
        }
        if (!player.isOp() && !sender.hasPermission("vehicle.admin")) {
            player.sendRichMessage("<red>Nu ai permisiunea pentru obiecte de drum.</red>");
            return;
        }
        if (args.length < 2) {
            player.sendRichMessage("<red>/" + "vehicle give <cone|boostpad|pipe|ramp|pump|extractor> [cate]</red>");
            return;
        }
        com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind kind =
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "cone", "con" -> com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind.CONE;
                    case "boostpad", "boost", "pad" ->
                            com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind.BOOST_PAD;
                    case "pipe", "teava", "fluidpipe", "fluid_pipe" ->
                            com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind.FLUID_PIPE;
                    case "ramp", "rampa", "boostramp", "steep", "steepest" ->
                            com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind.STEEP_RAMP;
                    case "pump", "pompa", "fluidpump", "fluid_pump" ->
                            com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind.FLUID_PUMP;
                    case "extractor", "fluidextractor", "fluid_extractor" ->
                            com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind.FLUID_EXTRACTOR;
                    default -> null;
                };
        if (kind == null) {
            player.sendRichMessage("<red>Obiect necunoscut. Foloseste: cone | boostpad | pipe | ramp | pump | extractor</red>");
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException exception) {
                player.sendRichMessage("<red>Cantitatea trebuie sa fie un numar (1-64).</red>");
                return;
            }
        }
        ItemStack given = com.mrcrayfish.vehicle.paper.world.TrafficProps.item(kind, amount);
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(given);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        String propName = switch (kind) {
            case CONE -> "Con de Trafic";
            case BOOST_PAD -> "Placa de Boost";
            case FLUID_PIPE -> "Teava de Fluid";
            case STEEP_RAMP -> "Rampa de Boost Abrupta";
            case FLUID_PUMP -> "Pompa de Fluid";
            case FLUID_EXTRACTOR -> "Extractor de Fluid";
        };
        player.sendRichMessage("<green>Ai primit " + amount + "x " + propName
                + ". Click-dreapta pe sol pentru plasare.</green>");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("spawn", "remove", "refuel", "gaspump", "jerrycan", "trust", "untrust", "trusted", "magazin", "podium", "list", "save", "pack", "creative", "paint", "vopsea", "vopseste", "garaj", "perf", "consum", "stats", "give", "propsdebug", "seattest", "seatkind", "seatyaw", "seatlook"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return filter(onTabPlayers(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("podium")) {
            return filter(java.util.List.of("set", "remove"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("podium") && "set".equalsIgnoreCase(args[1])) {
            return filter(new ArrayList<>(LandVehicleSpec.ids()), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("podium") && "set".equalsIgnoreCase(args[1])) {
            return filter(java.util.List.of("10000", "25000", "60000", "100000"), args[3]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("seatlook")) {
            return filter(List.of("0", "1", "5", "15", "45", "999"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("seatyaw")) {
            return filter(List.of("anchor", "mount", "both", "off"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("seatkind")) {
            return filter(List.of("porc", "cal", "armorstand", "barca"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("seattest")) {
            return filter(List.of("b", "c"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("jerrycan")) {
            return filter(List.of("normal", "industrial"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("cone", "boostpad", "pipe", "ramp", "pump", "extractor"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "8", "16", "64"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            List<String> types = new ArrayList<>(LandVehicleSpec.ids());
            types.addAll(TrailerSpec.ids());
            types.sort(String::compareTo);
            return filter(types, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
