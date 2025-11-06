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
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * SignPrompt for Minecraft 1.13.x
 * Opens a virtual sign input GUI for a player and captures their text input using reflection.
 */
public class SignPrompt implements Listener {

    private final Player player;
    private final SignCallback callback;
    private final String handlerName;

    public SignPrompt(Player player, SignCallback callback) {
        this.player = player;
        this.callback = callback;
        this.handlerName = "SignPrompt_" + UUID.randomUUID();

        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getProvidingPlugin(this.getClass()));
        openSign();
    }

    private void openSign() {
        try {
            // Get NMS classes using ReflectionUtils
            Class<?> craftPlayerClass = ReflectionUtils.getCraftClass("entity.CraftPlayer");
            Class<?> playerConnectionClass = ReflectionUtils.getNMSClass("PlayerConnection");
            Class<?> packetClass = ReflectionUtils.getNMSClass("Packet");
            Class<?> blockPositionClass = ReflectionUtils.getNMSClass("BlockPosition");
            Class<?> packetPlayOutBlockChangeClass = ReflectionUtils.getNMSClass("PacketPlayOutBlockChange");
            Class<?> packetPlayOutOpenSignEditorClass = ReflectionUtils.getNMSClass("PacketPlayOutOpenSignEditor");
            Class<?> iRegistryClass = ReflectionUtils.getNMSClass("IRegistry");
            Class<?> minecraftKeyClass = ReflectionUtils.getNMSClass("MinecraftKey");

            // Get player and connection objects
            Object craftPlayer = craftPlayerClass.cast(player);
            Object entityPlayer = ReflectionUtils.invokeMethod(craftPlayer, "getHandle");
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");

            // Create a fake sign position
            Constructor<?> blockPositionConstructor = blockPositionClass.getConstructor(int.class, int.class, int.class);
            Object blockPosition = blockPositionConstructor.newInstance(player.getLocation().getBlockX(), 0, player.getLocation().getBlockZ());

            // Get the block registry from IRegistry.BLOCK
            Object blockRegistry = ReflectionUtils.getStaticFieldValue(iRegistryClass, "BLOCK");

            // Create a new MinecraftKey for "minecraft:sign"
            Constructor<?> minecraftKeyConstructor = minecraftKeyClass.getConstructor(String.class);
            Object signMinecraftKey = minecraftKeyConstructor.newInstance("minecraft:sign"); // Use "minecraft:sign" or just "sign"

            // Get the sign block from the registry
            Method getBlockFromRegistryMethod = blockRegistry.getClass().getMethod("get", minecraftKeyClass);
            Object signBlock = getBlockFromRegistryMethod.invoke(blockRegistry, signMinecraftKey);

            // Get the block data for the sign
            Object signBlockData = ReflectionUtils.invokeMethod(signBlock, "getBlockData");

            // Create and configure the PacketPlayOutBlockChange
            Object blockChangePacket = packetPlayOutBlockChangeClass.getConstructor().newInstance();
            ReflectionUtils.setFieldValue(blockChangePacket, "a", blockPosition); // Position field
            ReflectionUtils.setFieldValue(blockChangePacket, "block", signBlockData); // IBlockData field

            // Send the fake block packet
            Method sendPacketMethod = playerConnectionClass.getMethod("sendPacket", packetClass);
            sendPacketMethod.invoke(playerConnection, blockChangePacket);

            // Open the sign editor GUI
            Constructor<?> packetPlayOutOpenSignEditorConstructor = packetPlayOutOpenSignEditorClass.getConstructor(blockPositionClass);
            Object openSignPacket = packetPlayOutOpenSignEditorConstructor.newInstance(blockPosition);
            sendPacketMethod.invoke(playerConnection, openSignPacket);

            // Inject our packet listener
            injectPacketListener();

        } catch (Exception e) {
            e.printStackTrace();
            cleanup();
        }
    }

    private void injectPacketListener() {
        try {
            ChannelDuplexHandler handler = new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                    try {
                        Class<?> packetPlayInUpdateSignClass = ReflectionUtils.getNMSClass("PacketPlayInUpdateSign");
                        if (packetPlayInUpdateSignClass.isInstance(packet)) {
                            // In 1.13+, get back the lines[]
                            String[] lines = (String[]) ReflectionUtils.invokeMethod(packet, "c");

                            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(SignPrompt.this.getClass()), () -> {
                                try {
                                    callback.onSignUpdate(lines, String.join("", lines));
                                    resetFakeSign();
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    cleanup();
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    super.channelRead(ctx, packet);
                }
            };

            Object craftPlayer = ReflectionUtils.getCraftClass("entity.CraftPlayer").cast(player);
            Object entityPlayer = ReflectionUtils.invokeMethod(craftPlayer, "getHandle");
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

    private void resetFakeSign() {
        try {
            // Get NMS classes
            Class<?> craftPlayerClass = ReflectionUtils.getCraftClass("entity.CraftPlayer");
            Class<?> playerConnectionClass = ReflectionUtils.getNMSClass("PlayerConnection");
            Class<?> packetClass = ReflectionUtils.getNMSClass("Packet");
            Class<?> blockPositionClass = ReflectionUtils.getNMSClass("BlockPosition");
            Class<?> blocksClass = ReflectionUtils.getNMSClass("Blocks");
            Class<?> packetPlayOutBlockChangeClass = ReflectionUtils.getNMSClass("PacketPlayOutBlockChange");

            // Get player and connection objects
            Object craftPlayer = craftPlayerClass.cast(player);
            Object entityPlayer = ReflectionUtils.invokeMethod(craftPlayer, "getHandle");
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");

            // Create the block position
            Constructor<?> blockPositionConstructor = blockPositionClass.getConstructor(int.class, int.class, int.class);
            Object blockPosition = blockPositionConstructor.newInstance(player.getLocation().getBlockX(), 0, player.getLocation().getBlockZ());

            // Get AIR block data
            Object airBlock = ReflectionUtils.getStaticFieldValue(blocksClass, "AIR");
            Object airBlockData = ReflectionUtils.invokeMethod(airBlock, "getBlockData");

            // Create and send the packet to change the block back to air
            Object resetPacket = packetPlayOutBlockChangeClass.getConstructor().newInstance();
            ReflectionUtils.setFieldValue(resetPacket, "a", blockPosition); // Position field
            ReflectionUtils.setFieldValue(resetPacket, "block", airBlockData); // IBlockData field

            Method sendPacketMethod = playerConnectionClass.getMethod("sendPacket", packetClass);
            sendPacketMethod.invoke(playerConnection, resetPacket);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void uninjectPacketListener() {
        try {
            Object craftPlayer = ReflectionUtils.getCraftClass("entity.CraftPlayer").cast(player);
            Object entityPlayer = ReflectionUtils.invokeMethod(craftPlayer, "getHandle");
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");
            Object networkManager = ReflectionUtils.getFieldValue(playerConnection, "networkManager");
            Channel channel = (Channel) ReflectionUtils.getFieldValue(networkManager, "channel");

            if (channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }
        } catch (Exception ignored) {}
    }

    private void cleanup() {
        uninjectPacketListener();
        HandlerList.unregisterAll(this);
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