package fr.arnaud.abstractify.api;

import fr.arnaud.abstractify.Abstractify;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles waiting for player chat input and returning it through a callback.
 * Useful for text prompts (e.g., naming items, commands, etc).
 */
public class AbstractChat implements Listener {

    private final Abstractify plugin = Abstractify.getInstance();

    private final Player player;
    private final String messageToSend;
    private final ChatCallback callback;

    /**
     * Create a new chat prompt for a specific player.
     * @param player The player to listen to.
     * @param messageToSend The message to send to the player as a prompt.
     * @param callback Callback executed once the player responds.
     */
    public AbstractChat(Player player, String messageToSend, ChatCallback callback) {
        this.player = player;
        this.messageToSend = messageToSend;
        this.callback = callback;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startListening();
    }

    /**
     * Sends the prompt message and begins listening for the chat input.
     */
    public void startListening() {
        player.sendMessage(messageToSend);

        // Slight delay to ensure listener is ready
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) unregister();
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        event.setCancelled(true);

        unregister();
        callback.onPlayerChat(player, event.getMessage());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(player)) unregister();
    }

    /**
     * Unregisters the chat listener for this player.
     */
    public void unregister() {
        AsyncPlayerChatEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);
    }

    /**
     * Callback interface for chat input.
     */
    public interface ChatCallback {
        void onPlayerChat(Player player, String message);
    }
}