package me.jambolo.amazingchest;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.Chest;


import java.io.File;
import java.io.IOException;
import java.util.*;

import static jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle.title;


public class Amazingchest extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<Block, String> chestNames = new HashMap<>();
    private final Map<String, Inventory> chestGUIs = new HashMap<>();
    private File chestsFolder;
    private FileConfiguration keysConfig;
    private File keysFile;
    private final Map<Player, Boolean> isCreatingChest = new HashMap<>();
    private final Map<Player, String> chestToCreate = new HashMap<>();
    private final Map<String, String> chestKeys = new HashMap<>();

    @Override
    public void onEnable() {
        getCommand("amazingchest").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        chestsFolder = new File(getDataFolder(), "chests");
        keysFile = new File(getDataFolder(), "keys.yml");

        if (!chestsFolder.exists()) {
            chestsFolder.mkdirs();
        }

        if (!keysFile.exists()) {
            saveResource("keys.yml", false);
        }

        keysConfig = YamlConfiguration.loadConfiguration(keysFile);

        // Wczytaj dane o kluczach z pliku YAML
        loadKeys();
        loadGUIChests();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda jest dostępna tylko dla graczy!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfigs();
            player.sendMessage(ChatColor.YELLOW + "Pliki .yml zostały przeładowane.");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String chestName = args[1];
            if (removeChest(chestName)) {
                chestKeys.remove(chestName); // Usunięcie klucza przypisanego do usuniętej skrzynki
                chestGUIs.remove(chestName); // Usunięcie GUI przypisanego do usuniętej skrzynki
                player.sendMessage(ChatColor.GREEN + "Usunięto skrzynię " + chestName);
            } else {
                player.sendMessage(ChatColor.RED + "Nie można odnaleźć skrzyni o nazwie " + chestName);
            }
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Użyj: /amazingchest create <nazwa>");
            return true;
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

        if (args.length == 4 && args[0].equalsIgnoreCase("key")) {
            String playerName = args[1];
            String chestName = args[2];
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Nieprawidłowa ilość kluczy.");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                player.sendMessage(ChatColor.RED + "Gracz " + playerName + " nie jest online.");
                return true;
            }

            if (chestGUIs.containsKey(chestName)) {
                String key = chestKeys.getOrDefault(chestName, "TRIPWIRE_HOOK"); // Pobranie klucza przypisanego do skrzynki, domyślnie TRIPWIRE_HOOK
                ItemStack keyItem = new ItemStack(Material.valueOf(key), amount);
                ItemMeta meta = keyItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GREEN + "Klucz do skrzynki: " + chestName);
                    keyItem.setItemMeta(meta);
                    targetPlayer.getInventory().addItem(keyItem);
                    player.sendMessage(ChatColor.GREEN + "Dodałeś " + amount + " kluczy do skrzynki " + chestName + " dla gracza " + playerName);
                } else {
                    player.sendMessage(ChatColor.RED + "Nie można utworzyć klucza dla skrzynki " + chestName);
                }
            } else {
                player.sendMessage(ChatColor.RED + "Skrzynia o nazwie " + chestName + " nie istnieje lub nie została jeszcze utworzona!");
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("editkey")) {
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem == null || handItem.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Trzymasz pusty przedmiot w ręce!");
                return true;
            }

            Material newKeyMaterial = handItem.getType();
            String newKey = newKeyMaterial.name();
            chestKeys.put(args[1], newKey);
            player.sendMessage(ChatColor.GREEN + "Pomyślnie zedytowano klucz dla skrzynki " + args[1]);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("settings")) {
            openSettingsGUI(player);
            return true;
        }

        return false;
    }


    private boolean removeChest(String chestName) {
        File chestFile = new File(chestsFolder, chestName + ".yml");
        if (chestFile.exists()) {
            chestFile.delete();

            // Usuń informacje o skrzynce z map
            for (Iterator<Map.Entry<Block, String>> iterator = chestNames.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<Block, String> entry = iterator.next();
                if (entry.getValue().equals(chestName)) {
                    iterator.remove();
                }
            }

            chestKeys.remove(chestName);
            chestGUIs.remove(chestName);

            return true;
        }
        return false;
    }
    private void reloadConfigs() {
        chestsFolder = new File(getDataFolder(), "chests");
        keysFile = new File(getDataFolder(), "keys.yml");

        if (!chestsFolder.exists()) {
            chestsFolder.mkdirs();
        }

        if (!keysFile.exists()) {
            saveResource("keys.yml", false);
        }

        keysConfig = YamlConfiguration.loadConfiguration(keysFile);

        // Wczytaj dane o kluczach z pliku YAML
        loadKeys();
        loadGUIChests();
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
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    String chestName = chestNames.get(clickedBlock);
                    if (chestKeys.containsKey(chestName)) {
                        String key = chestKeys.get(chestName);
                        ItemStack keyItem = new ItemStack(Material.TRIPWIRE_HOOK);
                        ItemMeta meta = keyItem.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ChatColor.GREEN + "Klucz do skrzynki: " + chestName);
                            keyItem.setItemMeta(meta);
                        }
                        if (player.getInventory().getItemInMainHand().isSimilar(keyItem)) {
                            consumeKey(player, keyItem); // Usuwamy klucz z ekwipunku
                            ItemStack randomItem = getRandomItem(chestName);
                            if (randomItem != null) {
                                player.getInventory().addItem(randomItem);
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "Musisz trzymać klucz w ręce, aby otworzyć skrzynkę!");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Ta skrzynka nie posiada przypisanego klucza!");
                    }
                } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    openChestGUI(player, chestNames.get(clickedBlock));
                }
            }
        }
    }


    private void openSettingsGUI(Player player) {
        Inventory settingsGUI = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "Wszystkie skrzynki");

        // Dodanie szyb czerwonych wokół GUI (kontynuacja)
        for (int i = 0; i < 9; i++) {
            settingsGUI.setItem(i, createStainedGlassPane(true)); // Górna krawędź
            settingsGUI.setItem(i + 45, createStainedGlassPane(true)); // Dolna krawędź
        }
        for (int i = 0; i < 45; i += 9) {
            settingsGUI.setItem(i, createStainedGlassPane(true)); // Lewa krawędź
            settingsGUI.setItem(i + 8, createStainedGlassPane(true)); // Prawa krawędź
        }

        // Dodanie skrzynek wewnątrz obramowania
        int currentSlot = 10; // Rozpoczynamy od drugiego rzędu, drugiego slotu (indeks 10)
        for (String chestName : chestGUIs.keySet()) {
            if (currentSlot < 44) { // Sprawdzenie czy jest jeszcze miejsce w środku
                settingsGUI.setItem(currentSlot, createChest(chestName)); // Dodajemy skrzynkę
                currentSlot++;
                if (currentSlot % 9 == 0) {
                    currentSlot += 8; // Przejście do kolejnego rzędu
                }
            } else {
                break; // Przerwanie pętli, gdy brakuje miejsca w środku
            }
        }

        player.openInventory(settingsGUI);
    }

    private ItemStack createChest(String chestName) {
        ItemStack chestItem = new ItemStack(Material.CHEST);
        ItemMeta meta = chestItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + chestName);
            chestItem.setItemMeta(meta);
        }
        return chestItem;
    }

    private ItemStack createStainedGlassPane(boolean isRed) {
        Material glassPaneType = isRed ? Material.RED_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
        ItemStack glassPane = new ItemStack(glassPaneType);
        ItemMeta meta = glassPane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLACK + "");
            glassPane.setItemMeta(meta);
        }
        return glassPane;
    }
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();
        ItemStack clickedItem = event.getCurrentItem();

        if (inv != null && inv.getHolder() instanceof Chest) {
            Chest chest = (Chest) inv.getHolder();
            String chestName = chest.getCustomName();

            event.setCancelled(true);

            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                if (chestKeys.containsKey(chestName)) {
                    String keyName = chestKeys.get(chestName);
                    ItemMeta meta = clickedItem.getItemMeta();
                    if (meta != null) {
                        String itemName = meta.getDisplayName();
                        List<String> itemLore = meta.getLore();
                        if (itemName != null && itemName.equals(ChatColor.GREEN + "Klucz do skrzynki: " + chestName)) {
                            Material itemType = clickedItem.getType(); // Poprawka: Pobierz materiał przedmiotu
                            String itemKeyName = itemType.name(); // Poprawka: Pobierz nazwę materiału przedmiotu
                            if (itemLore != null && !itemLore.isEmpty()) {
                                for (String line : itemLore) {
                                    itemKeyName += "_" + line;
                                }
                            }
                            if (itemKeyName.equals(keyName)) {
                                // Klucz pasuje, otwórz skrzynię
                                inv.setItem(event.getRawSlot(), null); // Usunięcie klucza z ekwipunku gracza
                                openChestGUI(player, chestName);
                            } else {
                                player.sendMessage(ChatColor.RED + "Ten przedmiot nie jest kluczem do tej skrzynki.");
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "Ten przedmiot nie jest kluczem do tej skrzynki.");
                        }
                    }
                }
            }
        }
    }


    private void setNewKey(Player player, String chestName, ItemStack keyItem) {
        if (keyItem != null && keyItem.getType() != Material.AIR) {
            // Zapisz typ i meta-dane przedmiotu jako nowy klucz dla skrzynki
            String key = keyItem.getType().toString();
            ItemMeta meta = keyItem.getItemMeta();
            if (meta != null) {
                key += ":" + meta.serialize().toString(); // Dodaj meta-dane do klucza
            }
            chestKeys.put(chestName, key);

            // Aktualizuj plik konfiguracyjny lub inne odpowiednie miejsce do zapisywania kluczy
            saveKeysConfig();

            player.sendMessage(ChatColor.GREEN + "Ustawiono nowy klucz dla skrzynki " + chestName);
        } else {
            player.sendMessage(ChatColor.RED + "Trzymasz pusty przedmiot w ręce!");
        }
    }


    private void openEditKeyGUI(Player player, String chestName) {
        Inventory editKeyGUI = Bukkit.createInventory(null, 9, ChatColor.BLUE + "Edycja klucza: " + chestName);

        ItemStack setKeyItem = new ItemStack(Material.DIAMOND_SWORD); // Przykładowy przedmiot do ustawienia klucza
        ItemMeta setKeyMeta = setKeyItem.getItemMeta();
        if (setKeyMeta != null) {
            setKeyMeta.setDisplayName(ChatColor.GREEN + "Ustaw nowy klucz"); // Nazwa przedmiotu w GUI
            setKeyItem.setItemMeta(setKeyMeta);
        }
        editKeyGUI.setItem(4, setKeyItem); // Umieść przedmiot w slocie 4

        player.openInventory(editKeyGUI);
    }


    private void openChestSettingsGUI(Player player, String chestName) {
        Inventory chestSettingsGUI = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "Ustawienia skrzynki: " + chestName);

        // Dodajemy żółtą obwódkę
        ItemStack yellowGlass = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta yellowGlassMeta = yellowGlass.getItemMeta();
        if (yellowGlassMeta != null) {
            yellowGlassMeta.setDisplayName(ChatColor.YELLOW + "");
            yellowGlass.setItemMeta(yellowGlassMeta);
        }

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || (i + 1) % 9 == 0) {
                chestSettingsGUI.setItem(i, yellowGlass);
            }
        }

        ItemStack tripwireHook = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta tripwireHookMeta = tripwireHook.getItemMeta();
        if (tripwireHookMeta != null) {
            tripwireHookMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Keys");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + "Kliknij, PPM aby przywołać klucz!");
            lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + "Kliknij, LPM aby edytować klucz!");
            tripwireHookMeta.setLore(lore);
            tripwireHook.setItemMeta(tripwireHookMeta);
        }

        chestSettingsGUI.setItem(11, tripwireHook); // Dodajemy przedmiot Tripwire Hook

        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta clockMeta = clock.getItemMeta();
        if (clockMeta != null) {
            clockMeta.setDisplayName(ChatColor.GREEN + "Zegar");
            clock.setItemMeta(clockMeta);
        }
        chestSettingsGUI.setItem(15, clock);

        ItemStack cocoaBeans = new ItemStack(Material.BROWN_DYE);
        ItemMeta cocoaBeansMeta = cocoaBeans.getItemMeta();
        if (cocoaBeansMeta != null) {
            cocoaBeansMeta.setDisplayName(ChatColor.GREEN + "Brązowy barwnik");
            cocoaBeans.setItemMeta(cocoaBeansMeta);
        }
        chestSettingsGUI.setItem(22, cocoaBeans);

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compass.getItemMeta();
        if (compassMeta != null) {
            compassMeta.setDisplayName(ChatColor.GREEN + "Kompas");
            compass.setItemMeta(compassMeta);
        }
        chestSettingsGUI.setItem(30, compass);

        ItemStack sign = new ItemStack(Material.OAK_SIGN);
        ItemMeta signMeta = sign.getItemMeta();
        if (signMeta != null) {
            signMeta.setDisplayName(ChatColor.GREEN + "Tabliczka");
            sign.setItemMeta(signMeta);
        }
        chestSettingsGUI.setItem(32, sign);

        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = barrier.getItemMeta();
        if (barrierMeta != null) {
            barrierMeta.setDisplayName(ChatColor.RED + "Powrót");
            barrier.setItemMeta(barrierMeta);
        }
        chestSettingsGUI.setItem(53, barrier);

        player.openInventory(chestSettingsGUI);
    }
    private void openChestGUI(Player player, String chestName) {
        Inventory gui = chestGUIs.get(chestName);
        if (gui != null) {
            player.openInventory(gui);
        }
    }

    private void loadGUIChests() {
        if (!chestsFolder.exists()) {
            return;
        }

        // Wczytywanie danych o skrzynkach z plików YAML
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

                        Inventory gui = Bukkit.createInventory(null, 9, ChatColor.DARK_GREEN + chestName);
                        for (String slot : config.getConfigurationSection("items").getKeys(false)) {
                            ItemStack itemStack = config.getItemStack("items." + slot);
                            if (itemStack != null) {
                                gui.setItem(Integer.parseInt(slot), itemStack);
                            }
                        }

                        chestNames.put(block, chestName);
                        chestGUIs.put(chestName, gui);

                        // Wczytywanie kluczy przypisanych do skrzynek
                        if (keysConfig.contains(chestName)) {
                            String key = keysConfig.getString(chestName);
                            chestKeys.put(chestName, key);
                        }
                    }
                }
            }
        }
    }

    private void createChestGUI(Player player, Block block, String chestName) {
        Inventory gui = Bukkit.createInventory(null, 9, ChatColor.DARK_GREEN + chestName);

        ItemStack diamond = new ItemStack(Material.DIAMOND);
        ItemMeta diamondMeta = diamond.getItemMeta();
        if (diamondMeta != null) {
            diamondMeta.setDisplayName(ChatColor.GREEN + "Diament");
            diamond.setItemMeta(diamondMeta);
        }
        gui.setItem(0, diamond);

        ItemStack gold = new ItemStack(Material.GOLD_INGOT);
        ItemMeta goldMeta = gold.getItemMeta();
        if (goldMeta != null) {
            goldMeta.setDisplayName(ChatColor.GOLD + "Złoto");
            gold.setItemMeta(goldMeta);
        }
        gui.setItem(1, gold);

        ItemStack iron = new ItemStack(Material.IRON_INGOT);
        ItemMeta ironMeta = iron.getItemMeta();
        if (ironMeta != null) {
            ironMeta.setDisplayName(ChatColor.WHITE + "Żelazo");
            iron.setItemMeta(ironMeta);
        }
        gui.setItem(2, iron);

        ItemStack coal = new ItemStack(Material.COAL);
        ItemMeta coalMeta = coal.getItemMeta();
        if (coalMeta != null) {
            coalMeta.setDisplayName(ChatColor.GRAY + "Węgiel");
            coal.setItemMeta(coalMeta);
        }
        gui.setItem(3, coal);

        ItemStack copper = new ItemStack(Material.COPPER_INGOT);
        ItemMeta copperMeta = copper.getItemMeta();
        if (copperMeta != null) {
            copperMeta.setDisplayName(ChatColor.AQUA + "Miedź");
            copper.setItemMeta(copperMeta);
        }
        gui.setItem(4, copper);

        chestNames.put(block, chestName);
        chestGUIs.put(chestName, gui);

        // Zapis danych o skrzynce do pliku YAML
        File chestFile = new File(chestsFolder, chestName + ".yml");
        YamlConfiguration chestConfig = YamlConfiguration.loadConfiguration(chestFile);
        chestConfig.set("world", block.getWorld().getName());
        chestConfig.set("x", block.getX());
        chestConfig.set("y", block.getY());
        chestConfig.set("z", block.getZ());
        ConfigurationSection itemsSection = chestConfig.createSection("items");
        for (int i = 0; i < 5; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null) {
                itemsSection.set(String.valueOf(i), item);
            }
        }
        try {
            chestConfig.save(chestFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        player.sendMessage(ChatColor.GREEN + "Utworzono GUI dla skrzyni: " + chestName);
    }

    private ItemStack getRandomItem(String chestName) {
        Inventory gui = chestGUIs.get(chestName);
        if (gui != null) {
            Random random = new Random();
            int size = gui.getSize();
            int attempts = 0;
            while (attempts < size) {
                int randomSlot = random.nextInt(size);
                ItemStack itemStack = gui.getItem(randomSlot);
                if (itemStack != null && itemStack.getType() != Material.AIR) {
                    return itemStack;
                }
                attempts++;
            }
        }
        return null;
    }


    private void giveKey(Player player, String chestName, int amount) {
        String key = chestKeys.get(chestName);
        ItemStack keyItem = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = keyItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Klucz do skrzynki: " + chestName);
            keyItem.setItemMeta(meta);
        }
        keyItem.getItemMeta().getPersistentDataContainer().set(new NamespacedKey(this, "chestKey"), PersistentDataType.STRING, chestName); // Przypisanie nazwy skrzynki do klucza
        player.getInventory().addItem(keyItem);
        player.sendMessage(ChatColor.GREEN + "Otrzymałeś klucz do skrzyni " + chestName + " (" + amount + "x)");
    }


    private void consumeKey(Player player, ItemStack keyItem) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack itemStack = contents[i];
            if (itemStack != null && itemStack.isSimilar(keyItem)) {
                int amount = itemStack.getAmount();
                if (amount > 1) {
                    itemStack.setAmount(amount - 1);
                    player.getInventory().setItem(i, itemStack);
                } else {
                    player.getInventory().setItem(i, null);
                }
                break;
            }
        }
    }


    private String generateRandomKey() {
        StringBuilder key = new StringBuilder();
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < 8; i++) {
            int index = (int) (Math.random() * characters.length());
            key.append(characters.charAt(index));
        }
        return key.toString();
    }

    private void loadKeys() {
        // Wczytaj klucze z pliku YAML
        if (keysConfig.contains("keys")) {
            ConfigurationSection keysSection = keysConfig.getConfigurationSection("keys");
            if (keysSection != null) {
                for (String chestName : keysSection.getKeys(false)) {
                    String key = keysSection.getString(chestName);
                    chestKeys.put(chestName, key);
                }
            }
        }
    }

    private void saveKeysConfig() {
        try {
            keysConfig.save(keysFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        // Zapisz klucze do pliku YAML
        ConfigurationSection keysSection = keysConfig.createSection("keys");
        for (Map.Entry<String, String> entry : chestKeys.entrySet()) {
            keysSection.set(entry.getKey(), entry.getValue());
        }
        try {
            keysConfig.save(keysFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Usuń puste sekcje kluczy z pliku YAML
        for (String key : keysConfig.getKeys(false)) {
            if (keysConfig.getConfigurationSection(key) != null && keysConfig.getConfigurationSection(key).getKeys(false).isEmpty()) {
                keysConfig.set(key, null);
            }
        }
        try {
            keysConfig.save(keysFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}