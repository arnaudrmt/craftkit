package fr.arnaud.craftkit.api;

import fr.arnaud.craftkit.util.ReflectionUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
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

        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getProvidingPlugin(this.getClass()));
        openAnvil();
    }

    private void openAnvil() {
        try {
            this.serverPlayer = (ServerPlayer) ReflectionUtils.getHandle(player);

            this.containerId = serverPlayer.nextContainerCounter();

            ContainerLevelAccess access = ContainerLevelAccess.create(serverPlayer.level, serverPlayer.blockPosition());
            this.anvilMenu = new AnvilMenu(containerId, serverPlayer.getInventory(), access);
            this.anvilMenu.checkReachable = false;
            this.anvilMenu.setTitle(new TextComponent("Repair & Name"));

            Component title = new TextComponent("Repair & Name");
            ClientboundOpenScreenPacket openWindowPacket = new ClientboundOpenScreenPacket(containerId, MenuType.ANVIL, title);
            serverPlayer.connection.send(openWindowPacket);

            serverPlayer.containerMenu = anvilMenu;
            serverPlayer.initMenu(anvilMenu);

            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta paperMeta = paper.getItemMeta();
            paperMeta.setDisplayName(this.prefill);
            paper.setItemMeta(paperMeta);

            net.minecraft.world.item.ItemStack nmsPaper =
                    (net.minecraft.world.item.ItemStack) ReflectionUtils.asNMSCopy(paper);

            ClientboundContainerSetSlotPacket setSlotPacket = new ClientboundContainerSetSlotPacket(containerId, 0, 0, nmsPaper);
            serverPlayer.connection.send(setSlotPacket);

            injectPacketListener();

        } catch (Exception e) {
            cleanup();
            throw new RuntimeException("Failed to open AnvilPrompt for player " + player.getName(), e);
        }
    }

    private void injectPacketListener() {
        Channel channel = serverPlayer.connection.connection.channel;

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

    private void uninjectPacketListener() {
        if (serverPlayer == null) return;
        try {
            Channel channel = serverPlayer.connection.connection.channel;
            if (channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }
        } catch (Exception ignored) {}
    }

    private void cleanup() {
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(this.getClass()), () -> {
            try {
                if (serverPlayer != null) {
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