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
 * /garaj - ONE global garage per player (everything lives under garage.<uuid>.*) with
 * LOCATION-BASED pricing:
 * <ul>
 *   <li>inside his PERSONAL circle: the global list with [SCOATE VEHICUL (Gratuit)] - 0$;</li>
 *   <li>inside a PUBLIC circle: THE SAME list with [RECUPEREAZĂ (Taxă: X$)] - paid via Vault;</li>
 *   <li>on the street, no personal spot: hint to /garaj set;</li>
 *   <li>on the street, spot set but far away: plain error.</li>
 * </ul>
 * /garaj set | /garaj remove : the player's ONE private spot (perm vehicle.garage; OP bypass).
 * /garaj set public           : admin-only public parking (OP or vehicle.admin) - hidden
 *                               from tab-complete for regular players.
 */
public final class GarajCommand implements CommandExecutor, TabCompleter {
    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;

    public GarajCommand(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    /** OP = full bypass everywhere; regular players need the LuckPerms node. */
    private static boolean isAdmin(Player player) {
        return player.isOp() || player.hasPermission("vehicle.admin");
    }

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
            case "set" -> set(player, args);
            case "remove", "sterge" -> remove(player, args);
            case "scoate" -> takeOut(player, args);
            case "recupereaza" -> recover(player, args);
            default -> player.sendRichMessage("<red>Folosire: /" + label + " [set|remove]</red>");
        }
        return true;
    }

    /** Bare /garaj: decide purely by WHERE the player stands. */
    private void hybrid(Player player) {
        // 1) Inside HIS personal circle -> the free variant of the global list.
        if (plugin.garageStations().insidePersonalSpot(player.getUniqueId(), player.getLocation())) {
            showList(player, false);
            return;
        }
        // 2) Inside a PUBLIC circle -> THE SAME global list, but each pull-out costs the fee.
        if (plugin.garageStations().publicSpotAt(player.getLocation()).isPresent()) {
            showList(player, true);
            return;
        }
        // 3) On the street: hint when he never set a spot, plain error otherwise.
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

    /** The ONE global list, rendered free (home) or paid (public) depending on the circle. */
    private void showList(Player player, boolean publicSpot) {
        List<VehicleManager.GarageEntry> stored = vehicles.garagedOf(player);
        if (stored.isEmpty()) {
            player.sendMessage(Component.text("[Garaj] ", NamedTextColor.GOLD)
                    .append(Component.text("Nu ai nicio mașină în garaj.", NamedTextColor.YELLOW)));
            return;
        }
        double fee = vehicles.impoundFee();
        player.sendMessage(Component.text(publicSpot
                        ? "┌── GARAJ PUBLIC (oraș) ──" : "┌── GARAJUL TĂU PERSONAL ──",
                NamedTextColor.GOLD));
        for (VehicleManager.GarageEntry entry : stored) {
            LandVehicleSpec spec = LandVehicleSpec.byId(entry.type());
            if (spec == null) {
                continue;
            }
            if (publicSpot) {
                player.sendMessage(Component.text("[Garaj] ", NamedTextColor.GOLD)
                        .append(Component.text(spec.displayName() + " - ", NamedTextColor.YELLOW))
                        .append(Component.text("[RECUPEREAZĂ (Taxă: " + fee + "$)]",
                                        NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/garaj recupereaza " + entry.id()))
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        "Click = plătești " + fee + "$ și mașina apare AICI")))));
            } else {
                player.sendMessage(Component.text("[Garaj Personal] ", NamedTextColor.GOLD)
                        .append(Component.text(spec.displayName() + " - ", NamedTextColor.YELLOW))
                        .append(Component.text("[SCOATE VEHICUL (Gratuit)]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/garaj scoate " + entry.id()))
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        "Click = mașina apare AICI, 0$ (acasă e mereu gratuit)")))));
            }
        }
    }

    /** /garaj set [/ garaj set public] - private spot for players, public spot for admins. */
    private void set(Player player, String[] args) {
        boolean wantsPublic = args.length >= 2 && args[1].equalsIgnoreCase("public");
        if (wantsPublic) {
            // ADMIN ONLY: OP or vehicle.admin; regular players never even tab-see this.
            if (!isAdmin(player)) {
                player.sendRichMessage("<red>Parcările PUBLICE se creează doar de admini (OP).</red>");
                return;
            }
            plugin.garageStations().place(player, true);
            player.sendMessage(Component.text("[Garaj] ", NamedTextColor.GOLD)
                    .append(Component.text("Parcare PUBLICĂ creată aici. ", NamedTextColor.GREEN))
                    .append(Component.text("Cercul de scântei o marchează; scoaterea unei mașini "
                            + "de aici costă " + vehicles.impoundFee() + "$.", NamedTextColor.GRAY)));
            return;
        }
        if (!hasGaragePermission(player)) {
            player.sendRichMessage("<red>Nu ai permisiunea vehicle.garage (cere adminului).</red>");
            return;
        }
        int removed = plugin.garageStations().removeOwnedBy(player.getUniqueId());
        plugin.garageStations().place(player, false);
        player.sendMessage(Component.text("[Garaj] ", NamedTextColor.GOLD)
                .append(Component.text("Ți-ai setat GARAJUL PERSONAL aici! ", NamedTextColor.GREEN))
                .append(Component.text("Cercul de scântei îl arată; scoaterea de acasă e "
                        + "mereu GRATUITĂ.", NamedTextColor.GRAY)));
        if (removed > 0) {
            player.sendRichMessage("<gray>(Vechiul tău loc de parcare a fost șters automat.)</gray>");
        }
    }

    /** /garaj remove | /garaj remove public - personal spot; public spots are admin-only. */
    private void remove(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("public")) {
            // ADMIN ONLY (OP sau vehicle.admin) - exact ca la /garaj set public.
            if (!isAdmin(player)) {
                player.sendRichMessage("<red>Parc\u0103rile PUBLICE se \u0219terg doar de admini (OP).</red>");
                return;
            }
            if (plugin.garageStations().removePublicAt(player.getLocation())) {
                player.sendMessage(Component.text("[Garaj] ", NamedTextColor.GOLD)
                        .append(Component.text("Parcarea PUBLIC\u0102 de aici a fost \u0219tears\u0103 "
                                + "\u0219i cercul ei de sc\u00e2ntei a disp\u0103rut.", NamedTextColor.YELLOW)));
            } else {
                player.sendMessage(Component.text(
                        "Nu stai \u00eentr-o parcare PUBLIC\u0102 (stai \u00een cercul de sc\u00e2ntei \u0219i reia).",
                        NamedTextColor.RED));
            }
            return;
        }
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

    /** /garaj scoate <id> - [SCOATE VEHICUL (Gratuit)]: free, but ONLY from the home circle. */
    private void takeOut(Player player, String[] args) {
        if (args.length < 2) {
            player.sendRichMessage("<red>Comanda vine din butonul [SCOATE VEHICUL (Gratuit)].</red>");
            return;
        }
        if (!plugin.garageStations().insidePersonalSpot(player.getUniqueId(), player.getLocation())) {
            player.sendMessage(Component.text(
                    "Scoaterea GRATUITĂ merge DOAR stând în cercul tău personal de acasă!",
                    NamedTextColor.RED));
            return;
        }
        java.util.UUID vehicleId = parseId(player, args[1]);
        if (vehicleId == null) {
            return;
        }
        plugin.garageStations().personalSpotOf(player.getUniqueId())
                .flatMap(id -> plugin.garageStations().location(id))
                .ifPresentOrElse(spot -> {
                    if (vehicles.retrieve(player, vehicleId, spot)) {
                        player.sendMessage(Component.text("[Garaj Personal] ", NamedTextColor.GOLD)
                                .append(Component.text("Mașina e afară - GRATUIT, 0$. Drum bun!",
                                        NamedTextColor.GREEN)));
                    } else {
                        player.sendRichMessage("<red>Mașina aceasta nu e a ta "
                                + "(sau nu e în garaj).</red>");
                    }
                }, () -> player.sendRichMessage("<red>Nu ai un garaj personal setat.</red>"));
    }

    /** /garaj recupereaza <id> - [RECUPEREAZĂ (Taxă)]: Vault charge, spawn in the public circle. */
    private void recover(Player player, String[] args) {
        if (args.length < 2) {
            player.sendRichMessage("<red>Comanda vine din butonul [RECUPEREAZĂ].</red>");
            return;
        }
        java.util.UUID spotId = plugin.garageStations().publicSpotAt(player.getLocation()).orElse(null);
        if (spotId == null) {
            player.sendMessage(Component.text(
                    "Recuperarea cu plată merge DOAR stând într-o parcare PUBLICĂ (cercul de scântei)!",
                    NamedTextColor.RED));
            return;
        }
        java.util.UUID vehicleId = parseId(player, args[1]);
        if (vehicleId == null) {
            return;
        }
        if (!com.mrcrayfish.vehicle.paper.shop.ShopEconomy.ready()) {
            player.sendRichMessage("<red>Economia nu este activă acum - contactează un admin.</red>");
            return;
        }
        double fee = vehicles.impoundFee();
        if (com.mrcrayfish.vehicle.paper.shop.ShopEconomy.balance(player) < fee) {
            player.sendMessage(Component.text(
                    "Nu ai suficienți bani pentru taxa de recuperare! (" + fee + "$)",
                    NamedTextColor.RED));
            return;
        }
        Location spot = plugin.garageStations().location(spotId).orElse(player.getLocation());
        // Audit Y: incasare STRICT pe livrare reusita. Se plateste intai, iar daca
        // livrarea esueaza banii se returneaza (nu ramane nici sperietura, nici gratisul).
        if (!com.mrcrayfish.vehicle.paper.shop.ShopEconomy.withdraw(player, fee)) {
            player.sendRichMessage("<red>Plata taxei a esuat - incearca din nou.</red>");
            return;
        }
        if (!vehicles.retrieve(player, vehicleId, spot)) {
            com.mrcrayfish.vehicle.paper.shop.ShopEconomy.deposit(player, fee);
            player.sendRichMessage("<red>Mașina aceasta nu e a ta (sau nu e în garaj). "
                    + "Taxa ti-a fost returnata.</red>");
            return;
        }
        player.sendMessage(Component.text("[Garaj] ", NamedTextColor.GOLD)
                .append(Component.text("Ai recuperat mașina pentru ", NamedTextColor.GREEN))
                .append(Component.text(fee + "$", NamedTextColor.GOLD))
                .append(Component.text(" - e aici, în parcare.", NamedTextColor.GREEN)));
    }

    private static java.util.UUID parseId(Player player, String raw) {
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException badId) {
            player.sendRichMessage("<red>ID de vehicul invalid.</red>");
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("set", "remove").stream().filter(word -> word.startsWith(prefix)).toList();
        }
        // "public" appears ONLY for OP / vehicle.admin - regular players never see it.
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove"))
                && sender instanceof Player player && isAdmin(player)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("public").stream().filter(word -> word.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
