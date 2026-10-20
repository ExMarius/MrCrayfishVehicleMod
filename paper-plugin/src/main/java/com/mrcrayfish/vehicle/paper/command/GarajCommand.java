package com.mrcrayfish.vehicle.paper.command;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * /garaj - the ONE player-facing garage command, hybrid and location-aware:
 * <ul>
 *   <li>inside his PERSONAL circle: free chat list of his stored cars ([SCOATE (Gratuit)]);</li>
 *   <li>inside a PUBLIC circle: impound list with the paid [RECUPEREAZĂ] button (Vault);</li>
 *   <li>on the street, no personal spot: hint to /garaj set;</li>
 *   <li>on the street, spot set but far away: plain error.</li>
 * </ul>
 * /garaj set and /garaj remove manage the ONE personal spot (one per owner).
 * Permission gate: OP bypasses everything, otherwise vehicle.garage (LuckPerms).
 */
public final class GarajCommand implements CommandExecutor, TabCompleter {
    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;

    public GarajCommand(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    /** OP = full bypass; everyone else needs the LuckPerms node vehicle.garage. */
    private static boolean hasGaragePermission(Player player) {
        return player.isOp() || player.hasPermission("vehicle.garage");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return true;
        }
        if (args.length == 0) {
            hybrid(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> set(player);
            case "remove", "sterge" -> remove(player);
            case "scoate" -> takeOut(player, args);
            case "unimpound", "recupereaza" -> unimpound(player, args);
            default -> player.sendRichMessage("<red>Folosire: /" + label + " [set|remove]</red>");
        }
        return true;
    }

    /** The bare /garaj: decide purely by WHERE the player stands. */
    private void hybrid(Player player) {
        // 1) Inside HIS personal circle -> the free home list.
        if (plugin.garageStations().insidePersonalSpot(player.getUniqueId(), player.getLocation())) {
            homeList(player);
            return;
        }
        // 2) Inside a PUBLIC circle -> the impound list (no personal spot required!).
        if (plugin.garageStations().publicSpotAt(player.getLocation()).isPresent()) {
            impoundList(player);
            return;
        }
        // 3) On the street: hint when he has no personal spot at all, plain error otherwise.
        if (plugin.garageStations().personalSpotOf(player.getUniqueId()).isEmpty()) {
            player.sendMessage(Component.text("[Garaj] ", NamedTextColor.GOLD)
                    .append(Component.text("Nu ai un garaj personal setat! Mergi în curtea casei "
                            + "tale și folosește: ", NamedTextColor.YELLOW))
                    .append(Component.text("/garaj set", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text(
                    "Trebuie să te afli în interiorul unui loc de parcare pentru a folosi această comandă!",
                    NamedTextColor.RED));
        }
    }

    /** /garaj set - mark the ONE personal spot under his feet (re-setting moves it). */
    private void set(Player player) {
        if (!hasGaragePermission(player)) {
            player.sendRichMessage("<red>Nu ai permisiunea vehicle.garage (cere adminului).</red>");
            return;
        }
        int removed = plugin.garageStations().removeOwnedBy(player.getUniqueId());
        plugin.garageStations().place(player, false);
        player.sendMessage(Component.text("[Garaj] ", NamedTextColor.GOLD)
                .append(Component.text("Ți-ai setat GARAJUL PERSONAL aici! ", NamedTextColor.GREEN))
                .append(Component.text("Cercul de scântei îl arată; mașina lăsată în el "
                        + "rămâne pe loc, gratuit.", NamedTextColor.GRAY)));
        if (removed > 0) {
            player.sendRichMessage("<gray>(Vechiul tău loc de parcare a fost șters automat.)</gray>");
        }
    }

    /** /garaj remove - delete the personal spot for good (moving house is free). */
    private void remove(Player player) {
        if (!hasGaragePermission(player)) {
            player.sendRichMessage("<red>Nu ai permisiunea vehicle.garage (cere adminului).</red>");
            return;
        }
        if (plugin.garageStations().removeOwnedBy(player.getUniqueId()) > 0) {
            player.sendMessage(Component.text("[Garaj] ", NamedTextColor.GOLD)
                    .append(Component.text("Ți-am șters garajul personal. "
                            + "Îl poți seta din nou oriunde cu /garaj set.", NamedTextColor.YELLOW)));
        } else {
            player.sendRichMessage("<red>Nu ai niciun garaj personal setat.</red>");
        }
    }

    /** Home list: everything under garage.* that is MINE and not seized = free button. */
    private void homeList(Player player) {
        List<VehicleManager.GarageEntry> stored = vehicles.garagedOf(player);
        if (stored.isEmpty()) {
            player.sendMessage(Component.text("[Garaj Personal] ", NamedTextColor.GOLD)
                    .append(Component.text("Nu ai nicio mașină salvată în garaj.", NamedTextColor.YELLOW)));
            return;
        }
        player.sendMessage(Component.text("┌── GARAJUL TĂU PERSONAL ──", NamedTextColor.GOLD));
        for (VehicleManager.GarageEntry entry : stored) {
            LandVehicleSpec spec = LandVehicleSpec.byId(entry.type());
            if (spec == null) {
                continue;
            }
            if (entry.impounded()) {
                // Seized cars NEVER leave for free - public spot only (sechestrul rămâne regula).
                player.sendMessage(Component.text("[Sechestru] ", NamedTextColor.RED)
                        .append(Component.text(spec.displayName() + " - ", NamedTextColor.YELLOW))
                        .append(Component.text("recuperabil DOAR dintr-o parcare PUBLICĂ",
                                NamedTextColor.RED)));
            } else {
                player.sendMessage(Component.text("[Garaj Personal] ", NamedTextColor.GOLD)
                        .append(Component.text(spec.displayName() + " - ", NamedTextColor.YELLOW))
                        .append(Component.text("[SCOATE (Gratuit)]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/garaj scoate " + entry.id()))
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        "Click = mașina apare AICI, fără nicio taxă")))));
            }
        }
    }

    /** Public list: my seized cars with the paid recovery button. */
    private void impoundList(Player player) {
        List<VehicleManager.GarageEntry> seized = vehicles.impoundedOf(player.getUniqueId());
        if (seized.isEmpty()) {
            player.sendMessage(Component.text("[Sechestru] ", NamedTextColor.GOLD)
                    .append(Component.text("Nu ai nicio mașină în sechestru.", NamedTextColor.GREEN)));
            return;
        }
        double fee = vehicles.impoundFee();
        player.sendMessage(Component.text("┌── MAȘINILE TALE SECHESTRATE ──", NamedTextColor.GOLD));
        for (VehicleManager.GarageEntry entry : seized) {
            LandVehicleSpec spec = LandVehicleSpec.byId(entry.type());
            if (spec == null) {
                continue;
            }
            player.sendMessage(Component.text("[Sechestru] ", NamedTextColor.GOLD)
                    .append(Component.text(spec.displayName() + " - ", NamedTextColor.YELLOW))
                    .append(Component.text("Taxă: " + fee + "$ ", NamedTextColor.RED))
                    .append(Component.text("[RECUPEREAZĂ]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/garaj unimpound " + entry.id()))
                            .hoverEvent(HoverEvent.showText(Component.text(
                                    "Click = plătești " + fee + "$ și mașina apare AICI")))));
        }
    }

    /** /garaj scoate <id> - the [SCOATE (Gratuit)] click: free, but ONLY from home. */
    private void takeOut(Player player, String[] args) {
        if (args.length < 2) {
            player.sendRichMessage("<red>Comanda vine din butonul [SCOATE (Gratuit)].</red>");
            return;
        }
        if (!plugin.garageStations().insidePersonalSpot(player.getUniqueId(), player.getLocation())) {
            player.sendMessage(Component.text(
                    "Mașinile din garajul personal se scot DOAR stând în cercul tău de acasă!",
                    NamedTextColor.RED));
            return;
        }
        java.util.UUID vehicleId;
        try {
            vehicleId = java.util.UUID.fromString(args[1]);
        } catch (IllegalArgumentException badId) {
            player.sendRichMessage("<red>ID de vehicul invalid.</red>");
            return;
        }
        if (vehicles.isImpounded(vehicleId)) {
            player.sendMessage(Component.text(
                    "Mașina e SECHESTRATĂ - o recuperezi doar dintr-o parcare PUBLICĂ, contra taxei!",
                    NamedTextColor.RED));
            return;
        }
        plugin.garageStations().personalSpotOf(player.getUniqueId())
                .flatMap(id -> plugin.garageStations().location(id))
                .ifPresentOrElse(spot -> {
                    if (vehicles.retrieve(player, vehicleId, spot)) {
                        player.sendMessage(Component.text("[Garaj Personal] ", NamedTextColor.GOLD)
                                .append(Component.text("Mașina e afară - GRATUIT, 0$. "
                                        + "Drum bun!", NamedTextColor.GREEN)));
                    } else {
                        player.sendRichMessage("<red>Nu am putut scoate mașina din garaj.</red>");
                    }
                }, () -> player.sendRichMessage("<red>Nu ai un garaj personal setat.</red>"));
    }

    /** /garaj unimpound <id> - the [RECUPEREAZĂ] click: Vault check, charge, spawn in circle. */
    private void unimpound(Player player, String[] args) {
        if (args.length < 2) {
            player.sendRichMessage("<red>Comanda vine din butonul [RECUPEREAZĂ].</red>");
            return;
        }
        java.util.UUID spotId = plugin.garageStations().publicSpotAt(player.getLocation()).orElse(null);
        if (spotId == null) {
            player.sendMessage(Component.text(
                    "Poți recupera DOAR stând într-o parcare PUBLICĂ (cercul de scântei)!",
                    NamedTextColor.RED));
            return;
        }
        java.util.UUID vehicleId;
        try {
            vehicleId = java.util.UUID.fromString(args[1]);
        } catch (IllegalArgumentException badId) {
            player.sendRichMessage("<red>ID de vehicul invalid.</red>");
            return;
        }
        if (!vehicles.isImpounded(vehicleId)) {
            player.sendRichMessage("<red>Mașina aceasta nu e în sechestru (sau nu e a ta).</red>");
            return;
        }
        if (!com.mrcrayfish.vehicle.paper.shop.ShopEconomy.ready()) {
            player.sendRichMessage("<red>Economia nu este activă acum - contactează un admin.</red>");
            return;
        }
        double fee = vehicles.impoundFee();
        if (com.mrcrayfish.vehicle.paper.shop.ShopEconomy.balance(player) < fee) {
            player.sendMessage(Component.text(
                    "Nu ai suficienți bani pentru a plăti taxa de sechestru! (" + fee + "$)",
                    NamedTextColor.RED));
            return;
        }
        Location spot = plugin.garageStations().location(spotId).orElse(player.getLocation());
        if (!vehicles.retrieve(player, vehicleId, spot)) {
            player.sendRichMessage("<red>Nu am putut scoate mașina din sechestru.</red>");
            return;
        }
        com.mrcrayfish.vehicle.paper.shop.ShopEconomy.withdraw(player, fee);
        vehicles.clearImpounded(vehicleId);
        player.sendMessage(Component.text("[Sechestru] ", NamedTextColor.GOLD)
                .append(Component.text("Ai recuperat mașina pentru ", NamedTextColor.GREEN))
                .append(Component.text(fee + "$", NamedTextColor.GOLD))
                .append(Component.text(" - e aici, în parcare.", NamedTextColor.GREEN)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("set", "remove").stream().filter(word -> word.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
