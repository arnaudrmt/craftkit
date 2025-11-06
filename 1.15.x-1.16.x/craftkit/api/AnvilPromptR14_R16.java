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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Handles virtual Anvil input using Netty and reflection for Minecraft 1.14.4.
 * This class opens a virtual Anvil GUI for a player to type text input.
 */
public class AnvilPrompt implements Listener {

    private final Player player;
    private final String prefill;
    private final AnvilCallback callback;
    private final String handlerName;

    private Object entityPlayer;
    private Object container;

    public AnvilPrompt(Player player, String prefill, AnvilCallback callback) {
        this.player = player;
        this.prefill = prefill != null ? prefill : "";
        this.callback = callback;
        this.handlerName = "AnvilPrompt_" + player.getUniqueId();

        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getProvidingPlugin(this.getClass()));
        openAnvil();
    }

    private void openAnvil() {
        try {
            // --- 1. GET NMS HANDLES AND CLASSES ---
            this.entityPlayer = ReflectionUtils.invokeMethod(player, "getHandle");
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");

            Class<?> blockPositionClass = ReflectionUtils.getNMSClass("BlockPosition");
            Class<?> containerAnvilClass = ReflectionUtils.getNMSClass("ContainerAnvil");
            Class<?> worldClass = ReflectionUtils.getNMSClass("World");
            Class<?> playerInventoryClass = ReflectionUtils.getNMSClass("PlayerInventory");
            Class<?> containerAccessClass = ReflectionUtils.getNMSClass("ContainerAccess");
            Class<?> iCraftingClass = ReflectionUtils.getNMSClass("ICrafting");
            Class<?> packetPlayOutOpenWindowClass = ReflectionUtils.getNMSClass("PacketPlayOutOpenWindow");
            Class<?> chatMessageClass = ReflectionUtils.getNMSClass("ChatMessage");
            Class<?> containersClass = ReflectionUtils.getNMSClass("Containers");
            Class<?> packetPlayInWindowClickClass = ReflectionUtils.getNMSClass("PacketPlayInWindowClick");
            Class<?> craftItemStackClass = ReflectionUtils.getCraftClass("inventory.CraftItemStack");
            Class<?> nmsItemStackClass = ReflectionUtils.getNMSClass("ItemStack");
            Class<?> packetPlayOutSetSlotClass = ReflectionUtils.getNMSClass("PacketPlayOutSetSlot");

            // --- 2. PREPARE THE SERVER-SIDE CONTAINER ---
            int containerId = (int) ReflectionUtils.invokeMethod(entityPlayer, "nextContainerCounter");
            Object playerInventory = ReflectionUtils.getFieldValue(entityPlayer, "inventory");

            Object world = ReflectionUtils.getFieldValue(entityPlayer, "world");
            Object fakeBlockPosition = blockPositionClass.getConstructor(int.class, int.class, int.class).newInstance(0, 0, 0);
            Method atContainerAccessMethod = containerAccessClass.getMethod("at", worldClass, blockPositionClass);
            Object containerAccess = atContainerAccessMethod.invoke(null, world, fakeBlockPosition);

            Constructor<?> anvilConstructor = containerAnvilClass.getConstructor(int.class, playerInventoryClass, containerAccessClass);
            this.container = anvilConstructor.newInstance(containerId, playerInventory, containerAccess);
            ReflectionUtils.setFieldValue(container, "checkReachable", false);

            // --- 3. THE PACKET AND SYNCHRONIZATION SEQUENCE ---
            Object anvilContainerType = ReflectionUtils.getStaticFieldValue(containersClass, "ANVIL");
            Object title = chatMessageClass.getConstructor(String.class, Object[].class).newInstance("Repair & Name", new Object[0]);

            Constructor<?> openWindowConstructor = packetPlayOutOpenWindowClass.getConstructor(int.class, containersClass, ReflectionUtils.getNMSClass("IChatBaseComponent"));
            Object openWindowPacket = openWindowConstructor.newInstance(containerId, anvilContainerType, title);
            sendPacket(playerConnection, openWindowPacket);

            ReflectionUtils.setFieldValue(entityPlayer, "activeContainer", container);
            ReflectionUtils.setFieldValue(container, "windowId", containerId);

            container.getClass().getMethod("addSlotListener", iCraftingClass).invoke(container, entityPlayer);

            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta paperMeta = paper.getItemMeta();
            paperMeta.setDisplayName(this.prefill);
            paper.setItemMeta(paperMeta);
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsPaper = asNMSCopy.invoke(null, paper);

            Object setSlotPacket = packetPlayOutSetSlotClass.getConstructor(int.class, int.class, nmsItemStackClass)
                    .newInstance(containerId, 0, nmsPaper);
            sendPacket(playerConnection, setSlotPacket);

            injectPacketListener(player, packetPlayInWindowClickClass);

        } catch (Exception e) {
            cleanup();
            throw new RuntimeException("Failed to open AnvilPrompt for player " + player.getName(), e);
        }
    }

    private void injectPacketListener(Player p, Class<?> packetClass) throws Exception {
        Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");
        Object networkManager = ReflectionUtils.getFieldValue(playerConnection, "networkManager");
        Channel channel = (Channel) ReflectionUtils.getFieldValue(networkManager, "channel");

        if (channel.pipeline().get(handlerName) != null) {
            channel.pipeline().remove(handlerName);
        }

        channel.pipeline().addBefore("packet_handler", this.handlerName, new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                if (packetClass.isInstance(packet)) {
                    int clickedSlotId = (int) ReflectionUtils.getFieldValue(packet, "slot");

                    if (clickedSlotId == 2) {
                        // Read the text field property from the server-side container.
                        String inputText = (String) ReflectionUtils.getFieldValue(container, "renameText");

                        if (inputText != null) {
                            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(this.getClass()), () -> {
                                callback.onInput(inputText);
                                cleanup();
                            });
                        }
                    }
                }
                super.channelRead(ctx, packet);
            }
        });
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

    private void sendPacket(Object playerConnection, Object packet) throws Exception {
        Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", ReflectionUtils.getNMSClass("Packet"));
        sendPacketMethod.invoke(playerConnection, packet);
    }

    private void cleanup() {
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(this.getClass()), () -> {
            try {
                Object defaultContainer = ReflectionUtils.getFieldValue(entityPlayer, "defaultContainer");
                ReflectionUtils.setFieldValue(entityPlayer, "activeContainer", defaultContainer);
                entityPlayer.getClass().getMethod("closeInventory").invoke(entityPlayer);
            } catch (Exception e) {
                // Ignore
            } finally {
                uninjectPacketListener();
                HandlerList.unregisterAll(this);
            }
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(player)) {
            cleanup();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(player)) {
            cleanup();
        }
    }

    public interface AnvilCallback {
        void onInput(String text);
    }
}