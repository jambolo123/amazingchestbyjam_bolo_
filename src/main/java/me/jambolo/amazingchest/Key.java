package me.jambolo.amazingchest;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class Key implements CommandExecutor {

    private Amazingchest amazingchest;

    public Key(Amazingchest amazingchest) {
        this.amazingchest = amazingchest;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /amazingchest key <chestName>");
            return true;
        }

        Map chestName = amazingchest.chestNames;

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Hold an item in your hand to enchant!");
            return true;
        }

        itemInHand.addUnsafeEnchantment(Enchantment.MENDING, 10);

        ItemMeta meta = itemInHand.getItemMeta();
        meta.setDisplayName(ChatColor.BOLD + "" + ChatColor.GOLD + "(" + chestName + ")");
        itemInHand.setItemMeta(meta);

        player.sendMessage(ChatColor.GREEN + "Item in hand enchanted and renamed successfully!");

        return true;
    }

}
