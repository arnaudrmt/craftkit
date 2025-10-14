package fr.arnaud.abstractify.exemples;

import fr.arnaud.abstractify.api.AbstractGUI;
import fr.arnaud.abstractify.api.AbstractItemStack;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Example of a simple confirmation GUI using AbstractGUI.
 * This GUI displays a central option item, a cancel button, and a confirm button.
 */
public class GuiExample extends AbstractGUI {

    private String itemTitle;           // Title of the main option item
    private List<String> itemDescription; // Lore/description of the main option item
    private String itemTexture;         // Custom texture for the main item (skull)

    /**
     * Constructor for the confirmation GUI.
     *
     * @param player The player who will see the GUI
     */
    public GuiExample(Player player) {
        // Call parent constructor: player, GUI title, placeholder (null), size (9*3 slots)
        super(player, "CONFIRMATION", null, 9 * 3);

        this.itemTitle = itemTitle;
        this.itemDescription = itemDescription;
        this.itemTexture = itemTexture;
    }

    /**
     * This method is called to populate the GUI with items.
     *
     * @param player    The player who opened the GUI
     * @param inventory The inventory instance representing the GUI
     */
    @Override
    public void createItems(Player player, Inventory inventory) {

        // ----------------------------
        // 1. Main option item
        // ----------------------------
        AbstractItemStack optionItem = new AbstractItemStack(Material.SKULL_ITEM, itemTitle);
        optionItem.setTexture(itemTexture);          // Set custom skull texture
        optionItem.setLore(itemDescription);        // Set item lore (description)

        addItem(4, optionItem.build());             // Place the item in the center slot

        // ----------------------------
        // 2. Cancel button
        // ----------------------------
        AbstractItemStack cancelItem = new AbstractItemStack(Material.SKULL_ITEM, "§cAnnuler");
        cancelItem.setTexture(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjc1NDgzNjJhMjRjMGZhODQ1M2U0ZDkzZTY4YzU5NjlkZGJkZTU3YmY2NjY2YzAzMTljMWVkMWU4NGQ4OTA2NSJ9fX0="
        ); // Set skull texture for cancel button
        cancelItem.setLore(
                "",
                "§7This option will cancel your choice.",
                "",
                "§cThis action is non-reversible."
        );

        // Add click handler for cancel button
        addItem(12, cancelItem.build(), (e) -> {
            player.sendMessage("§cAction cancelled."); // Notify player
            close();                                    // Close the GUI
        });

        // ----------------------------
        // 3. Confirm button
        // ----------------------------
        AbstractItemStack confirmItem = new AbstractItemStack(Material.SKULL_ITEM, "§aConfirm");
        confirmItem.setTexture(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTc5YTVjOTVlZTE3YWJmZWY0NWM4ZGMyMjQxODk5NjQ5NDRkNTYwZjE5YTQ0ZjE5ZjhhNDZhZWYzZmVlNDc1NiJ9fX0="
        ); // Set skull texture for confirm button
        confirmItem.setLore(
                "",
                "§7This option will proceed with your choice.",
                "",
                "§cThis action is non-reversible."
        );

        // Add click handler for confirm button
        addItem(14, confirmItem.build(), (e) -> {
            player.sendMessage("§aAction succeeded."); // Notify player
            close();                                   // Close the GUI
        });
    }
}