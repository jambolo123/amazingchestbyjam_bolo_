package me.jambolo.amazingchest;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class GuiMain extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getLogger().info("Plugin został włączony!");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin został wyłączony!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Komenda dostępna tylko dla graczy!");
            return true;
        }

        Player player = (Player) sender;

        getLogger().info("Komenda " + label + " została wywołana przez " + player.getName());

        if (label.equalsIgnoreCase("amazingchest")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("settings")) {
                openSettingsGUI(player);
                return true;
            }
        }

        return false;
    }

    private void openSettingsGUI(Player player) {
        getLogger().info("Otwieranie GUI ustawień dla " + player.getName());

        Inventory gui = Bukkit.createInventory(player, 9, "Amazing Chest Settings");

        // Dodaj elementy do GUI
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        gui.addItem(sword);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory != null && event.getView().getTitle().equals("Amazing Chest Settings")) {
            event.setCancelled(true);
            player.sendMessage("Kliknąłeś na element GUI!");
        }
    }
}
