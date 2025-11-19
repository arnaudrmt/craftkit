package fr.arnaud.craftkit.api;

import fr.arnaud.craftkit.util.ReflectionUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.UUID;

public class SignPrompt implements Listener {

    private final Player player;
    private final SignCallback callback;
    private final String handlerName;

    private final ServerPlayer serverPlayer;
    private final BlockPos signPos;

    public SignPrompt(Player player, SignCallback callback) {
        this.player = player;
        this.callback = callback;
        this.handlerName = "SignPrompt_" + UUID.randomUUID();

        this.serverPlayer = (ServerPlayer) ReflectionUtils.getHandle(player);

        this.signPos = new BlockPos(player.getLocation().getBlockX(), 0, player.getLocation().getBlockZ());

        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getProvidingPlugin(this.getClass()));
        openSign();
    }

    private void openSign() {
        try {
            ClientboundBlockUpdatePacket blockChangePacket = new ClientboundBlockUpdatePacket(signPos, Blocks.OAK_SIGN.defaultBlockState());
            serverPlayer.connection.send(blockChangePacket);

            ClientboundOpenSignEditorPacket openSignPacket = new ClientboundOpenSignEditorPacket(signPos);
            serverPlayer.connection.send(openSignPacket);

            injectPacketListener();

        } catch (Exception e) {
            e.printStackTrace();
            cleanup();
        }
    }

    private void injectPacketListener() {
        try {
            Channel channel = getChannel(serverPlayer);

            if (channel != null) {
                if (channel.pipeline().get(handlerName) != null) {
                    channel.pipeline().remove(handlerName);
                }

                channel.pipeline().addBefore("packet_handler", handlerName, new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                        if (packet instanceof ServerboundSignUpdatePacket) {
                            ServerboundSignUpdatePacket signPacket = (ServerboundSignUpdatePacket) packet;

                            if (signPacket.getPos().equals(signPos)) {
                                String[] lines = signPacket.getLines();

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
                    // Paperweight handles remapping Connection.class to the runtime class (e.g. NetworkManager)
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

    private void resetFakeSign() {
        try {
            ClientboundBlockUpdatePacket resetPacket = new ClientboundBlockUpdatePacket(signPos, Blocks.AIR.defaultBlockState());
            serverPlayer.connection.send(resetPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void uninjectPacketListener() {
        try {
            Channel channel = getChannel(serverPlayer);
            if (channel != null && channel.pipeline().get(handlerName) != null) {
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