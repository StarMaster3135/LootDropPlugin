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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class LootDropPlugin extends JavaPlugin implements Listener, TabCompleter {

    // Main collection to track active loot drops by their ArmorStand UUID
    private final Map<UUID, LootDropEntity> activeLootDrops = new HashMap<>();
    
    // Configuration fields
    private double maxHealth;
    private String displayName;
    private int particleHeight;
    private int fallHeight;
    private int fallDurationTicks;
    private boolean useCustomHitSound; 

    @Override
    public void onEnable() {
        // Ensure config is saved and loaded first
        saveDefaultConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("lootdrop").setTabCompleter(this);
        getLogger().info("LootDrop Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Clean up all active loot drops when the plugin shuts down
        for (LootDropEntity lootDrop : activeLootDrops.values()) {
            lootDrop.remove();
        }
        activeLootDrops.clear();
        getLogger().info("LootDrop Plugin has been disabled!");
    }

    /**
     * Loads configuration values from config.yml.
     */
    private void loadConfig() {
        reloadConfig(); // Ensure we load the latest from disk
        maxHealth = getConfig().getDouble("loot-drop.max-health", 500.0);
        displayName = getConfig().getString("loot-drop.display-name", "&6&lLOOT DROP");
        particleHeight = getConfig().getInt("loot-drop.particle-height", 50);
        
        // Load falling animation settings
        fallHeight = getConfig().getInt("loot-drop.fall-animation.height", 20);
        fallDurationTicks = getConfig().getInt("loot-drop.fall-animation.duration-ticks", 60);
        
        // Load custom hit sound setting (Default to true for backward compatibility)
        useCustomHitSound = getConfig().getBoolean("loot-drop.use-custom-hit-sound", true);

        getLogger().info("Loaded base configuration for loot drop.");
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

                    Bukkit.broadcastMessage("§6§l[LOOT DROP] §eA loot drop is incoming! Find the landing site!");

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
        }
        return false;
    }

    /**
     * Handles tab completion for the /lootdrop command.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("lootdrop")) return null;
        if (!sender.hasPermission("lootdrop.admin")) return new ArrayList<>();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("spawn");
            completions.add("reload");
            completions.add("list");

            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args[0].equalsIgnoreCase("spawn") && sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length == 2) { // X coordinate
                completions.add(String.valueOf((int)player.getLocation().getX()));
            } else if (args.length == 3) { // Y coordinate
                completions.add(String.valueOf((int)player.getLocation().getY()));
            } else if (args.length == 4) { // Z coordinate
                completions.add(String.valueOf((int)player.getLocation().getZ()));
            } else if (args.length == 5) { // World names
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

    /**
     * Initiates the loot drop spawn process, including the descent animation.
     * @param targetLocation The final landing location.
     */
    private void spawnLootDrop(Location targetLocation) {
        // Calculate the starting location for the fall animation
        Location startLocation = targetLocation.clone().add(0, fallHeight, 0);

        // 1. Create the entity (it is marked as invisible in its constructor)
        LootDropEntity lootDrop = new LootDropEntity(startLocation, maxHealth, displayName, particleHeight);
        activeLootDrops.put(lootDrop.getUUID(), lootDrop);
        
        // 2. Start the falling animation
        // This task will start the standard particle effect once the drop has landed.
        new LootDropFallTask(lootDrop, targetLocation, fallDurationTicks).runTaskTimer(this, 0L, 1L);

        getLogger().info("Started loot drop fall from " + startLocation + " to " + targetLocation);
    }

    /**
     * Handles damage events to the loot drop.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) return;

        ArmorStand armorStand = (ArmorStand) event.getEntity();
        LootDropEntity lootDrop = activeLootDrops.get(armorStand.getUniqueId());

        if (lootDrop == null) return;

        // Cancel the damage event completely to prevent vanilla damage mechanics
        event.setCancelled(true);
        
        double damage = event.getDamage(EntityDamageEvent.DamageModifier.BASE); // Get base damage
        lootDrop.damage(damage);

        // Play a sound for the attacker.
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            // Check config to see if we should play the custom hit sound
            if (useCustomHitSound) {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.5f);
            }
        }

        if (lootDrop.isDead()) {
            dropLoot(lootDrop.getLocation());
            lootDrop.remove();
            activeLootDrops.remove(lootDrop.getUUID());

            // Broadcast message on destruction
            Location loc = lootDrop.getLocation();
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distance(loc) <= 50) {
                    player.sendMessage("§6§l[LOOT DROP] §eThe loot drop has been destroyed! Grab the loot!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Drops the actual loot items based on the configuration and chance rolls.
     * @param location The location where the loot should be dropped.
     */
    private void dropLoot(Location location) {
        List<ItemStack> actualLoot = new ArrayList<>();
        ConfigurationSection itemsSection = getConfig().getConfigurationSection("loot-drop.items");
        
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                String materialName = itemsSection.getString(key + ".material");
                int amount = itemsSection.getInt(key + ".amount", 1);
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

        for (ItemStack item : actualLoot) {
            location.getWorld().dropItemNaturally(location, item.clone());
        }

        // Spawn explosion effects
        location.getWorld().spawnParticle(Particle.EXPLOSION, location.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
        location.getWorld().spawnParticle(Particle.FIREWORK, location.clone().add(0, 1, 0), 50, 1, 1, 1, 0.1);
    }
    
    // =========================================================================
    // NESTED CLASSES
    // =========================================================================

    /**
     * Handles the logic for the continuous falling animation.
     */
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
                // Animation finished
                cancel();
                
                // Teleport to the final, precise location.
                // Using 0.05 offset to ensure the chest sits cleanly on the ground block 
                // and the ArmorStand hitbox/nametag are in the correct place for a non-marker entity.
                lootDrop.getEntity().teleport(targetLocation.clone().add(0, 0.05, 0));
                
                // Start the particle beam effect
                lootDrop.startParticleEffect();
                
                // Landing sound effect
                // Using FLASH particle for compatibility and visual impact
                targetLocation.getWorld().spawnParticle(Particle.FLASH, targetLocation, 1); 
                targetLocation.getWorld().playSound(targetLocation, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
                
                getLogger().info("Loot drop landed at " + targetLocation.getBlockX() + ", " + targetLocation.getBlockY() + ", " + targetLocation.getBlockZ());
                return;
            }

            // Calculate the progress (0.0 to 1.0)
            double progress = (double) currentTick / totalTicks;
            
            // Calculate the new Y position using linear interpolation
            double newY = startLocation.getY() - (totalDistance * progress);
            
            // Create the new location for teleport
            Location newLocation = new Location(
                targetLocation.getWorld(), 
                targetLocation.getX(),
                newY, 
                targetLocation.getZ()
            );
            
            // Teleport the ArmorStand entity
            lootDrop.getEntity().teleport(newLocation);
            
            // Spawn a smoke/trail effect below the dropping loot
            newLocation.getWorld().spawnParticle(Particle.CLOUD, newLocation.clone().add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0.01);
            
            currentTick++;
        }
    }

    /**
     * Manages the data and behavior of a single loot drop ArmorStand entity.
     */
    private class LootDropEntity {
        private final ArmorStand entity;
        private double health;
        private final double maxHealth;
        private BukkitRunnable particleTask;
        private final int particleHeight;
        private final String entityDisplayName;

        public LootDropEntity(Location location, double maxHealth, String displayName, int particleHeight) {
            this.maxHealth = maxHealth;
            this.health = maxHealth;
            this.particleHeight = particleHeight;
            this.entityDisplayName = displayName;

            // Spawn the ArmorStand
            this.entity = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
            
            // --- Appearance and Behavior Settings ---
            entity.setVisible(false);         // Make the body invisible
            entity.setGravity(false);         // Stop it from falling
            entity.setInvulnerable(false);    // MUST be false to take damage (Hitbox enabled)
            entity.setCustomNameVisible(true);
            entity.setBasePlate(false);
            entity.setArms(false);
            // Glowing is disabled to prevent the invisible body's outline from showing.
            
            // Set the appearance (e.g., a Chest helmet)
            entity.getEquipment().setHelmet(new ItemStack(Material.CHEST));
            
            updateDisplay();

            getLogger().info("Created loot drop entity at " + location);
        }

        /**
         * Starts the continuous particle beam effect when the drop has landed.
         */
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

                    // Create stationary particle beam from above
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

                    // Rotating particles around the entity
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

        public void damage(double amount) {
            health -= amount;
            if (health < 0) health = 0;
            updateDisplay();

            // Damage particle effect
            entity.getWorld().spawnParticle(
                Particle.DAMAGE_INDICATOR,
                entity.getLocation().clone().add(0, 1.5, 0),
                10,
                0.3, 0.3, 0.3,
                0
            );

            // The custom sound logic is now in the main event handler.
        }

        private void updateDisplay() {
            double percentage = (health / maxHealth) * 100;
            String healthBar = getHealthBar(percentage);
            String formattedName = formatName(entityDisplayName);
            // Update the display name with the health bar
            entity.setCustomName(formattedName + " §7[" + healthBar + "§7] §c" + (int)health + "§7/§c" + (int)maxHealth + " HP");
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
            if (entity != null) {
                entity.remove();
            }
        }
    }
}
