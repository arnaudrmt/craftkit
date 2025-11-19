package fr.arnaud.craftkit.api;

import fr.arnaud.craftkit.util.ReflectionUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Handles virtual Anvil input using Netty and reflection.
 * This class opens a virtual Anvil GUI for a player to type text input.
 * The logic is based on proven implementations to ensure stability and visual synchronization
 * across supported Minecraft versions.
 */
public class AnvilPrompt implements Listener {

    private final Player player;
    private final String prefill;
    private final AnvilCallback callback;
    private final String handlerName;

    // NMS objects are stored to be accessed during cleanup
    private Object entityPlayer;
    private Object container;

    /**
     * Creates a new Anvil input prompt for a player.
     *
     * @param player   The player who will see the Anvil GUI.
     * @param prefill  Optional text to pre-fill the Anvil's input field.
     * @param callback The callback that will be executed when the player submits their input.
     */
    public AnvilPrompt(Player player, String prefill, AnvilCallback callback) {
        this.player = player;
        this.prefill = prefill != null ? prefill : "";
        this.callback = callback;
        this.handlerName = "AnvilPrompt_" + player.getUniqueId(); // A unique name for the Netty handler

        // Register this class as a listener to handle cleanup events
        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getProvidingPlugin(this.getClass()));

        // Immediately open the GUI upon instantiation
        openAnvil();
    }

    /**
     * The core logic to construct and display the virtual Anvil GUI.
     * This method handles the NMS and packet-level interactions required.
     */
    private void openAnvil() {
        try {
            // --- 1. GET NMS HANDLES AND CLASSES ---
            // Get the NMS EntityPlayer from the Bukkit Player
            this.entityPlayer = ReflectionUtils.invokeMethod(player, "getHandle");
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");

            // Retrieve all necessary NMS/CraftBukkit classes via reflection
            Class<?> blockPositionClass = ReflectionUtils.getNMSClass("BlockPosition");
            Class<?> containerAnvilClass = ReflectionUtils.getNMSClass("ContainerAnvil");
            Class<?> entityHumanClass = ReflectionUtils.getNMSClass("EntityHuman");
            Class<?> worldClass = ReflectionUtils.getNMSClass("World");
            Class<?> playerInventoryClass = ReflectionUtils.getNMSClass("PlayerInventory");
            Class<?> iCraftingClass = ReflectionUtils.getNMSClass("ICrafting");
            Class<?> packetPlayOutOpenWindowClass = ReflectionUtils.getNMSClass("PacketPlayOutOpenWindow");
            Class<?> chatMessageClass = ReflectionUtils.getNMSClass("ChatMessage");
            Class<?> packetPlayInWindowClickClass = ReflectionUtils.getNMSClass("PacketPlayInWindowClick");
            Class<?> craftItemStackClass = ReflectionUtils.getCraftClass("inventory.CraftItemStack");
            Class<?> nmsItemStackClass = ReflectionUtils.getNMSClass("ItemStack");
            Class<?> packetPlayOutSetSlotClass = ReflectionUtils.getNMSClass("PacketPlayOutSetSlot");

            // --- 2. PREPARE THE SERVER-SIDE CONTAINER ---
            // Get the next available container ID for the player
            int containerId = (int) ReflectionUtils.invokeMethod(entityPlayer, "nextContainerCounter");

            // Create a new ContainerAnvil instance on the server
            Object playerInventory = ReflectionUtils.getFieldValue(entityPlayer, "inventory");
            Object world = ReflectionUtils.getFieldValue(entityPlayer, "world");
            Object blockPos = blockPositionClass.getConstructor(int.class, int.class, int.class).newInstance(0, 0, 0);

            this.container = containerAnvilClass.getConstructor(playerInventoryClass, worldClass, blockPositionClass, entityHumanClass)
                    .newInstance(playerInventory, world, blockPos, entityPlayer);

            // This is crucial: it prevents the server from closing the inventory due to the player being "too far" from the fake block position.
            ReflectionUtils.setFieldValue(container, "checkReachable", false);

            // --- 3. THE PACKET AND SYNCHRONIZATION SEQUENCE ---
            // The order of these steps is critical to prevent the GUI from instantly closing.

            // STEP A: Send the OpenWindow packet. This tells the client to open the GUI.
            Object title = chatMessageClass.getConstructor(String.class, Object[].class).newInstance("Repair & Name", new Object[0]);
            Object openWindowPacket = packetPlayOutOpenWindowClass.getConstructor(int.class, String.class, ReflectionUtils.getNMSClass("IChatBaseComponent"), int.class)
                    .newInstance(containerId, "minecraft:anvil", title, 0);
            sendPacket(playerConnection, openWindowPacket);

            // STEP B: Set the player's active container on the server. This links the server-side logic to the client-side GUI.
            ReflectionUtils.setFieldValue(entityPlayer, "activeContainer", container);
            ReflectionUtils.setFieldValue(container, "windowId", containerId);

            // STEP C: Add the player as a listener (ICrafting). The server will now send inventory updates to this player for this container.
            container.getClass().getMethod("addSlotListener", iCraftingClass).invoke(container, entityPlayer);

            // STEP D: Manually send a packet to place the item in the slot. This guarantees the client sees the item immediately, preventing visual bugs.
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta paperMeta = paper.getItemMeta();
            paperMeta.setDisplayName(this.prefill);
            paper.setItemMeta(paperMeta);
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsPaper = asNMSCopy.invoke(null, paper);

            Object setSlotPacket = packetPlayOutSetSlotClass.getConstructor(int.class, int.class, nmsItemStackClass)
                    .newInstance(containerId, 0, nmsPaper);
            sendPacket(playerConnection, setSlotPacket);

            // STEP E: Inject our Netty handler to listen for the player clicking the output slot.
            injectPacketListener(player, packetPlayInWindowClickClass);

        } catch (Exception e) {
            cleanup(); // Ensure cleanup happens on failure
            throw new RuntimeException("Failed to open AnvilPrompt for player " + player.getName(), e);
        }
    }

    /**
     * Injects a handler into the player's network channel to read incoming packets.
     *
     * @param p           The player whose channel will be injected.
     * @param packetClass The NMS packet class to listen for (PacketPlayInWindowClick).
     */
    private void injectPacketListener(Player p, Class<?> packetClass) throws Exception {
        Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");
        Object networkManager = ReflectionUtils.getFieldValue(playerConnection, "networkManager");
        Channel channel = (Channel) ReflectionUtils.getFieldValue(networkManager, "channel");

        // Remove any old handler to prevent duplicates
        if (channel.pipeline().get(handlerName) != null) {
            channel.pipeline().remove(handlerName);
        }

        // Add the new handler before the server's main packet handler
        channel.pipeline().addBefore("packet_handler", this.handlerName, new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                // Check if the incoming packet is the one we're looking for
                if (packetClass.isInstance(packet)) {
                    int clickedSlotId = (int) ReflectionUtils.getFieldValue(packet, "slot");

                    // The Anvil's output slot is always 2
                    if (clickedSlotId == 2) {
                        // Get the item the player clicked on
                        Object nmsItemStack = ReflectionUtils.getFieldValue(packet, "item");
                        ItemStack clickedItem = (ItemStack) ReflectionUtils.getCraftClass("inventory.CraftItemStack")
                                .getMethod("asBukkitCopy", ReflectionUtils.getNMSClass("ItemStack"))
                                .invoke(null, nmsItemStack);

                        // If the item has a display name, the input is valid
                        if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                            // Run the callback and cleanup on the main Spigot thread to ensure thread safety
                            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(this.getClass()), () -> {
                                callback.onInput(clickedItem.getItemMeta().getDisplayName());
                                cleanup();
                            });
                        }
                    }
                }
                // Pass the packet along the pipeline to be handled by the server
                super.channelRead(ctx, packet);
            }
        });
    }

    /**
     * Uninjects the Netty handler from the player's channel.
     * This is crucial to prevent memory leaks and unintended behavior.
     */
    private void uninjectPacketListener() {
        if (entityPlayer == null) return;
        try {
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");
            Object networkManager = ReflectionUtils.getFieldValue(playerConnection, "networkManager");
            Channel channel = (Channel) ReflectionUtils.getFieldValue(networkManager, "channel");
            if (channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }
        } catch (Exception e) {
            // Silently fail, as the player might be offline or the channel closed
        }
    }

    /**
     * Sends a packet to the player via their PlayerConnection.
     *
     * @param playerConnection The NMS PlayerConnection object.
     * @param packet           The NMS Packet object to send.
     */
    private void sendPacket(Object playerConnection, Object packet) throws Exception {
        Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", ReflectionUtils.getNMSClass("Packet"));
        sendPacketMethod.invoke(playerConnection, packet);
    }

    /**
     * Cleans up all resources associated with this prompt to prevent memory leaks.
     * This includes uninjecting the Netty handler and unregistering Bukkit listeners.
     */
    private void cleanup() {
        // Run on the main thread to ensure NMS objects are accessed safely
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(this.getClass()), () -> {
            try {
                // Restore the player's default inventory container
                Object defaultContainer = ReflectionUtils.getFieldValue(entityPlayer, "defaultContainer");
                ReflectionUtils.setFieldValue(entityPlayer, "activeContainer", defaultContainer);
                // Trigger the server to close the inventory window
                entityPlayer.getClass().getMethod("closeInventory").invoke(entityPlayer);
            } catch (Exception e) {
                // Ignore exceptions, as the player may have disconnected
            } finally {
                uninjectPacketListener();
                // Unregister the onPlayerQuit and onInventoryClose listeners for THIS INSTANCE
                HandlerList.unregisterAll(this);
            }
        });
    }

    /**
     * Failsafe listener to trigger cleanup if the player closes the inventory manually.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(player)) {
            cleanup();
        }
    }

    /**
     * Failsafe listener to trigger cleanup if the player quits the server.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(player)) {
            cleanup();
        }
    }

    /**
     * Callback interface for Anvil input.
     */
    public interface AnvilCallback {
        /**
         * Called when the player submits their text input.
         *
         * @param text   The text that was entered.
         */
        void onInput(String text);
    }
}