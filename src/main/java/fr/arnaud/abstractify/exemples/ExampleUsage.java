package fr.arnaud.abstractify.exemples;

import fr.arnaud.abstractify.api.*;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * ExampleUsage demonstrates how to use the Abstractify API.
 * This includes Anvil input, Sign input, Chat prompts, ItemStack setup, and MessageBuilder messages.
 */
public class ExampleUsage {

    /**
     * Opens a virtual Anvil GUI for the player to enter a name.
     * The entered name is sent back to the player as a chat message.
     *
     * @param player The player who will see the Anvil GUI
     */
    public void openNamePrompt(Player player) {
        new AbstractAnvil(player, "Type name", (p, text) -> {
            // Send the entered name back to the player
            p.sendMessage("§aYou've chosen the name: §e" + text);
        });
    }

    /**
     * Opens a virtual Sign GUI for the player to enter multi-line input.
     * The combined text of all lines is sent back to the player.
     *
     * @param player The player who will see the Sign GUI
     */
    public void openSearchBar(Player player) {
        new AbstractSign(player, (p, lines, combinedLines) ->
                p.sendMessage("§aYou're searching for the word: §e" + combinedLines));
    }

    /**
     * Prompts the player with a chat message to enter text (e.g., password or input).
     * Once the player sends a message, a confirmation is sent back.
     *
     * @param player The player who will enter text via chat
     */
    public void enterPassword(Player player) {
        new AbstractChat(player, "§aYou may enter your password:", (p, message) ->
                p.sendMessage("§aLogging in..."));
    }

    /**
     * Adds example hub items to the player's inventory.
     * Demonstrates how to create clickable and custom items using AbstractItemStack.
     *
     * @param player The player whose inventory will be modified
     */
    public void setHubItem(Player player) {

        // Create a "Servers" item with lore
        AbstractItemStack serverItem = new AbstractItemStack(Material.COMPASS, "§6Servers");
        serverItem.setLore("", "§7Right-click to open the server menu");

        // Create a "Profile" skull item personalized to the player
        AbstractItemStack profileItem = new AbstractItemStack(Material.SKULL_ITEM, "§6Profile");
        profileItem.setOwner(player);
        profileItem.setLore("", "§7Right-click to open the profile menu");

        // Place items in specific inventory slots
        player.getInventory().setItem(4, serverItem.build());
        player.getInventory().setItem(8, profileItem.build());
    }

    /**
     * Sends an interactive, multi-line message to the player using AbstractMessageBuilder.
     * Demonstrates clickable text and custom formatting.
     *
     * @param player The player who will receive the message
     */
    public void sendShopMessage(Player player) {
        new AbstractMessageBuilder()
                .newLine()
                .addText("§6Don't forget to shop our holiday sales!")
                .newLine()
                .addInteractiveText(
                        "Visit our website",                 // Displayed text
                        ClickEvent.Action.OPEN_URL,         // Click action
                        "https://example.com",              // Click target
                        "§aClick me!"                       // Hover text
                )
                .send(player); // Send the built message to the player
    }
}