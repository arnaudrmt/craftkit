package fr.arnaud.craftkit.exemples;

import fr.arnaud.craftkit.api.InventoryBuilder;
import fr.arnaud.craftkit.api.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

public class ServerSelector extends InventoryBuilder {

    public ServerSelector(Player player) {
        // Call the parent constructor with the player, GUI title, owner (can be null), and size
        super(player, "§8Select a Server", null, 27);
    }

    @Override
    public void createItems(Player player, Inventory inventory) {
        // 2. Use ItemBuilder and addItem to populate the GUI

        // A simple item with no action
        ItemStack border = new ItemBuilder(Material.STAINED_GLASS_PANE, " ").setDurability((short) 15).build();
        for (int i = 0; i < 27; i++) {
            addItem(i, border);
        }

        // An interactive item with a click action
        ItemStack factions = new ItemBuilder(Material.DIAMOND_SWORD, "§cFactions")
                .setLore("§7Click to join the Factions server!")
                .addFlag(ItemFlag.HIDE_ATTRIBUTES)
                .build();

        // Add the item to slot 13 and define what happens when it's clicked
        addItem(13, factions, (event) -> {
            player.sendMessage("§aConnecting you to Factions...");
            // Add your BungeeCord server switch logic here
            close(); // Close the GUI after clicking
        });
    }
}