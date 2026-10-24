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
            case "paint", "vopsea", "vopseste" -> paint(sender);
            default -> help(sender, label);
        }
        return true;
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
        sender.sendRichMessage("<red>Am luat cheia înapoi: " + args[1]
                + " nu mai poate conduce mașina ta.</red>");
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

    private void help(CommandSender sender, String label) {
        sender.sendRichMessage("<gold>Vehicle Plugin</gold> <gray>vehicule pentru clienți vanilla</gray>");
        sender.sendRichMessage("<yellow>/" + label + " spawn <tip></yellow> <gray>- creează un vehicul sau una dintre cele 5 remorci</gray>");
        sender.sendRichMessage("<yellow>/" + label + " remove</yellow> <gray>- elimină vehiculul apropiat</gray>");
        sender.sendRichMessage("<yellow>/" + label + " refuel</yellow> <gray>- umple rezervorul vehiculului apropiat</gray>");
        sender.sendRichMessage("<yellow>/" + label + " gaspump</yellow> <gray>- primești o pompă de benzină plasabilă</gray>");
        sender.sendRichMessage("<yellow>/" + label + " list</yellow> <gray>- număr vehicule active</gray>");
        sender.sendRichMessage("<yellow>/" + label + " paint</yellow> <gray>- paleta noastră de vopsea GRATUITĂ (stai lângă mașina ta)</gray>");
        sender.sendRichMessage("<yellow>/" + label + " podium set <vehicul> <pret></yellow> <gray>- showroom static (admin); remove = șterge podiumul de aici</gray>");
        sender.sendRichMessage("<yellow>/" + label + " save</yellow> <gray>- salvează vehiculele</gray>");
        sender.sendRichMessage("<yellow>/garaj</yellow> <gray>- garajul global (set/remove, liste în chat; acasă gratuit, public cu taxă)</gray>");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("spawn", "remove", "refuel", "gaspump", "jerrycan", "trust", "untrust", "trusted", "magazin", "podium", "list", "save", "pack", "creative", "paint", "vopsea", "vopseste"), args[0]);
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
        if (args.length == 2 && args[0].equalsIgnoreCase("jerrycan")) {
            return filter(List.of("normal", "industrial"), args[1]);
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
