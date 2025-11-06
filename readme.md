<div align="center">

![CraftKit Banner](https://placehold.co/800x200/3498DB/FFFFFF?text=CraftKit&font=montserrat)

![Java](https://img.shields.io/badge/Java-8-blue?logo=openjdk&logoColor=white)
![Spigot API](https://img.shields.io/badge/Spigot-1.8%20-orange?logo=spigotmc)
![License](https://img.shields.io/badge/License-MIT-yellow?logo=opensourceinitiative)

</div>

> ⚠️ **Version Compatibility Notice**
>
> Please be aware that CraftKit has currently only been tested and is guaranteed to work on **Spigot 1.8.x** through **Spigot 1.16.x**. Support for newer versions is a top priority and will be added very soon!

This library is built on a simple philosophy: **No dependencies. No complex setup. Just copy the code you need.** Each utility is self-contained and provides a clean, modern API for handling traditionally difficult tasks like player input, custom items, and interactive messages.

---

## How to Use: The Copy-Paste Philosophy

This is not a traditional library you add as a dependency. It's a code repository designed for you to take what you need.

1.  Find the folder that matches the Minecraft version you are developing for (e.g., `1.9-1.12`).
2.  Copy the `.java` file(s) for the utilities you want into your own project's source folder.
3.  **Important:** Most utilities depend on `ReflectionUtils.java`, located in the `common/util` folder. Make sure to copy it into your project as well!

---

## Showcase: The API in Action

CraftKit provides a range of easy-to-use tools. Here’s a look at what you can build in just a few lines of code.

| Feature Showcase                                                    | Description |
|:--------------------------------------------------------------------| :--- |
| ![AnvilPrompt Showcase](.github/assets/anvil_showcase.gif)          | **`AnvilPrompt`**: Opens a virtual Anvil GUI, allowing you to easily capture text input from a player. Perfect for naming pets, setting waypoints, or any feature requiring a single line of text. |
| ![SignPrompt Showcase](.github/assets/sign_showcase.gif)            | **`SignPrompt`**: Prompts the player with a virtual sign editor to capture multi-line text input. Ideal for feedback forms, mail systems, or custom commands that require detailed input. |
| ![InventoryBuilder Showcase](.github/assets/inventory_showcase.gif) | **`InventoryBuilder`**: An abstract base class that dramatically simplifies the creation of interactive GUI menus. Handles click events, player management, and even animations, letting you define your items with ease. |

---

## API Usage Examples

### `AnvilPrompt` & `SignPrompt` — Capturing Player Input

Forget Netty handlers and NMS containers. Just create a new prompt and provide a callback for when the player is done.

```java
// --- Chat Input ---
// Asks the player to type "confirm" in chat to proceed.
new ChatPrompt(player, "§cAre you sure? Type 'confirm' to proceed.", (message) -> {
        if (message.equalsIgnoreCase("confirm")) {
        player.sendMessage("§aAction confirmed!");
        // Proceed with the action...
        } else {
                player.sendMessage("§cAction cancelled.");
        }
});

// --- Anvil Input ---
// Prompts the player to enter a name and sends it back to them.
new AnvilPrompt(player, "Enter your name", (text) -> {
        player.sendMessage("§aYour name is: §e" + text);
});

// --- Sign Input ---
// Prompts the player to enter four lines of text for a search.
new SignPrompt(player, (lines, combined) -> {
        player.sendMessage("§aYou searched for: §e" + combined);
});
```

### `ItemBuilder` — Creating Custom ItemStacks

Build complex ItemStacks with lore, custom skull textures, and enchantments using a clean, fluent API.

```java
// Create a "Profile" skull item personalized to the player
ItemStack profileItem = new ItemBuilder(Material.SKULL_ITEM, "§6My Profile")
    .setOwner(player)
    .setLore(
        "§7Level: 42",
        "§7Guild: The Coders",
        "",
        "§eClick to view your stats!"
    )
    .addFlag(ItemFlag.HIDE_ATTRIBUTES)
    .build();

// Create an enchanted "Excalibur" sword
ItemStack excalibur = new ItemBuilder(Material.DIAMOND_SWORD, "§bExcalibur")
    .setUnbreakable()
    .addEnchantment(Enchantment.DAMAGE_ALL, 5)
    .addEnchantment(Enchantment.FIRE_ASPECT, 2)
    .build();
```

### `MessageBuilder` — Sending Interactive Chat Messages

Construct hoverable, clickable, and centered chat messages without wrestling with BungeeCord's `ComponentBuilder`.

```java
// Sends a welcome message with a clickable link to a website.
new MessageBuilder()
    .newLine()
        .center()
        .addText("§bWelcome to the Server!")
    .newLine()
        .center()
        .addInteractiveText(
            "§eClick here to visit our website!",   // Display text
            ClickEvent.Action.OPEN_URL,             // Action on click
            "https://www.spigotmc.org",              // Value for the action
            "§aOpens spigotmc.org"                  // Hover text
        )
    .send(player);
```

### `InventoryBuilder` — Building Interactive Menus

Create complex, interactive GUIs by extending the `InventoryBuilder` class. It handles all the listener registration and boilerplate for you.

```java
// 1. Create a class that extends InventoryBuilder
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

// 3. To open the menu for a player:
new ServerSelector(player).open();
```

---

## How It Works: A Look Under the Hood

CraftKit is designed to be simple on the outside but powerful on the inside. It achieves this by abstracting away complex, version-specific Minecraft internals.

*   **`ReflectionUtils`:** This is the core of the library. It dynamically locates NMS and CraftBukkit classes and methods based on the server's runtime version, allowing a single codebase to work across multiple versions of Minecraft.
*   **Netty Injection:** For the `AnvilPrompt` and `SignPrompt`, the library safely injects a temporary handler into the player's network channel (`Channel`). This allows it to listen for specific incoming packets (like a sign update or an inventory click) without interfering with the server's normal operations.
*   **Packet-Based Rendering:** The prompts work by sending purely client-side packets to the player. For example, `SignPrompt` sends a `PacketPlayOutBlockChange` to create a "ghost" sign that only the target player can see, followed by a packet to open its editor. This means the server's world is never modified.

---

## Features

*   **Zero Dependencies:** Designed to be completely standalone. Just copy and paste the code.
*   **Multi-Version Support:** Core utilities are designed to work on Spigot 1.8 through 1.16.
*   **Player Input Prompts:**
    *   **`AnvilPrompt`:** For single-line text input.
    *   **`SignPrompt`:** For multi-line text input.
    *   **`ChatPrompt`:** For capturing raw chat messages as input.
*   **Builders for Common Tasks:**
    *   **`ItemBuilder`:** A fluent API for creating complex `ItemStack`s.
    *   **`MessageBuilder`:** A simple way to build interactive and formatted chat messages.
*   **GUI Management:**
    *   **`InventoryBuilder`:** An abstract base class to quickly create powerful, interactive inventory menus.