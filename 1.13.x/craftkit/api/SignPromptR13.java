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
import java.util.UUID;

/**
 * Handles virtual sign input using Netty and reflection for Minecraft 1.13.x.
 */
public class SignPrompt implements Listener {

    private final Player player;
    private final SignCallback callback;
    private final String handlerName;

    // NMS object stored for reuse
    private Object entityPlayer;

    public SignPrompt(Player player, SignCallback callback) {
        this.player = player;
        this.callback = callback;
        this.handlerName = "SignPrompt_" + player.getUniqueId();

        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getProvidingPlugin(this.getClass()));
        openSign();
    }

    private void openSign() {
        try {
            // --- 1. GET NMS HANDLES AND CLASSES ---
            this.entityPlayer = ReflectionUtils.invokeMethod(player, "getHandle");
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");

            Class<?> blockPositionClass = ReflectionUtils.getNMSClass("BlockPosition");
            Class<?> packetPlayOutBlockChangeClass = ReflectionUtils.getNMSClass("PacketPlayOutBlockChange");
            Class<?> packetPlayOutOpenSignEditorClass = ReflectionUtils.getNMSClass("PacketPlayOutOpenSignEditor");
            Class<?> blocksClass = ReflectionUtils.getNMSClass("Blocks");
            Class<?> packetPlayInUpdateSignClass = ReflectionUtils.getNMSClass("PacketPlayInUpdateSign");

            // --- 2. PREPARE AND SEND FAKE BLOCK PACKETS ---
            // Create a fake block position at y=0
            Object blockPos = blockPositionClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(player.getLocation().getBlockX(), 0, player.getLocation().getBlockZ());

            // PacketPlayOutBlockChange
            // Get the IBlockData for a Sign
            Object signBlock = blocksClass.getField("SIGN").get(null);
            Object signData = ReflectionUtils.invokeMethod(signBlock, "getBlockData");

            Object blockChangePacket = packetPlayOutBlockChangeClass.getConstructor().newInstance();
            ReflectionUtils.setFieldValue(blockChangePacket, "a", blockPos);
            ReflectionUtils.setFieldValue(blockChangePacket, "block", signData);

            sendPacket(playerConnection, blockChangePacket);

            // PacketPlayOutOpenSignEditor
            Constructor<?> openSignConstructor = packetPlayOutOpenSignEditorClass.getConstructor(blockPositionClass);
            Object openSignPacket = openSignConstructor.newInstance(blockPos);
            sendPacket(playerConnection, openSignPacket);

            // --- 3. INJECT NETTY LISTENER ---
            injectPacketListener(packetPlayInUpdateSignClass, blockChangePacket, blocksClass);

        } catch (Exception e) {
            cleanup();
            throw new RuntimeException("Failed to open SignPrompt for player " + player.getName(), e);
        }
    }

    private void injectPacketListener(Class<?> packetClass, Object blockChangePacket, Class<?> blocksClass) {
        ChannelDuplexHandler handler = new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                if (packetClass.isInstance(packet)) {
                    Field bField = packet.getClass().getDeclaredField("b");
                    bField.setAccessible(true);

                    String[] lines = (String[]) bField.get(packet);

                    // Defer callback to main thread
                    Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(SignPrompt.this.getClass()), () -> {
                        callback.onSignUpdate(lines, String.join("", lines));

                        // Cleanup: Set block back to AIR
                        try {
                            Object airBlock = blocksClass.getField("AIR").get(null);
                            Object airData = ReflectionUtils.invokeMethod(airBlock, "getBlockData");
                            ReflectionUtils.setFieldValue(blockChangePacket, "block", airData);
                            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");
                            sendPacket(playerConnection, blockChangePacket);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            cleanup();
                        }
                    });
                }
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
            // Silently fail
        }
    }

    private void cleanup() {
        uninjectPacketListener();
        HandlerList.unregisterAll(this);
    }

    private void sendPacket(Object playerConnection, Object packet) throws Exception {
        Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", ReflectionUtils.getNMSClass("Packet"));
        sendPacketMethod.invoke(playerConnection, packet);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(player)) {
            cleanup();
        }
    }

    public interface SignCallback {
        void onSignUpdate(String[] lines, String combinedLines);
    }
}