package fr.arnaud.craftkit;

import org.bukkit.plugin.java.JavaPlugin;

public class CraftKit extends JavaPlugin {

    private static CraftKit instance;

    @Override
    public void onEnable() {

        instance = this;

        getLogger().info("✅ CraftKit API enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("❌ CraftKit API disabled.");
    }
}
