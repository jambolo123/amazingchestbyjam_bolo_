package me.jambolo.amazingchest;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Amazingchest extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<Player, Boolean> isCreatingChest = new HashMap<>();
    private final Map<Player, String> chestToCreate = new HashMap<>();
    public final Map<Block, String> chestNames = new HashMap<>();
    private final Map<String, Inventory> chestGUIs = new HashMap<>();
    private File chestsFolder;
    private File configFile;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        getCommand("amazingchest").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        chestsFolder = new File(getDataFolder(), "chests");
        configFile = new File(getDataFolder(), "config.yml");
        config = getConfig();
        config.options().copyDefaults(true);
        saveConfig();

        if (!chestsFolder.exists()) {
            chestsFolder.mkdirs();
        }

        loadGUIChests();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda jest dostępna tylko dla graczy!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Użyj: /amazingchest create <nazwa>");
            return true;
        }
        if (args[0].equalsIgnoreCase("key")) {
            Key key = new Key(this);
            key.key(sender, command, label, args);
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
            String chestName = String.join(" ", args).substring(7);
            if (chestName.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Zapomniałeś o nazwie!");
                return true;
            }

            isCreatingChest.put(player, true);
            chestToCreate.put(player, chestName);
            player.sendMessage(ChatColor.YELLOW + "Kliknij prawym przyciskiem myszy na skrzynkę, aby stworzyć GUI o nazwie: " + ChatColor.GREEN + chestName);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            chestGUIs.clear();
            chestNames.clear();
            loadGUIChests();
            player.sendMessage(ChatColor.GREEN + "Przeładowano konfiguracje AmazingChest.");
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock != null && clickedBlock.getType() == Material.CHEST) {
            if (isCreatingChest.containsKey(player) && isCreatingChest.get(player)) {
                event.setCancelled(true);
                String chestName = chestToCreate.get(player);
                createChestGUI(player, clickedBlock, chestName);
                isCreatingChest.remove(player);
                chestToCreate.remove(player);
            } else if (chestNames.containsKey(clickedBlock)) {
                event.setCancelled(true);
                openChestGUI(player, chestNames.get(clickedBlock));
            }
        }
    }

    private void loadGUIChests() {
        if (!chestsFolder.exists()) {
            return;
        }

        for (File file : chestsFolder.listFiles()) {
            if (file.getName().endsWith(".yml")) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                String chestName = file.getName().replace(".yml", "");

                if (config.contains("items")) {
                    String worldName = config.getString("world");
                    int x = config.getInt("x");
                    int y = config.getInt("y");
                    int z = config.getInt("z");

                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        Block block = world.getBlockAt(x, y, z);

                        ConfigurationSection itemsSection = config.getConfigurationSection("items");
                        if (itemsSection != null) {
                            int size = config.getInt("size", 9); // Domyślny rozmiar GUI to 9
                            Inventory gui = Bukkit.createInventory(null, size, ChatColor.DARK_GREEN + chestName);
                            for (String slot : itemsSection.getKeys(false)) {
                                ItemStack itemStack = itemsSection.getItemStack(slot);
                                gui.setItem(Integer.parseInt(slot), itemStack);
                            }
                            chestNames.put(block, chestName);
                            chestGUIs.put(chestName, gui);
                        }
                    }
                } else {
                    getLogger().warning("Brak sekcji z danymi skrzynki w pliku YAML: " + file.getName());
                }
            }
        }
    }


    private void createChestGUI(Player player, Block block, String chestName) {
        Inventory gui = Bukkit.createInventory(null, 9, ChatColor.DARK_GREEN + chestName);

        ItemStack exampleItem = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta itemMeta = exampleItem.getItemMeta();
        if (itemMeta != null) {
            itemMeta.setDisplayName(ChatColor.YELLOW + "Przykładowy Przedmiot");
            exampleItem.setItemMeta(itemMeta);
        }
        gui.setItem(4, exampleItem);

        chestNames.put(block, chestName);
        chestGUIs.put(chestName, gui);
        saveGUIChestToFile(block, chestName);
    }

    private void saveGUIChestToFile(Block block, String chestName) {
        File chestFile = new File(chestsFolder, chestName + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(chestFile);

        Inventory gui = chestGUIs.get(chestName);

        if (gui != null) {
            ConfigurationSection itemsSection = config.createSection("items");
            for (int i = 0; i < gui.getSize(); i++) {
                ItemStack itemStack = gui.getItem(i);
                if (itemStack != null) {
                    itemsSection.set(String.valueOf(i), itemStack);
                }
            }
        }
        config.set("world", block.getWorld().getName());
        config.set("x", block.getX());
        config.set("y", block.getY());
        config.set("z", block.getZ());

        try {
            config.save(chestFile);
        } catch (IOException e) {
            getLogger().warning("Wystąpił problem z zapisaniem GUI do pliku YAML.");
            e.printStackTrace();
        }
    }

    private void openChestGUI(Player player, String chestName) {
        Inventory gui = chestGUIs.get(chestName);
        if (gui != null) {
            player.openInventory(gui);
        } else {
            player.sendMessage(ChatColor.RED + "Wystąpił błąd podczas otwierania GUI.");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block brokenBlock = event.getBlock();

        if (isCreatingChest.containsKey(player) && isCreatingChest.get(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nie możesz niszczyć skrzynek podczas tworzenia GUI!");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory != null && chestGUIs.containsValue(clickedInventory)) {
            event.setCancelled(true); // Blokada klikania tylko w GUI
        }
    }
}
