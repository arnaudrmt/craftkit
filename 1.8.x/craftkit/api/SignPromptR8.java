package fr.arnaud.craftkit.api;

import fr.arnaud.craftkit.util.ReflectionUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles virtual sign input using Netty and reflection.
 * This class opens a virtual sign editor for a player, allowing them to enter
 * multi-line text input without placing a physical sign in the world.
 */
public class SignPrompt implements Listener {

    private final Player player;
    private final SignCallback callback;
    private final String handlerName;

    // NMS object stored for reuse
    private Object entityPlayer;

    /**
     * Creates a new sign input prompt for a player.
     *
     * @param player   The player who will see the sign editor.
     * @param callback The callback to be executed when the player submits their input.
     */
    public SignPrompt(Player player, SignCallback callback) {
        this.player = player;
        this.callback = callback;
        this.handlerName = "SignPrompt_" + player.getUniqueId(); // Unique name for the Netty handler

        // Register this class as a listener to handle cleanup events
        Bukkit.getPluginManager().registerEvents(this,  JavaPlugin.getProvidingPlugin(this.getClass()));

        openSign();
    }

    /**
     * Constructs and displays the virtual sign editor.
     * This involves sending a fake block change packet followed by an open sign editor packet.
     */
    private void openSign() {
        try {
            // --- 1. GET NMS HANDLES AND CLASSES ---
            this.entityPlayer = ReflectionUtils.invokeMethod(player, "getHandle");
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");

            // Retrieve all necessary NMS/CraftBukkit classes
            Class<?> blockPositionClass = ReflectionUtils.getNMSClass("BlockPosition");
            Class<?> packetPlayOutBlockChangeClass = ReflectionUtils.getNMSClass("PacketPlayOutBlockChange");
            Class<?> packetPlayOutOpenSignEditorClass = ReflectionUtils.getNMSClass("PacketPlayOutOpenSignEditor");
            Class<?> worldServerClass = ReflectionUtils.getNMSClass("WorldServer");
            Class<?> craftWorldClass = ReflectionUtils.getCraftClass("CraftWorld");
            Class<?> blocksClass = ReflectionUtils.getNMSClass("Blocks");
            Class<?> packetPlayInUpdateSignClass = ReflectionUtils.getNMSClass("PacketPlayInUpdateSign");
            Class<?> iChatBaseComponentClass = ReflectionUtils.getNMSClass("IChatBaseComponent");

            // --- 2. PREPARE AND SEND FAKE BLOCK PACKETS ---
            // Create a fake block position at y=0 to avoid interfering with the player's view.
            Object blockPos = blockPositionClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(player.getLocation().getBlockX(), 0, player.getLocation().getBlockZ());

            // Create a PacketPlayOutBlockChange to temporarily tell the client a sign exists at the location.
            Object craftWorld = craftWorldClass.cast(player.getWorld());
            Object worldServer = ReflectionUtils.invokeMethod(craftWorld, "getHandle");
            Object signBlock = blocksClass.getField("STANDING_SIGN").get(null);
            Object signData = ReflectionUtils.invokeMethod(signBlock, "getBlockData");
            Constructor<?> blockChangeConstructor = packetPlayOutBlockChangeClass.getConstructor(worldServerClass.getSuperclass(), blockPositionClass);
            Object blockChangePacket = blockChangeConstructor.newInstance(worldServer, blockPos);
            ReflectionUtils.setFieldValue(blockChangePacket, "block", signData);
            sendPacket(playerConnection, blockChangePacket);

            // Create and send the PacketPlayOutOpenSignEditor to open the GUI.
            Constructor<?> openSignConstructor = packetPlayOutOpenSignEditorClass.getConstructor(blockPositionClass);
            Object openSignPacket = openSignConstructor.newInstance(blockPos);
            sendPacket(playerConnection, openSignPacket);

            // --- 3. INJECT NETTY LISTENER ---
            // This handler will listen for the player's response packet (PacketPlayInUpdateSign).
            injectPacketListener(player, packetPlayInUpdateSignClass, iChatBaseComponentClass, blockChangePacket, blocksClass);

        } catch (Exception e) {
            cleanup(); // Ensure cleanup happens on failure
            throw new RuntimeException("Failed to open SignPrompt for player " + player.getName(), e);
        }
    }

    /**
     * Injects a handler into the player's network channel to read incoming packets.
     */
    private void injectPacketListener(Player p, Class<?> packetClass, Class<?> iChatBaseComponentClass, Object blockChangePacket, Class<?> blocksClass) {
        ChannelDuplexHandler handler = new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                if (packetClass.isInstance(packet)) {
                    // Extract the text lines from the packet
                    Field bField = packet.getClass().getDeclaredField("b");
                    bField.setAccessible(true);
                    Object[] components = (Object[]) bField.get(packet);
                    String[] lines = new String[4];
                    Method getText = iChatBaseComponentClass.getMethod("getText");

                    for (int i = 0; i < components.length; i++) {
                        lines[i] = (String) getText.invoke(components[i]);
                    }

                    // Defer the callback and cleanup to the main thread to ensure thread safety
                    Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(this.getClass()), () -> {
                        // Execute the user-defined callback with the results
                        callback.onSignUpdate(lines, String.join("", lines));

                        // Send a packet to change the fake sign back to air, cleaning up the client's view
                        try {
                            Object airBlock = blocksClass.getField("AIR").get(null);
                            Object airData = ReflectionUtils.invokeMethod(airBlock, "getBlockData");
                            ReflectionUtils.setFieldValue(blockChangePacket, "block", airData);
                            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");
                            sendPacket(playerConnection, blockChangePacket);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            // Clean up listeners and handlers
                            cleanup();
                        }
                    });
                }
                // Pass the packet along the pipeline
                super.channelRead(ctx, packet);
            }
        };

        try {
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");
            Object networkManager = ReflectionUtils.getFieldValue(playerConnection, "networkManager");
            Channel channel = (Channel) ReflectionUtils.getFieldValue(networkManager, "channel");

            if (channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }

            channel.pipeline().addBefore("packet_handler", handlerName, handler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Uninjects the Netty handler from the player's channel.
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
            // Silently fail, as the player may be offline
        }
    }

    /**
     * A unified cleanup method to unregister listeners and handlers.
     */
    private void cleanup() {
        uninjectPacketListener();
        // Unregister the onPlayerQuit listener for THIS INSTANCE
        HandlerList.unregisterAll(this);
    }

    /**
     * Sends a packet to the player via their PlayerConnection.
     */
    private void sendPacket(Object playerConnection, Object packet) throws Exception {
        Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", ReflectionUtils.getNMSClass("Packet"));
        sendPacketMethod.invoke(playerConnection, packet);
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
     * Callback interface for sign input.
     */
    public interface SignCallback {
        /**
         * Called when the player submits their sign input.
         *
         * @param lines         An array of the 4 lines of text entered.
         * @param combinedLines All lines concatenated with no separator.
         */
        void onSignUpdate(String[] lines, String combinedLines);
    }
}