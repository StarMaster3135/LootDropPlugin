package com.starmaster.lootdrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;

import java.util.*;
import java.util.stream.Collectors;

public class LootDropPlugin extends JavaPlugin implements Listener, TabCompleter {

    private final Map<UUID, LootDropEntity> activeLootDrops = new HashMap<>();

    private double maxHealth;
    private String displayName;
    private int particleHeight;
    private int fallHeight;
    private int fallDurationTicks;
    private boolean useCustomHitSound;
    private boolean broadcastCoordinates;
    private String spawnMessage;
    private String spawnMessageNoCoords;
    private int entityDespawnSeconds;
    private double itemDropRadius;

    private boolean autoSpawnEnabled;
    private int autoSpawnIntervalSeconds;
    private int autoSpawnRadius;
    private int autoSpawnMinPlayers;
    private List<String> autoSpawnWorlds;
    private BukkitTask autoSpawnTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        if (getConfig().getBoolean("metrics.enabled", true)) {
            int pluginId = 33512;
            new Metrics(this, pluginId);
            getLogger().info("bStats metrics enabled. You can disable this in config.yml (metrics.enabled).");
        } else {
            getLogger().info("bStats metrics disabled via config.yml.");
        }

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("lootdrop").setTabCompleter(this);
        getLogger().info("LootDrop Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (autoSpawnTask != null) {
            autoSpawnTask.cancel();
            autoSpawnTask = null;
        }

        for (LootDropEntity lootDrop : activeLootDrops.values()) {
            lootDrop.remove();
        }
        activeLootDrops.clear();
        getLogger().info("LootDrop Plugin has been disabled!");
    }

    private void loadConfig() {
        reloadConfig();
        maxHealth = getConfig().getDouble("loot-drop.max-health", 500.0);
        displayName = getConfig().getString("loot-drop.display-name", "&6&lLOOT DROP");
        particleHeight = getConfig().getInt("loot-drop.particle-height", 50);

        fallHeight = getConfig().getInt("loot-drop.fall-animation.height", 20);
        fallDurationTicks = getConfig().getInt("loot-drop.fall-animation.duration-ticks", 60);

        useCustomHitSound = getConfig().getBoolean("loot-drop.use-custom-hit-sound", true);

        broadcastCoordinates = getConfig().getBoolean("loot-drop.broadcast-coordinates", true);
        spawnMessage = getConfig().getString("loot-drop.spawn-message",
            "&6&l[LOOT DROP] &eA loot drop is incoming! &7[%x%, %y%, %z% &7in &f%world%&7]");
        spawnMessageNoCoords = getConfig().getString("loot-drop.spawn-message-no-coords",
            "&6&l[LOOT DROP] &eA loot drop is incoming! &eFind the landing site!");

        entityDespawnSeconds = getConfig().getInt("loot-drop.entity-despawn-seconds", 300);
        itemDropRadius = getConfig().getDouble("loot-drop.item-drop-radius", 3.0);

        autoSpawnEnabled = getConfig().getBoolean("auto-spawn.enabled", true);
        autoSpawnIntervalSeconds = getConfig().getInt("auto-spawn.spawn-interval-seconds", 1800);
        autoSpawnRadius = getConfig().getInt("auto-spawn.spawn-radius", 500);
        autoSpawnMinPlayers = getConfig().getInt("auto-spawn.min-players-online", 1);
        autoSpawnWorlds = getConfig().getStringList("auto-spawn.worlds");

        getLogger().info("Loaded base configuration for loot drop.");

        startAutoSpawnTask();
    }

    private void startAutoSpawnTask() {
        if (autoSpawnTask != null) {
            autoSpawnTask.cancel();
            autoSpawnTask = null;
        }

        if (!autoSpawnEnabled) {
            getLogger().info("Auto-spawn is disabled.");
            return;
        }

        long intervalTicks = Math.max(20L, autoSpawnIntervalSeconds * 20L);

        autoSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                attemptAutoSpawn();
            }
        }.runTaskTimer(this, intervalTicks, intervalTicks);

        getLogger().info("Auto-spawn enabled: every " + autoSpawnIntervalSeconds + "s, radius " +
            autoSpawnRadius + ", min players " + autoSpawnMinPlayers);
    }

    private void attemptAutoSpawn() {
        if (Bukkit.getOnlinePlayers().size() < autoSpawnMinPlayers) {
            return;
        }

        List<org.bukkit.World> candidateWorlds = new ArrayList<>();
        if (autoSpawnWorlds != null && !autoSpawnWorlds.isEmpty()) {
            for (String worldName : autoSpawnWorlds) {
                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    candidateWorlds.add(world);
                } else {
                    getLogger().warning("Auto-spawn: configured world not found: " + worldName);
                }
            }
        } else {
            candidateWorlds.addAll(Bukkit.getWorlds());
        }

        if (candidateWorlds.isEmpty()) {
            getLogger().warning("Auto-spawn: no valid worlds available to spawn in.");
            return;
        }

        org.bukkit.World world = candidateWorlds.get(new Random().nextInt(candidateWorlds.size()));
        Location center = world.getSpawnLocation();

        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * autoSpawnRadius;

        int x = (int) Math.round(center.getX() + Math.cos(angle) * distance);
        int z = (int) Math.round(center.getZ() + Math.sin(angle) * distance);
        int y = world.getHighestBlockYAt(x, z) + 1;

        Location target = new Location(world, x + 0.5, y, z + 0.5);

        spawnLootDrop(target);
        getLogger().info("Auto-spawned a loot drop at " + x + ", " + y + ", " + z + " in world " + world.getName());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("lootdrop")) {
            if (!sender.hasPermission("lootdrop.admin")) {
                sender.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage("§e§lLootDrop Commands:");
                sender.sendMessage("§7/lootdrop spawn <x> <y> <z> [world] - Spawn a loot drop");
                sender.sendMessage("§7/lootdrop reload - Reload configuration");
                sender.sendMessage("§7/lootdrop list - List active loot drops");
                sender.sendMessage("§7/lootdrop auto <on|off|status> - Control automatic spawning");
                return true;
            }

            if (args[0].equalsIgnoreCase("spawn")) {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /lootdrop spawn <x> <y> <z> [world]");
                    return true;
                }

                try {
                    double x = Double.parseDouble(args[1]);
                    double y = Double.parseDouble(args[2]);
                    double z = Double.parseDouble(args[3]);

                    String worldName = args.length >= 5 ? args[4] :
                        (sender instanceof Player ? ((Player) sender).getWorld().getName() : "world");

                    org.bukkit.World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        sender.sendMessage("§cWorld not found: " + worldName);
                        return true;
                    }

                    Location location = new Location(world, x, y, z);
                    spawnLootDrop(location);
                    sender.sendMessage("§aLoot drop initiated descent at " + x + ", " + y + ", " + z + " in world " + worldName);

                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid coordinates!");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                loadConfig();
                sender.sendMessage("§aConfiguration reloaded!");
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                if (activeLootDrops.isEmpty()) {
                    sender.sendMessage("§eNo active loot drops.");
                } else {
                    sender.sendMessage("§e§lActive Loot Drops (" + activeLootDrops.size() + "):");
                    for (LootDropEntity lootDrop : activeLootDrops.values()) {
                        Location loc = lootDrop.getLocation();
                        sender.sendMessage("§7- World: " + loc.getWorld().getName() +
                            " | X: " + (int)loc.getX() + " Y: " + (int)loc.getY() + " Z: " + (int)loc.getZ() +
                            " | HP: " + (int)lootDrop.getHealth() + "/" + (int)maxHealth);
                    }
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("auto")) {
                if (args.length < 2) {
                    sender.sendMessage("§eAuto-spawn is currently " + (autoSpawnEnabled ? "§aENABLED" : "§cDISABLED") +
                        "§e. Interval: " + autoSpawnIntervalSeconds + "s | Radius: " + autoSpawnRadius +
                        " | Min players: " + autoSpawnMinPlayers);
                    sender.sendMessage("§7Usage: /lootdrop auto <on|off|status>");
                    return true;
                }

                if (args[1].equalsIgnoreCase("status")) {
                    sender.sendMessage("§eAuto-spawn is currently " + (autoSpawnEnabled ? "§aENABLED" : "§cDISABLED"));
                    return true;
                }

                if (args[1].equalsIgnoreCase("on")) {
                    autoSpawnEnabled = true;
                    startAutoSpawnTask();
                    sender.sendMessage("§aAuto-spawn enabled (until next reload of config.yml, unless you also update the file).");
                    return true;
                }

                if (args[1].equalsIgnoreCase("off")) {
                    autoSpawnEnabled = false;
                    startAutoSpawnTask();
                    sender.sendMessage("§cAuto-spawn disabled (until next reload of config.yml, unless you also update the file).");
                    return true;
                }

                sender.sendMessage("§cUsage: /lootdrop auto <on|off|status>");
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("lootdrop")) return null;
        if (!sender.hasPermission("lootdrop.admin")) return new ArrayList<>();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("spawn");
            completions.add("reload");
            completions.add("list");
            completions.add("auto");

            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args[0].equalsIgnoreCase("auto") && args.length == 2) {
            List<String> autoCompletions = Arrays.asList("on", "off", "status");
            return autoCompletions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args[0].equalsIgnoreCase("spawn") && sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length == 2) {
                completions.add(String.valueOf((int)player.getLocation().getX()));
            } else if (args.length == 3) {
                completions.add(String.valueOf((int)player.getLocation().getY()));
            } else if (args.length == 4) {
                completions.add(String.valueOf((int)player.getLocation().getZ()));
            } else if (args.length == 5) {
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    completions.add(world.getName());
                }
                return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                    .collect(Collectors.toList());
            }
            return completions;
        }

        return new ArrayList<>();
    }

    private void spawnLootDrop(Location targetLocation) {
        Location startLocation = targetLocation.clone().add(0, fallHeight, 0);

        LootDropEntity lootDrop = new LootDropEntity(startLocation, maxHealth, displayName, particleHeight);
        activeLootDrops.put(lootDrop.getUUID(), lootDrop);

        new LootDropFallTask(lootDrop, targetLocation, fallDurationTicks).runTaskTimer(this, 0L, 1L);

        Bukkit.broadcastMessage(buildIncomingBroadcast(targetLocation));

        getLogger().info("Started loot drop fall from " + startLocation + " to " + targetLocation);
    }

    private String buildIncomingBroadcast(Location targetLocation) {
        String template = broadcastCoordinates ? spawnMessage : spawnMessageNoCoords;

        String message = template
            .replace("%x%", String.valueOf(targetLocation.getBlockX()))
            .replace("%y%", String.valueOf(targetLocation.getBlockY()))
            .replace("%z%", String.valueOf(targetLocation.getBlockZ()))
            .replace("%world%", targetLocation.getWorld().getName());

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private void despawnLootDrop(LootDropEntity lootDrop) {
        if (!activeLootDrops.containsKey(lootDrop.getUUID())) {
            return;
        }

        Location loc = lootDrop.getLocation();
        activeLootDrops.remove(lootDrop.getUUID());
        lootDrop.remove();

        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distance(loc) <= 50) {
                player.sendMessage("§6§l[LOOT DROP] §7The loot drop has despawned...");
            }
        }

        getLogger().info("Loot drop despawned (timeout) at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (activeLootDrops.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) return;

        ArmorStand armorStand = (ArmorStand) event.getEntity();
        LootDropEntity lootDrop = activeLootDrops.get(armorStand.getUniqueId());

        if (lootDrop == null) return;

        event.setCancelled(true);

        double damage = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
        lootDrop.damage(damage);

        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();

            if (useCustomHitSound) {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.5f);
            }
        }

        if (lootDrop.isDead()) {
            dropLoot(lootDrop.getLocation());
            lootDrop.remove();
            activeLootDrops.remove(lootDrop.getUUID());

            Location loc = lootDrop.getLocation();
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distance(loc) <= 50) {
                    player.sendMessage("§6§l[LOOT DROP] §eThe loot drop has been destroyed! Grab the loot!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }
        }
    }

    private void dropLoot(Location location) {
        List<ItemStack> actualLoot = new ArrayList<>();
        ConfigurationSection itemsSection = getConfig().getConfigurationSection("loot-drop.items");

        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                String materialName = itemsSection.getString(key + ".material");
                int amount = resolveAmount(itemsSection, key);
                double chance = itemsSection.getDouble(key + ".chance", 100.0);

                try {
                    Material material = Material.valueOf(materialName.toUpperCase());

                    if (Math.random() * 100 <= chance) {
                        actualLoot.add(new ItemStack(material, amount));
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid material: " + materialName);
                }
            }
        }

        if (actualLoot.isEmpty()) {
            getLogger().warning("No loot items to drop!");
            return;
        }

        getLogger().info("Dropping " + actualLoot.size() + " items at " + location);

        Random random = new Random();
        for (ItemStack item : actualLoot) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * itemDropRadius;
            double dx = Math.cos(angle) * distance;
            double dz = Math.sin(angle) * distance;

            Location dropLocation = location.clone().add(dx, 0.5, dz);
            location.getWorld().dropItemNaturally(dropLocation, item.clone());
        }

        location.getWorld().spawnParticle(Particle.EXPLOSION, location.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
        location.getWorld().spawnParticle(Particle.FIREWORK, location.clone().add(0, 1, 0), 50, 1, 1, 1, 0.1);
    }

    private int resolveAmount(ConfigurationSection itemsSection, String key) {
        Object raw = itemsSection.get(key + ".amount", 1);

        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }

        String str = String.valueOf(raw).trim();

        if (str.contains("-")) {
            String[] parts = str.split("-", 2);
            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (max < min) {
                    int temp = min;
                    min = max;
                    max = temp;
                }
                return min + new Random().nextInt((max - min) + 1);
            } catch (NumberFormatException e) {
                getLogger().warning("Invalid amount range '" + str + "' for item " + key + ", defaulting to 1");
                return 1;
            }
        }

        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid amount '" + str + "' for item " + key + ", defaulting to 1");
            return 1;
        }
    }

    private class LootDropFallTask extends BukkitRunnable {

        private final LootDropEntity lootDrop;
        private final Location startLocation;
        private final Location targetLocation;
        private final double totalDistance;
        private final int totalTicks;
        private int currentTick = 0;

        public LootDropFallTask(LootDropEntity lootDrop, Location targetLocation, int totalDurationTicks) {
            this.lootDrop = lootDrop;
            this.startLocation = lootDrop.getLocation().clone();
            this.targetLocation = targetLocation.clone();
            this.totalDistance = startLocation.getY() - targetLocation.getY();
            this.totalTicks = totalDurationTicks;
        }

        @Override
        public void run() {
            if (currentTick >= totalTicks) {
                cancel();

                lootDrop.teleportTo(targetLocation.clone().add(0, 0.05, 0));

                lootDrop.startParticleEffect();

                lootDrop.scheduleDespawn(entityDespawnSeconds, () -> despawnLootDrop(lootDrop));

                targetLocation.getWorld().spawnParticle(Particle.FLASH, targetLocation, 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
                targetLocation.getWorld().playSound(targetLocation, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);

                getLogger().info("Loot drop landed at " + targetLocation.getBlockX() + ", " + targetLocation.getBlockY() + ", " + targetLocation.getBlockZ());
                return;
            }

            double progress = (double) currentTick / totalTicks;

            double newY = startLocation.getY() - (totalDistance * progress);

            Location newLocation = new Location(
                targetLocation.getWorld(),
                targetLocation.getX(),
                newY,
                targetLocation.getZ()
            );

            lootDrop.teleportTo(newLocation);

            newLocation.getWorld().spawnParticle(Particle.CLOUD, newLocation.clone().add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0.01);

            currentTick++;
        }
    }

    private class LootDropEntity {
        private static final double TEXT_DISPLAY_Y_OFFSET = 2.3;

        private final ArmorStand entity;
        private final TextDisplay textDisplay;
        private final UUID textDisplayUUID;
        private double health;
        private final double maxHealth;
        private BukkitRunnable particleTask;
        private BukkitTask despawnTask;
        private final int particleHeight;
        private final String entityDisplayName;
        private final org.bukkit.World world;
        private final int chunkX;
        private final int chunkZ;
        private boolean chunkTicketHeld;

        public LootDropEntity(Location location, double maxHealth, String displayName, int particleHeight) {
            this.maxHealth = maxHealth;
            this.health = maxHealth;
            this.particleHeight = particleHeight;
            this.entityDisplayName = displayName;
            this.world = location.getWorld();
            this.chunkX = location.getBlockX() >> 4;
            this.chunkZ = location.getBlockZ() >> 4;

            this.chunkTicketHeld = world.addPluginChunkTicket(chunkX, chunkZ, LootDropPlugin.this);

            this.entity = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);

            entity.setVisible(false);
            entity.setGravity(false);
            entity.setInvulnerable(false);
            entity.setCustomNameVisible(false);
            entity.setBasePlate(false);
            entity.setArms(false);

            entity.getEquipment().setHelmet(new ItemStack(Material.CHEST));

            this.textDisplay = (TextDisplay) location.getWorld().spawnEntity(
                location.clone().add(0, TEXT_DISPLAY_Y_OFFSET, 0), EntityType.TEXT_DISPLAY);
            this.textDisplayUUID = textDisplay.getUniqueId();
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setSeeThrough(true);
            textDisplay.setShadowed(true);
            textDisplay.setDefaultBackground(true);

            updateDisplay();

            getLogger().info("Created loot drop entity at " + location);
        }

        public void teleportTo(Location loc) {
            entity.teleport(loc);
            if (textDisplay != null && textDisplay.isValid()) {
                textDisplay.teleport(loc.clone().add(0, TEXT_DISPLAY_Y_OFFSET, 0));
            }
        }

        public void startParticleEffect() {
            if (particleTask != null) {
                particleTask.cancel();
            }

            particleTask = new BukkitRunnable() {
                private int ticks = 0;

                @Override
                public void run() {
                    if (entity == null || !entity.isValid()) {
                        cancel();
                        return;
                    }

                    Location loc = entity.getLocation();

                    for (int i = 0; i < particleHeight; i += 3) {
                        Location particleLoc = loc.clone().add(0, i, 0);
                        entity.getWorld().spawnParticle(
                            Particle.DUST,
                            particleLoc,
                            2,
                            0.1, 0.1, 0.1,
                            0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.5f)
                        );
                    }

                    double radius = 1.5;
                    double y = 1.0;
                    for (int i = 0; i < 3; i++) {
                        double angle = (ticks * 0.1) + (i * 2 * Math.PI / 3);
                        double x = radius * Math.cos(angle);
                        double z = radius * Math.sin(angle);
                        Location particleLoc = loc.clone().add(x, y, z);

                        entity.getWorld().spawnParticle(
                            Particle.END_ROD,
                            particleLoc,
                            1,
                            0, 0, 0,
                            0
                        );
                    }

                    ticks++;
                }
            };
            particleTask.runTaskTimer(LootDropPlugin.this, 0L, 5L);
        }

        public void scheduleDespawn(int seconds, Runnable onDespawn) {
            if (seconds <= 0) return;

            despawnTask = new BukkitRunnable() {
                @Override
                public void run() {
                    onDespawn.run();
                }
            }.runTaskLater(LootDropPlugin.this, seconds * 20L);
        }

        public void damage(double amount) {
            health -= amount;
            if (health < 0) health = 0;
            updateDisplay();

            entity.getWorld().spawnParticle(
                Particle.DAMAGE_INDICATOR,
                entity.getLocation().clone().add(0, 1.5, 0),
                10,
                0.3, 0.3, 0.3,
                0
            );
        }

        private void updateDisplay() {
            double percentage = (health / maxHealth) * 100;
            String healthBar = getHealthBar(percentage);
            String formattedName = formatName(entityDisplayName);

            String line1 = formattedName;
            String line2 = "§7[" + healthBar + "§7] §c" + (int) health + "§7/§c" + (int) maxHealth + " HP";

            if (textDisplay != null && textDisplay.isValid()) {
                textDisplay.setText(line1 + "\n" + line2);
            }
        }

        private String getHealthBar(double percentage) {
            int bars = 10;
            int filled = (int) ((percentage / 100) * bars);
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < bars; i++) {
                if (i < filled) {
                    sb.append("§a█");
                } else {
                    sb.append("§8█");
                }
            }
            return sb.toString();
        }

        private String formatName(String name) {
            return ChatColor.translateAlternateColorCodes('&', name);
        }

        public boolean isDead() {
            return health <= 0;
        }

        public double getHealth() {
            return health;
        }

        public Location getLocation() {
            return entity.getLocation();
        }

        public UUID getUUID() {
            return entity.getUniqueId();
        }

        public ArmorStand getEntity() {
            return entity;
        }

        public void remove() {
            if (particleTask != null) {
                particleTask.cancel();
            }
            if (despawnTask != null) {
                despawnTask.cancel();
            }

            Entity liveEntity = Bukkit.getEntity(getUUID());
            if (liveEntity != null) {
                liveEntity.remove();
            } else if (entity != null && entity.isValid()) {
                entity.remove();
            }

            Entity liveText = textDisplayUUID != null ? Bukkit.getEntity(textDisplayUUID) : null;
            if (liveText != null) {
                liveText.remove();
            } else if (textDisplay != null && textDisplay.isValid()) {
                textDisplay.remove();
            }

            if (chunkTicketHeld) {
                world.removePluginChunkTicket(chunkX, chunkZ, LootDropPlugin.this);
                chunkTicketHeld = false;
            }
        }
    }
}
