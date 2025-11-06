package fr.arnaud.craftkit.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class to build GUI menus easily.
 * Handles item registration, click actions, and simple animations.
 */
public abstract class InventoryBuilder implements Listener {

    private final Player player;
    private final String title;
    private final Player owner;
    private final int size;
    private final Map<Player, Inventory> inventories = new HashMap<>();
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private final List<BukkitTask> animations = new ArrayList<>();

    public InventoryBuilder(Player player, String title, Player owner, int size) {
        this.player = player;
        this.title = title;
        this.owner = owner;
        this.size = size;
    }

    /**
     * Create and populate the inventory items.
     */
    public abstract void createItems(Player player, Inventory inventory);

    /**
     * Opens the GUI for the player.
     */
    public void open() {
        Inventory inventory = Bukkit.createInventory(owner, size, title);
        inventories.put(player, inventory);
        createItems(player, inventory);
        player.openInventory(inventory);
        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getProvidingPlugin(this.getClass()));
    }

    /**
     * Add an item without a click action.
     */
    public void addItem(int slot, ItemStack item) {
        addItem(slot, item, null);
    }

    /**
     * Add an item with an optional click action.
     */
    public void addItem(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        actions.put(slot, action);
        getInventory(player).setItem(slot, item);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player clicker = (Player) event.getWhoClicked();
        Inventory inv = inventories.get(clicker);

        if (inv == null || !event.getInventory().equals(inv)) return;

        int slot = event.getRawSlot();
        Consumer<InventoryClickEvent> action = actions.get(slot);
        if (action != null) action.accept(event);

        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(getInventory(player))) reset();
    }

    /**
     * Animates an item’s display name by cycling through a list of names.
     */
    public void animateItemName(int slot, List<String> names, long period) {
        BukkitTask task = new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                ItemStack item = getInventory(player).getItem(slot);
                if (item == null) return;

                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(names.get(index));
                item.setItemMeta(meta);

                index = (index + 1) % names.size();
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(this.getClass()), 0L, period);

        animations.add(task);
    }

    /**
     * Closes the GUI and cleans up.
     */
    public void close() {
        player.closeInventory();
        reset();
    }

    /**
     * Cancels animations and unregisters the inventory.
     */
    public void reset() {
        inventories.remove(player);
        animations.forEach(BukkitTask::cancel);
    }

    public Inventory getInventory(Player player) {
        return inventories.get(player);
    }

    public Player getPlayer() {
        return player;
    }
}