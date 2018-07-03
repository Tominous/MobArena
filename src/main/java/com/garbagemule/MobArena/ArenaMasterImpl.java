package com.garbagemule.MobArena;

import static com.garbagemule.MobArena.util.config.ConfigUtils.makeSection;
import static com.garbagemule.MobArena.util.config.ConfigUtils.parseLocation;

import com.garbagemule.MobArena.framework.Arena;
import com.garbagemule.MobArena.framework.ArenaMaster;
import com.garbagemule.MobArena.things.Thing;
import com.garbagemule.MobArena.util.config.ConfigUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ArenaMasterImpl implements ArenaMaster
{
    private MobArena plugin;
    private FileConfiguration config;

    private List<Arena> arenas;
    private Map<Player, Arena> arenaMap;
    private Arena selectedArena;

    private Map<String, ArenaClass> classes;

    private Set<String> allowedCommands;
    private SpawnsPets spawnsPets;
    
    private boolean enabled;

    /**
     * Default constructor.
     */
    public ArenaMasterImpl(MobArena plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();

        this.arenas = new ArrayList<>();
        this.arenaMap = new HashMap<>();

        this.classes = new HashMap<>();

        this.allowedCommands = new HashSet<>();
        this.spawnsPets = new SpawnsPets(Material.BONE);
        
        this.enabled = config.getBoolean("global-settings.enabled", true);
    }

    /*
     * /////////////////////////////////////////////////////////////////////////
     * // // NEW METHODS IN REFACTORING //
     * /////////////////////////////////////////////////////////////////////////
     */

    public MobArena getPlugin() {
        return plugin;
    }

    @Override
    public Messenger getGlobalMessenger() {
        return plugin.getGlobalMessenger();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        config.set("global-settings.enabled", enabled);
    }

    public boolean notifyOnUpdates() {
        return config.getBoolean("global-settings.update-notification", false);
    }

    public List<Arena> getArenas() {
        return arenas;
    }

    public Map<String, ArenaClass> getClasses() {
        return classes;
    }

    public void addPlayer(Player p, Arena arena) {
        arenaMap.put(p, arena);
    }

    public Arena removePlayer(Player p) {
        return arenaMap.remove(p);
    }

    public void resetArenaMap() {
        arenaMap.clear();
    }

    public boolean isAllowed(String command) {
        return allowedCommands.contains(command);
    }

    /*
     * /////////////////////////////////////////////////////////////////////////
     * // // Arena getters //
     * /////////////////////////////////////////////////////////////////////////
     */

    public List<Arena> getEnabledArenas() {
        return getEnabledArenas(arenas);
    }
    
    public List<Arena> getEnabledArenas(List<Arena> arenas) {
        List<Arena> result = new ArrayList<>(arenas.size());
        for (Arena arena : arenas)
            if (arena.isEnabled()) 
                result.add(arena);
        return result;
    }

    public List<Arena> getPermittedArenas(Player p) {
        List<Arena> result = new ArrayList<>(arenas.size());
        for (Arena arena : arenas)
            if (arena.hasPermission(p))
                result.add(arena);
        return result;
    }

    public List<Arena> getEnabledAndPermittedArenas(Player p) {
        List<Arena> result = new ArrayList<>(arenas.size());
        for (Arena arena : arenas)
            if (arena.isEnabled() && arena.hasPermission(p))
                result.add(arena);
        return result;
    }

    public Arena getArenaAtLocation(Location loc) {
        for (Arena arena : arenas)
            if (arena.getRegion().contains(loc))
                return arena;
        return null;
    }

    public List<Arena> getArenasInWorld(World world) {
        List<Arena> result = new ArrayList<>(arenas.size());
        for (Arena arena : arenas)
            if (arena.getWorld().equals(world))
                result.add(arena);
        return result;
    }

    public List<Player> getAllPlayers() {
        List<Player> result = new ArrayList<>(arenas.size());
        for (Arena arena : arenas)
            result.addAll(arena.getAllPlayers());
        return result;
    }

    public List<Player> getAllPlayersInArena(String arenaName) {
        Arena arena = getArenaWithName(arenaName);
        return (arena != null) ? new ArrayList<>(arena.getPlayersInArena()) : new ArrayList<Player>();
    }

    public List<Player> getAllLivingPlayers() {
        List<Player> result = new ArrayList<>();
        for (Arena arena : arenas)
            result.addAll(arena.getPlayersInArena());
        return result;
    }

    public List<Player> getLivingPlayersInArena(String arenaName) {
        Arena arena = getArenaWithName(arenaName);
        return (arena != null) ? new ArrayList<>(arena.getPlayersInArena()) : new ArrayList<Player>();
    }

    public Arena getArenaWithPlayer(Player p) {
        return arenaMap.get(p);
    }

    public Arena getArenaWithPlayer(String playerName) {
        return arenaMap.get(plugin.getServer().getPlayer(playerName));
    }

    public Arena getArenaWithSpectator(Player p) {
        for (Arena arena : arenas) {
            if (arena.getSpectators().contains(p))
                return arena;
        }
        return null;
    }

    public Arena getArenaWithMonster(Entity e) {
        for (Arena arena : arenas)
            if (arena.getMonsterManager().getMonsters().contains(e))
                return arena;
        return null;
    }

    public Arena getArenaWithPet(Entity e) {
        for (Arena arena : arenas)
            if (arena.hasPet(e))
                return arena;
        return null;
    }

    public Arena getArenaWithName(String configName) {
        return getArenaWithName(this.arenas, configName);
    }

    public Arena getArenaWithName(Collection<Arena> arenas, String configName) {
        for (Arena arena : arenas)
            if (arena.configName().equalsIgnoreCase(configName))
                return arena;
        return null;
    }

    /*
     * /////////////////////////////////////////////////////////////////////////
     * // // Initialization //
     * /////////////////////////////////////////////////////////////////////////
     */

    public void initialize() {
        loadSettings();
        loadClasses();
        loadArenas();
    }

    /**
     * Load the global settings.
     */
    public void loadSettings() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("global-settings");
        ConfigUtils.addMissingRemoveObsolete(plugin, "global-settings.yml", section);

        // Grab the commands string
        String cmds = section.getString("allowed-commands", "");

        // Split by commas
        String[] parts = cmds.split(",");

        // Add in the /ma command.
        allowedCommands.add("/ma");

        // Add in each command
        for (String part : parts) {
            allowedCommands.add(part.trim().toLowerCase());
        }

        loadPetItems(section);
    }

    private void loadPetItems(ConfigurationSection settings) {
        String wolf = settings.getString("pet-items.wolf", "");

        Material wolfMaterial = Material.getMaterial(wolf.toUpperCase());

        if (wolfMaterial == null && !wolf.isEmpty()) {
            plugin.getLogger().warning("Unknown item type for wolf pet item: " + wolf);
        }

        spawnsPets = new SpawnsPets(wolfMaterial);
    }

    /**
     * Load all class-related stuff.
     */
    public void loadClasses() {
        ConfigurationSection section = makeSection(plugin.getConfig(), "classes");
        ConfigUtils.addIfEmpty(plugin, "classes.yml", section);


        // Establish the map.
        classes = new HashMap<>();
        Set<String> classNames = section.getKeys(false);

        // Load each individual class.
        for (String className : classNames) {
            loadClass(className);
        }

        // Add a class for "my items"
        loadClass("My Items");
    }

    /**
     * Helper method for loading a single class.
     */
    private ArenaClass loadClass(String classname) {
        ConfigurationSection section = config.getConfigurationSection("classes." + classname);
        String lowercase = classname.toLowerCase().replace(" ", "");

        // If the section doesn't exist, the class doesn't either.
        if (section == null) {
            // We may not have a class entry for My Items, but that's fine
            if (classname.equals("My Items")) {
                ArenaClass myItems = new ArenaClass.MyItems(null, false, false, this);
                classes.put(lowercase, myItems);
                return myItems;
            }
            plugin.getLogger().severe("Failed to load class '" + classname + "'.");
            return null;
        }
        
        // Check if weapons and armor for this class should be unbreakable
        boolean weps = section.getBoolean("unbreakable-weapons", true);
        boolean arms = section.getBoolean("unbreakable-armor", true);

        // Grab the class price, if any
        Thing price = null;
        String priceString = section.getString("price", null);
        if (priceString != null) {
            try {
                price = plugin.getThingManager().parse(priceString);
            } catch (Exception e) {
                plugin.getLogger().warning("Exception parsing class price: " + e.getLocalizedMessage());
                price = null;
            }
        }

        // Create an ArenaClass with the config-file name.
        ArenaClass arenaClass = classname.equals("My Items")
            ? new ArenaClass.MyItems(price, weps, arms, this)
            : new ArenaClass(classname, price, weps, arms);

        // Load items
        loadClassItems(section, arenaClass);

        // Load armor
        loadClassArmor(section, arenaClass);

        // Load potion effects
        loadClassPotionEffects(section, arenaClass);

        // Per-class permissions
        loadClassPermissions(arenaClass, section);
        loadClassLobbyPermissions(arenaClass, section);

        // Check for class chests
        Location cc = parseLocation(section, "classchest", null);
        arenaClass.setClassChest(cc);

        // Finally add the class to the classes map.
        classes.put(lowercase, arenaClass);
        return arenaClass;
    }

    private void loadClassItems(ConfigurationSection section, ArenaClass arenaClass) {
        List<String> items = section.getStringList("items");
        if (items == null || items.isEmpty()) {
            String value = section.getString("items", "");
            items = Arrays.asList(value.split(","));
        }

        List<Thing> things = items.stream()
            .map(String::trim)
            .map(plugin.getThingManager()::parse)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        arenaClass.setItems(things);
    }

    private void loadClassArmor(ConfigurationSection section, ArenaClass arenaClass) {
        // Legacy armor node
        loadClassArmorLegacyNode(section, arenaClass);

        // Specific armor pieces
        loadClassArmorPiece(section, "helmet",     arenaClass::setHelmet);
        loadClassArmorPiece(section, "chestplate", arenaClass::setChestplate);
        loadClassArmorPiece(section, "leggings",   arenaClass::setLeggings);
        loadClassArmorPiece(section, "boots",      arenaClass::setBoots);
        loadClassArmorPiece(section, "offhand",    arenaClass::setOffHand);
    }

    private void loadClassArmorLegacyNode(ConfigurationSection section, ArenaClass arenaClass) {
        List<String> armor = section.getStringList("armor");
        if (armor == null || armor.isEmpty()) {
            String value = section.getString("armor", "");
            armor = Arrays.asList(value.split(","));
        }

        // Prepend "armor:" for the armor thing parser
        List<Thing> things = armor.stream()
            .map(String::trim)
            .map(s -> "armor:" + s)
            .map(plugin.getThingManager()::parse)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        arenaClass.setArmor(things);
    }

    private void loadClassArmorPiece(ConfigurationSection section, String slot, Consumer<Thing> setter) {
        String value = section.getString(slot, null);
        if (value == null) {
            return;
        }
        // Prepend the slot name for the item parser
        Thing thing =  plugin.getThingManager().parse(slot + ":" + value);
        if (thing == null) {
            return;
        }
        setter.accept(thing);
    }

    private void loadClassPotionEffects(ConfigurationSection section, ArenaClass arenaClass) {
        List<String> effects = section.getStringList("effects");
        if (effects == null || effects.isEmpty()) {
            String value = section.getString("effects", "");
            effects = Arrays.asList(value.split(","));
        }

        // Prepend "effect:" for the potion effect thing parser
        List<Thing> things = effects.stream()
            .map(String::trim)
            .map(s -> "effect:" + s)
            .map(plugin.getThingManager()::parse)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        arenaClass.setEffects(things);
    }

    private void loadClassPermissions(ArenaClass arenaClass, ConfigurationSection section) {
        section.getStringList("permissions").stream()
            .map(perm -> "perm:" + perm)
            .map(plugin.getThingManager()::parse)
            .filter(Objects::nonNull)
            .forEach(arenaClass::addPermission);
    }

    private void loadClassLobbyPermissions(ArenaClass arenaClass, ConfigurationSection section) {
        section.getStringList("lobby-permissions").stream()
            .map(perm -> "perm:" + perm)
            .map(plugin.getThingManager()::parse)
            .filter(Objects::nonNull)
            .forEach(arenaClass::addLobbyPermission);
    }

    /**
     * Load all arena-related stuff.
     */
    public void loadArenas() {
        ConfigurationSection section = makeSection(config, "arenas");
        Set<String> arenanames = section.getKeys(false);

        // If no arenas were found, create a default node.
        if (arenanames == null || arenanames.isEmpty()) {
            createArenaNode(section, "default", plugin.getServer().getWorlds().get(0), false);
        }
        
        arenas = new ArrayList<>();
        for (World w : Bukkit.getServer().getWorlds()) {
            loadArenasInWorld(w.getName());
        }
    }
    
    public void loadArenasInWorld(String worldName) {
        Set<String> arenaNames = config.getConfigurationSection("arenas").getKeys(false);
        if (arenaNames == null || arenaNames.isEmpty()) {
            return;
        }
        for (String arenaName : arenaNames) {
            Arena arena = getArenaWithName(arenaName);
            if (arena != null) continue;
            
            String arenaWorld = config.getString("arenas." + arenaName + ".settings.world", "");
            if (!arenaWorld.equals(worldName)) continue;
            
            loadArena(arenaName);
        }
    }
    
    public void unloadArenasInWorld(String worldName) {
        Set<String> arenaNames = config.getConfigurationSection("arenas").getKeys(false);
        if (arenaNames == null || arenaNames.isEmpty()) {
            return;
        }
        for (String arenaName : arenaNames) {
            Arena arena = getArenaWithName(arenaName);
            if (arena == null) continue;
            
            String arenaWorld = arena.getWorld().getName();
            if (!arenaWorld.equals(worldName)) continue;
            
            arena.forceEnd();
            arenas.remove(arena);
        }
    }

    // Load an already existing arena node
    private Arena loadArena(String arenaname) {
        ConfigurationSection section  = makeSection(config, "arenas." + arenaname);
        ConfigurationSection settings = makeSection(section, "settings");
        String worldName = settings.getString("world", "");
        World world;

        if (!worldName.equals("")) {
            world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' for arena '" + arenaname + "' was not found...");
                return null;
            }
        } else {
            world = plugin.getServer().getWorlds().get(0);
            plugin.getLogger().warning("Could not find the world for arena '" + arenaname + "'. Using default world ('" + world.getName() + "')! Check the config-file!");
        }

        ConfigUtils.addMissingRemoveObsolete(plugin, "settings.yml", settings);
        ConfigUtils.addIfEmpty(plugin, "waves.yml", makeSection(section, "waves"));

        Arena arena = new ArenaImpl(plugin, section, arenaname, world);
        arenas.add(arena);
        plugin.getLogger().info("Loaded arena '" + arenaname + "'");
        return arena;
    }

    @Override
    public boolean reloadArena(String name) {
        Arena arena = getArenaWithName(name);
        if (arena == null) return false;

        arena.forceEnd();
        arenas.remove(arena);

        plugin.reloadConfig();
        config = plugin.getConfig();

        loadArena(name);
        return true;
    }

    // Create and load a new arena node
    @Override
    public Arena createArenaNode(String arenaName, World world) {
        ConfigurationSection section = makeSection(config, "arenas");
        return createArenaNode(section, arenaName, world, true);
    }

    // Create a new arena node, and (optionally) load it
    private Arena createArenaNode(ConfigurationSection arenas, String arenaName, World world, boolean load) {
        if (arenas.contains(arenaName)) {
            throw new IllegalArgumentException("Arena already exists!");
        }
        ConfigurationSection section = makeSection(arenas, arenaName);

        // Add missing settings and remove obsolete ones
        ConfigUtils.addMissingRemoveObsolete(plugin, "settings.yml", makeSection(section, "settings"));
        section.set("settings.world", world.getName());
        ConfigUtils.addIfEmpty(plugin, "waves.yml",   makeSection(section, "waves"));
        ConfigUtils.addIfEmpty(plugin, "rewards.yml", makeSection(section, "rewards"));
        plugin.saveConfig();

        // Load the arena
        return (load ? loadArena(arenaName) : null);
    }

    public void removeArenaNode(Arena arena) {
        arenas.remove(arena);

        config.set("arenas." + arena.configName(), null);
        plugin.saveConfig();
    }

    public SpawnsPets getSpawnsPets() {
        return spawnsPets;
    }

    public void reloadConfig() {
        boolean wasEnabled = isEnabled();
        if (wasEnabled) setEnabled(false);
        for (Arena a : arenas) {
            a.forceEnd();
        }
        plugin.reloadConfig();
        config = plugin.getConfig();
        initialize();
        plugin.reloadSigns();
        plugin.reloadAnnouncementsFile();
        if (wasEnabled) setEnabled(true);
    }

    public void saveConfig() {
        plugin.saveConfig();
    }
}
