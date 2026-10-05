package com.mrcrayfish.vehicle.paper.command;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.GoKart;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VehicleCommand implements CommandExecutor, TabCompleter {
    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;

    public VehicleCommand(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
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
            case "list" -> sender.sendRichMessage("<gold>Vehicule active:</gold> <white>" + vehicles.vehicles().size() + "</white>");
            case "save" -> {
                if (!sender.hasPermission("vehicle.admin")) {
                    sender.sendRichMessage("<red>Nu ai permisiunea vehicle.admin.</red>");
                    return true;
                }
                vehicles.save();
                sender.sendRichMessage("<green>Vehicule salvate.</green>");
            }
            case "pack" -> pack(sender);
            default -> help(sender, label);
        }
        return true;
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
        if (args.length < 2 || !args[1].equalsIgnoreCase("go_kart")) {
            sender.sendRichMessage("<yellow>Utilizare: /vehicle spawn go_kart</yellow>");
            return;
        }
        GoKart goKart = vehicles.spawn(player.getLocation());
        vehicles.save();
        player.sendRichMessage("<green>Go Kart creat.</green> <gray>ID: " + goKart.id() + "</gray>");
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
        vehicles.nearest(player.getLocation(), 6.0D).ifPresentOrElse(vehicle -> {
            vehicles.remove(vehicle);
            player.sendRichMessage("<green>Cel mai apropiat vehicul a fost eliminat.</green>");
        }, () -> player.sendRichMessage("<red>Nu există niciun vehicul la mai puțin de 6 blocuri.</red>"));
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
            vehicle.setFuel(GoKart.ENERGY_CAPACITY);
            vehicles.save();
            player.sendRichMessage("<green>Rezervorul vehiculului a fost umplut.</green>");
        }, () -> player.sendRichMessage("<red>Nu există niciun vehicul la mai puțin de 6 blocuri.</red>"));
    }

    private void pack(CommandSender sender) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url.isBlank()) {
            sender.sendRichMessage("<yellow>Resource pack-ul nu are încă un URL configurat.</yellow>");
        } else {
            sender.sendRichMessage("<green>Resource pack configurat:</green> <gray>" + url + "</gray>");
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendRichMessage("<gold>Vehicle Plugin</gold> <gray>prototip Go Kart</gray>");
        sender.sendRichMessage("<yellow>/" + label + " spawn go_kart</yellow> <gray>- creează un Go Kart</gray>");
        sender.sendRichMessage("<yellow>/" + label + " remove</yellow> <gray>- elimină vehiculul apropiat</gray>");
        sender.sendRichMessage("<yellow>/" + label + " refuel</yellow> <gray>- umple rezervorul vehiculului apropiat</gray>");
        sender.sendRichMessage("<yellow>/" + label + " list</yellow> <gray>- număr vehicule active</gray>");
        sender.sendRichMessage("<yellow>/" + label + " save</yellow> <gray>- salvează vehiculele</gray>");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("spawn", "remove", "refuel", "list", "save", "pack"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return filter(List.of("go_kart"), args[1]);
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
