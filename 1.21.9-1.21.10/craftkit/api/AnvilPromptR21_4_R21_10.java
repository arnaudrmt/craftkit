package fr.arnaud.craftkit.api;

import fr.arnaud.craftkit.util.ReflectionUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
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

import java.lang.reflect.Field;

public class AnvilPrompt implements Listener {

    private final Player player;
    private final String prefill;
    private final AnvilCallback callback;
    private final String handlerName;

    private ServerPlayer serverPlayer;
    private AnvilMenu anvilMenu;
    private int containerId;

    public AnvilPrompt(Player player, String prefill, AnvilCallback callback) {
        this.player = player;
        this.prefill = prefill != null ? prefill : "";
        this.callback = callback;
        this.handlerName = "AnvilPrompt_" + player.getUniqueId();

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());

        if (Bukkit.isPrimaryThread()) {
            runOpenSequence(plugin);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> runOpenSequence(plugin));
        }
    }

    private void runOpenSequence(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        openAnvil();
    }

    private void openAnvil() {
        try {
            this.serverPlayer = (ServerPlayer) ReflectionUtils.getHandle(player);

            this.containerId = serverPlayer.nextContainerCounter();

            ContainerLevelAccess access = ContainerLevelAccess.create(serverPlayer.level(), serverPlayer.blockPosition());
            this.anvilMenu = new AnvilMenu(containerId, serverPlayer.getInventory(), access);

            try {
                ReflectionUtils.setFieldValue(anvilMenu, "checkReachable", false);
            } catch (Exception e) {
                // Ignore
            }

            this.anvilMenu.setTitle(Component.literal("Repair & Name"));

            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta paperMeta = paper.getItemMeta();
            paperMeta.setDisplayName(this.prefill);
            paper.setItemMeta(paperMeta);

            net.minecraft.world.item.ItemStack nmsPaper =
                    (net.minecraft.world.item.ItemStack) ReflectionUtils.asNMSCopy(paper);

            this.anvilMenu.getSlot(0).set(nmsPaper);

            Component title = Component.literal("Repair & Name");
            ClientboundOpenScreenPacket openWindowPacket = new ClientboundOpenScreenPacket(containerId, MenuType.ANVIL, title);
            serverPlayer.connection.send(openWindowPacket);

            serverPlayer.containerMenu = anvilMenu;
            serverPlayer.initMenu(anvilMenu);

            injectPacketListener();

        } catch (Exception e) {
            cleanup();
            throw new RuntimeException("Failed to open AnvilPrompt for player " + player.getName(), e);
        }
    }

    private void injectPacketListener() {
        try {
            Channel channel = getChannel(serverPlayer);

            if (channel != null) {
                if (channel.pipeline().get(handlerName) != null) {
                    channel.pipeline().remove(handlerName);
                }

                channel.pipeline().addBefore("packet_handler", this.handlerName, new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                        if (packet instanceof ServerboundContainerClickPacket) {
                            ServerboundContainerClickPacket clickPacket = (ServerboundContainerClickPacket) packet;

                            if (clickPacket.getContainerId() == containerId) {
                                if (clickPacket.getSlotNum() == 2) {
                                    String inputText = anvilMenu.itemName;
                                    if (inputText != null && !inputText.isEmpty()) {
                                        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(AnvilPrompt.this.getClass()), () -> {
                                            callback.onInput(inputText);
                                            cleanup();
                                        });
                                    }
                                }
                            }
                        }
                        super.channelRead(ctx, packet);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Channel getChannel(ServerPlayer serverPlayer) {
        try {
            Object connectionListener = serverPlayer.connection;
            Object networkManager = null;

            Class<?> clazz = connectionListener.getClass();
            while (clazz != null && networkManager == null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Connection.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        networkManager = field.get(connectionListener);
                        break;
                    }
                }
                clazz = clazz.getSuperclass();
            }

            if (networkManager == null) return null;

            Channel channel = null;
            clazz = networkManager.getClass();
            while (clazz != null && channel == null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Channel.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        channel = (Channel) field.get(networkManager);
                        break;
                    }
                }
                clazz = clazz.getSuperclass();
            }

            return channel;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void uninjectPacketListener() {
        if (serverPlayer == null) return;
        try {
            Channel channel = getChannel(serverPlayer);
            if (channel != null && channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }
        } catch (Exception ignored) {}
    }

    private void cleanup() {
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(this.getClass()), () -> {
            try {
                if (serverPlayer != null) {
                    if (anvilMenu != null) {
                        anvilMenu.getSlot(0).set(net.minecraft.world.item.ItemStack.EMPTY);
                        anvilMenu.getSlot(1).set(net.minecraft.world.item.ItemStack.EMPTY);
                    }

                    serverPlayer.closeContainer();

                    serverPlayer.containerMenu = serverPlayer.inventoryMenu;
                }
            } catch (Exception e) {
                e.printStackTrace();
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