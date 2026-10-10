package com.mrcrayfish.vehicle.paper.command;

import com.mrcrayfish.vehicle.paper.ResourcePackSender;
import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.economy.GasPumpManager;
import com.mrcrayfish.vehicle.paper.runtime.PaperTrailer;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.TrailerSpec;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
            case "list" -> sender.sendRichMessage("<gold>Vehicule:</gold> <white>"
                    + vehicles.vehicles().size() + " active</white><gray>, "
                    + vehicles.pendingVehicleCount() + " inactive/în așteptare, "
                    + vehicles.trailers().trailers().size() + " remorci</gray>");
            case "save" -> {
                if (!sender.hasPermission("vehicle.admin")) {
                    sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
                    return true;
                }
                vehicles.save();
                sender.sendRichMessage("<green>Vehicule salvate.</green>");
            }
            case "pack" -> pack(sender);
            case "pump" -> pump(sender, args);
            case "fuelprice" -> fuelPrice(sender, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void pump(CommandSender sender, String[] args) {
        String action = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player player)) {
            // The console has no in-world location, so it can't create/remove/debug "the
            // nearest" pump -- except "debug", which it can run across every registered pump
            // at once, for checking live rig state without needing a player online.
            if (sender.hasPermission("vehicle.admin") && action.equals("debug")) {
                String dump = gasPumps.debugAllPumps();
                plugin.getLogger().info("[pump debug all]\n" + dump);
                sender.sendRichMessage("<gray>Dump trimis în consolă/log.</gray>");
            } else {
                sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            }
            return;
        }
        if (!sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
            return;
        }
        switch (action) {
            case "create" -> {
                RayTraceResult trace = player.rayTraceBlocks(6.0D);
                Block target = trace == null ? null : trace.getHitBlock();
                BlockFace hitFace = trace == null ? null : trace.getHitBlockFace();
                if (target == null || hitFace == null) {
                    player.sendRichMessage("<red>Privește spre un bloc, la maximum 6 blocuri distanță.</red>");
                    return;
                }
                // Anchor the pump on the face you're looking at, exactly like placing a real
                // block would -- so aiming at the top of a ground block stands the pump on top
                // of it instead of sinking its model into that block.
                Block placement = target.getRelative(hitFace);
                if (gasPumps.createPump(placement, player)) {
                    player.sendRichMessage("<green>Pompă de benzină creată la " + placement.getX()
                            + ", " + placement.getY() + ", " + placement.getZ() + ".</green>");
                } else {
                    player.sendRichMessage("<yellow>Acolo este deja o pompă de benzină.</yellow>");
                }
            }
            case "remove" -> {
                if (gasPumps.removeNearestPump(player.getLocation(), 6.0D)) {
                    player.sendRichMessage("<green>Cea mai apropiată pompă de benzină a fost eliminată.</green>");
                } else {
                    player.sendRichMessage("<red>Nu există nicio pompă de benzină la mai puțin de 6 blocuri.</red>");
                }
            }
            case "debug" -> {
                String dump = gasPumps.debugNearestPump(player.getLocation(), 6.0D);
                if (dump == null) {
                    player.sendRichMessage("<red>Nu există nicio pompă de benzină la mai puțin de 6 blocuri.</red>");
                } else {
                    for (String line : dump.split("\n")) {
                        player.sendRichMessage("<gray>" + line.replace("<", "\\<") + "</gray>");
                    }
                    plugin.getLogger().info("[pump debug]\n" + dump);
                }
            }
            default -> player.sendRichMessage(
                    "<yellow>Utilizare: /vehicle pump <create|remove|debug></yellow>");
        }
    }

    private void fuelPrice(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendRichMessage("<gold>Preț combustibil:</gold> <white>"
                    + String.format(Locale.ROOT, "%.2f", gasPumps.pricePerPercent())
                    + " per 1% din rezervor</white>"
                    + (gasPumps.economyAvailable() ? "" : " <gray>(Vault indisponibil, combustibilul e gratuit)</gray>"));
            return;
        }
        if (!sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
            return;
        }
        try {
            double value = Double.parseDouble(args[1]);
            if (value < 0.0D) {
                sender.sendRichMessage("<red>Prețul nu poate fi negativ.</red>");
                return;
            }
            gasPumps.setPricePerPercent(value);
            sender.sendRichMessage("<green>Preț combustibil setat la</green> <white>"
                    + String.format(Locale.ROOT, "%.2f", value) + "</white> <green>per 1% din rezervor.</green>");
        } catch (NumberFormatException exception) {
            sender.sendRichMessage("<red>Utilizare: /vehicle fuelprice <valoare></red>");
        }
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (!sender.hasPermission("vehicle.admin")) {
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
        if (!sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
            return;
        }
        LandVehicle vehicle = vehicles.nearest(player.getLocation(), 6.0D).orElse(null);
        PaperTrailer trailer = vehicles.trailers().nearest(player.getLocation(), 6.0D).orElse(null);
        if (vehicle == null && trailer == null) {
            player.sendRichMessage("<red>Nu există niciun vehicul sau remorcă la mai puțin de 6 blocuri.</red>");
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
        if (!sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
            return;
        }
        vehicles.nearest(player.getLocation(), 6.0D).ifPresentOrElse(vehicle -> {
            vehicle.setFuel(vehicle.spec().energyCapacity());
            vehicles.save();
            player.sendRichMessage("<green>Rezervorul vehiculului a fost umplut.</green>");
        }, () -> player.sendRichMessage("<red>Nu există niciun vehicul la mai puțin de 6 blocuri.</red>"));
    }

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
        sender.sendRichMessage("<yellow>/" + label + " list</yellow> <gray>- număr vehicule active</gray>");
        sender.sendRichMessage("<yellow>/" + label + " save</yellow> <gray>- salvează vehiculele</gray>");
        sender.sendRichMessage("<yellow>/" + label + " pump <create|remove></yellow> <gray>- "
                + "înregistrează/elimină o pompă de benzină pe blocul privit</gray>");
        sender.sendRichMessage("<yellow>/" + label + " fuelprice [valoare]</yellow> <gray>- "
                + "afișează sau setează prețul per 1% din rezervor</gray>");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("spawn", "remove", "refuel", "list", "save", "pack", "pump", "fuelprice"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            List<String> types = new ArrayList<>(LandVehicleSpec.ids());
            types.addAll(TrailerSpec.ids());
            types.sort(String::compareTo);
            return filter(types, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pump")) {
            return filter(List.of("create", "remove", "debug"), args[1]);
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
