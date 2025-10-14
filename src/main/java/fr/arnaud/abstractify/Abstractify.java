package fr.arnaud.abstractify;

import fr.arnaud.abstractify.api.*;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Abstractify extends JavaPlugin implements Listener {

    private static Abstractify instance;

    @Override
    public void onEnable() {

        instance = this;

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("✅ Abstractify API enabled.");
    }

    public static Abstractify getInstance() {
        return instance;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        new AbstractAnvil(player, "Enter your name", (p, text) -> {
            p.sendMessage("You chose the name: " + text);
        });
        event.setCancelled(true);
    }

    @Override
    public void onDisable() {
        getLogger().info("❌ Abstractify API disabled.");
    }

    public static Abstractify get() {
        return instance;
    }

    public void openAnvil(Player player, String prefill, AbstractAnvil.AnvilCallback callback) {
        new AbstractAnvil(player, prefill, callback);
    }

    public void readChat(Player player, String messageToSend, AbstractChat.ChatCallback callback) {
        new AbstractChat(player, messageToSend, callback);
    }

    public AbstractMessageBuilder createMessage() {
        return new AbstractMessageBuilder();
    }

    public void AbstractSign(Player player, AbstractSign.SignCallback callback) {
        new AbstractSign(player, callback);
    }
}
