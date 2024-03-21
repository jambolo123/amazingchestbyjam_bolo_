package me.jambolo.amazingchest;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class Save {
    private final File directory;
    private final File configFile;
    private final FileConfiguration config;

    public Save(JavaPlugin plugin) {
        this.directory = new File(plugin.getDataFolder(), "chest");
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);

        // Sprawdź, czy katalog chest istnieje; jeśli nie, utwórz go
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Sprawdź, czy plik config.yml istnieje; jeśli nie, utwórz go
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveGUI(String chestName, Inventory gui) {
        File chestFile = new File(directory, chestName + ".yml");
        FileConfiguration chestConfig = YamlConfiguration.loadConfiguration(chestFile);

        // Zapisz zawartość inventory do pliku YAML
        for (int i = 0; i < gui.getSize(); i++) {
            chestConfig.set("inventory." + i, gui.getItem(i));
        }

        try {
            chestConfig.save(chestFile);
            // Zapisz informacje o skrzynce w config.yml
            config.set("chest." + chestName, chestFile.getName());
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
