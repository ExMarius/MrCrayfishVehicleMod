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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

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
            case "parking", "parcare" -> parking(sender, args);
            case "mygarage" -> mygarage(sender, args);
            case "impound", "sechestru" -> impound(sender);
            case "unimpound", "recupereaza" -> unimpound(sender, args);
            case "trust" -> trust(sender, args);
            case "untrust" -> untrust(sender, args);
            case "trusted" -> trusted(sender);
            case "magazin", "shop", "dealer" -> {
                if (args.length >= 2 && "podium".equalsIgnoreCase(args[1])) {
                    dealerPodium(sender, args);
                } else {
                    dealer(sender);
                }
            }
            case "list" -> sender.sendRichMessage("<gold>Vehicule:</gold> <white>"
                    + vehicles.vehicles().size() + " active</white><gray>, "
                    + vehicles.pendingVehicleCount() + " inactive/în așteptare, "
                    + vehicles.trailers().trailers().size() + " remorci, "
                    + gasPumps.pumps().size() + " pompe de benzină</gray>");
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

    /** FiveM garages: opens the window; /vehicle garage station gives the physical terminal. */
    private void garage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        sender.sendRichMessage("<gold>Parcări fizice (fără meniuri):</gold>");
        sender.sendRichMessage("<yellow>/" + "vehicle parking set public</yellow> <gray>- parcare PUBLICĂ aici (admin)</gray>");
        sender.sendRichMessage("<yellow>/" + "vehicle mygarage set</yellow> <gray>- parcarea TA personală aici</gray>");
        sender.sendRichMessage("<yellow>/" + "vehicle impound</yellow> <gray>- sechestru, dintr-un cerc PUBLIC</gray>");
        sender.sendRichMessage("<gray>Condu în cercul cu scântei și coboară = mașina rămâne parcată pe loc.</gray>");
    }

    /** /vehicle parking set <public|private> - admin marks the block under his feet. */
    private void parking(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (!sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Locurile de parcare se creează doar de admini (OP).</red>");
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("set")
                || !(args[2].equalsIgnoreCase("public") || args[2].equalsIgnoreCase("private"))) {
            sender.sendRichMessage("<red>Folosire: /vehicle parking set <public|private></red>");
            return;
        }
        boolean publicSpot = args[2].equalsIgnoreCase("public");
        plugin.garageStations().place(player, publicSpot);
        if (publicSpot) {
            player.sendRichMessage("<green>Parcare PUBLICĂ creată aici.</green> "
                    + "<gray>Cercul de scântei (r=5) o marchează: oricine poate parca legal.</gray>");
        } else {
            player.sendRichMessage("<light_purple>Parcare PRIVATĂ creată aici (proprietar: tu).</light_purple> "
                    + "<gray>Doar mașinile tale parchează legal în acest cerc.</gray>");
        }
    }

    /** /vehicle mygarage set - the player's ONE personal spot; re-setting moves it. */
    private void mygarage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (!hasGaragePermission(player)) {
            sender.sendRichMessage("<red>Nu ai permisiunea vehicle.garage (cere adminului).</red>");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("set")) {
            sender.sendRichMessage("<red>Folosire: /vehicle mygarage set</red>");
            return;
        }
        int removed = plugin.garageStations().removeOwnedBy(player.getUniqueId());
        plugin.garageStations().place(player, false);
        player.sendRichMessage("<light_purple>Ai marcat PARCAREA TA PERSONALĂ aici.</light_purple> "
                + "<gray>Cercul de scântei o arată; mașina lăsată în el rămâne pe loc, gratuit.</gray>");
        if (removed > 0) {
            player.sendRichMessage("<gray>(Vechiul tău loc de parcare a fost șters automat.)</gray>");
        }
    }

    /** /vehicle impound - ONLY from inside a PUBLIC circle: chat list with a clickable button. */
    private void impound(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (plugin.garageStations().publicSpotAt(player.getLocation()).isEmpty()) {
            player.sendRichMessage("<red>Trebuie să stai într-o parcare PUBLICĂ "
                    + "(cercul de scântei) ca să recuperezi din sechestru.</red>");
            return;
        }
        List<VehicleManager.GarageEntry> seized = vehicles.impoundedOf(player.getUniqueId());
        if (seized.isEmpty()) {
            player.sendRichMessage("<green>Nu ai nicio mașină în sechestru.</green>");
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
                    .append(Component.text("Mașină: " + spec.displayName() + " - ",
                            NamedTextColor.YELLOW))
                    .append(Component.text("Taxă: " + fee + "$ ", NamedTextColor.RED))
                    .append(Component.text("[RECUPEREAZĂ]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/vehicle unimpound " + entry.id()))
                            .hoverEvent(HoverEvent.showText(Component.text(
                                    "Click = plătești " + fee + "$ și mașina apare AICI")))));
        }
    }

    /** /vehicle unimpound <uuid> - the [RECUPEREAZĂ] click target: Vault check, charge, spawn. */
    private void unimpound(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executată de un jucător.</red>");
            return;
        }
        if (args.length < 2) {
            player.sendRichMessage("<red>Folosire: /vehicle unimpound <id></red>");
            return;
        }
        java.util.UUID spotId = plugin.garageStations().publicSpotAt(player.getLocation()).orElse(null);
        if (spotId == null) {
            player.sendRichMessage("<red>Poți recupera DOAR stând într-o parcare PUBLICĂ "
                    + "(cercul de scântei).</red>");
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
            player.sendRichMessage("<red>Nu ai suficienți bani pentru a plăti taxa de sechestru! ("
                    + fee + "$)</red>");
            return;
        }
        Location spot = plugin.garageStations().location(spotId).orElse(player.getLocation());
        if (!vehicles.retrieve(player, vehicleId, spot)) {
            player.sendRichMessage("<red>Nu am putut scoate mașina din sechestru.</red>");
            return;
        }
        com.mrcrayfish.vehicle.paper.shop.ShopEconomy.withdraw(player, fee);
        vehicles.clearImpounded(vehicleId);
        player.sendRichMessage("<green>Ai recuperat mașina din SECHESTRU pentru <gold>"
                + fee + "$</gold> - e aici, în parcare.</green>");
    }

    /** /vehicle magazin - the FiveM dealer (podium / catalog). */
    private void dealer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executata de un jucator.</red>");
            return;
        }
        plugin.shopGui().open(player);
    }

    /** /vehicle magazin podium - admin PRIVIND locul construieste showroomul DIRECT acolo. */
    private void dealerPodium(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Comanda trebuie executata de un jucator.</red>");
            return;
        }
        if (!sender.hasPermission("vehicle.admin")) {
            sender.sendRichMessage("<red>Doar administratorii pot construi un showroom.</red>");
            return;
        }
        if (args.length < 3) {
            sender.sendRichMessage("<yellow>Utilizare: /vehicle magazin podium <vehicul>"
                    + " (ex: sports_car, atv, mini_bus).</yellow>");
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
        plugin.dealerStations().place(player, foot, args[2]);
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
    private static boolean hasGaragePermission(Player player) {
        if (player.hasPermission("vehicle.garage")) {
            return true;
        }
        return Bukkit.getPluginManager().getPlugin("LuckPerms") == null && player.isOp();
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
        sender.sendRichMessage("<yellow>/" + label + " gaspump</yellow> <gray>- primești o pompă de benzină plasabilă</gray>");
        sender.sendRichMessage("<yellow>/" + label + " list</yellow> <gray>- număr vehicule active</gray>");
        sender.sendRichMessage("<yellow>/" + label + " save</yellow> <gray>- salvează vehiculele</gray>");
        sender.sendRichMessage("<yellow>/" + label + " parking set <public|private></yellow> <gray>- parcare fizică aici (admin)</gray>");
        sender.sendRichMessage("<yellow>/" + label + " mygarage set</yellow> <gray>- parcarea ta personală aici</gray>");
        sender.sendRichMessage("<yellow>/" + label + " impound</yellow> <gray>- sechestru: listă + recuperare, dintr-un cerc public</gray>");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("spawn", "remove", "refuel", "gaspump", "jerrycan", "parking", "mygarage", "impound", "trust", "untrust", "trusted", "magazin", "list", "save", "pack"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("magazin") || args[0].equalsIgnoreCase("shop")
                || args[0].equalsIgnoreCase("dealer"))) {
            return filter(java.util.List.of("podium"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("magazin") || args[0].equalsIgnoreCase("shop")
                || args[0].equalsIgnoreCase("dealer")) && "podium".equalsIgnoreCase(args[1])) {
            return filter(new ArrayList<>(LandVehicleSpec.ids()), args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return filter(onTabPlayers(), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("parking") || args[0].equalsIgnoreCase("parcare"))) {
            return filter(List.of("set"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("parking") || args[0].equalsIgnoreCase("parcare"))
                && "set".equalsIgnoreCase(args[1])) {
            return filter(List.of("public", "private"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mygarage")) {
            return filter(List.of("set"), args[1]);
        }
        if (false) {
            return filter(List.of("station", "personal"), args[1]);
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
