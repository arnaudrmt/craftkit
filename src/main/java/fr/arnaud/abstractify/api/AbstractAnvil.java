package fr.arnaud.abstractify.api;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import fr.arnaud.abstractify.Abstractify;
import fr.arnaud.abstractify.util.ReflectionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Opens a virtual Anvil GUI for a player to type text input.
 * Fully compatible with Minecraft 1.8 → 1.20+.
 */
public class AbstractAnvil {

    private final Player player;
    private final String prefill;
    private final AnvilCallback callback;
    private PacketAdapter packetListener;

    /**
     * Opens a virtual anvil for the player.
     *
     * @param player   The player
     * @param prefill  Optional pre-filled text
     * @param callback Callback when the player submits text
     */
    public AbstractAnvil(Player player, String prefill, AnvilCallback callback) {
        this.player = player;
        this.prefill = prefill != null ? prefill : "";
        this.callback = callback;
        openAnvil();
    }

    /**
     * Opens the anvil GUI and sets up packet listener for input.
     */
    private void openAnvil() {
        try {
            // ----------------------------
            // 1. Create input item (paper with pre-filled text)
            // ----------------------------
            ItemStack inputItem = new ItemStack(Material.PAPER);
            ItemMeta meta = inputItem.getItemMeta();
            meta.setDisplayName(prefill);
            inputItem.setItemMeta(meta);

            // ----------------------------
            // 2. Get NMS player and world
            // ----------------------------
            Object craftPlayer = ReflectionUtils.getCraftClass("entity.CraftPlayer").cast(player);
            Object entityPlayer = ReflectionUtils.invokeMethod(craftPlayer, "getHandle");
            Object playerInventory = ReflectionUtils.getFieldValue(entityPlayer, "inventory");
            Object world = ReflectionUtils.getFieldValue(entityPlayer, "world");

            // ----------------------------
            // 3. Get container ID
            // ----------------------------
            int containerId = (int) ReflectionUtils.invokeMethod(entityPlayer, "nextContainerCounter");

            // ----------------------------
            // 4. Create ContainerAnvil (NMS)
            // ----------------------------
            Class<?> containerAnvilClass = ReflectionUtils.getNMSClass("ContainerAnvil");
            Constructor<?> containerCtor = containerAnvilClass.getConstructor(
                    ReflectionUtils.getNMSClass("PlayerInventory"),
                    ReflectionUtils.getNMSClass("World"),
                    ReflectionUtils.getNMSClass("BlockPosition"),
                    ReflectionUtils.getNMSClass("EntityHuman")
            );

            Class<?> blockPosClass = ReflectionUtils.getNMSClass("BlockPosition");
            Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class).newInstance(0, 0, 0);

            Object containerAnvil = containerCtor.newInstance(playerInventory, world, blockPos, entityPlayer);

            // Set container to player
            ReflectionUtils.setFieldValue(entityPlayer, "activeContainer", containerAnvil);
            ReflectionUtils.setFieldValue(containerAnvil, "windowId", containerId);

            // ----------------------------
            // 5. Set input slot immediately
            // ----------------------------
            Method getSlotMethod = containerAnvil.getClass().getMethod("getSlot", int.class);
            Object slot0 = getSlotMethod.invoke(containerAnvil, 0); // input
            Object slot2 = getSlotMethod.invoke(containerAnvil, 2); // output

            Method setItemMethod = slot0.getClass().getMethod("set", ReflectionUtils.getNMSClass("ItemStack"));
            Object nmsInput = ReflectionUtils.getCraftClass("inventory.CraftItemStack")
                    .getMethod("asNMSCopy", ItemStack.class).invoke(null, inputItem);
            setItemMethod.invoke(slot0, nmsInput);

            // Dummy output to enable typing
            ItemStack dummyOutput = new ItemStack(Material.PAPER);
            ItemMeta dummyMeta = dummyOutput.getItemMeta();
            dummyMeta.setDisplayName(prefill);
            dummyOutput.setItemMeta(dummyMeta);
            Object nmsOutput = ReflectionUtils.getCraftClass("inventory.CraftItemStack")
                    .getMethod("asNMSCopy", ItemStack.class).invoke(null, dummyOutput);
            setItemMethod.invoke(slot2, nmsOutput);

            // ----------------------------
            // 6. Open container for player
            // ----------------------------
            Object title = ReflectionUtils.getNMSClass("ChatMessage")
                    .getConstructor(String.class, Object[].class)
                    .newInstance("Repair & Name", new Object[]{});

            PacketContainer openWindowPacket = new PacketContainer(PacketType.Play.Server.OPEN_WINDOW);
            openWindowPacket.getIntegers().write(0, containerId);
            openWindowPacket.getStrings().write(0, "minecraft:anvil");
            openWindowPacket.getChatComponents().write(0, com.comphenix.protocol.wrappers.WrappedChatComponent.fromHandle(title));
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, openWindowPacket);

            // ----------------------------
            // 7. Update container slots visually
            // ----------------------------
            PacketContainer setSlotPacket0 = new PacketContainer(PacketType.Play.Server.SET_SLOT);
            setSlotPacket0.getIntegers().write(0, containerId);
            setSlotPacket0.getIntegers().write(1, 0);
            setSlotPacket0.getItemModifier().write(0, inputItem);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, setSlotPacket0);

            PacketContainer setSlotPacket2 = new PacketContainer(PacketType.Play.Server.SET_SLOT);
            setSlotPacket2.getIntegers().write(0, containerId);
            setSlotPacket2.getIntegers().write(1, 2);
            setSlotPacket2.getItemModifier().write(0, dummyOutput);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, setSlotPacket2);

            // ----------------------------
            // 8. Listen for player submitting output
            // ----------------------------
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            packetListener = new PacketAdapter(Abstractify.getInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.WINDOW_CLICK) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (!event.getPlayer().equals(player)) return;

                    PacketContainer packet = event.getPacket();
                    try {
                        int slot = packet.getIntegers().read(1);

                        // Anvil output slot is 2
                        if (slot == 2) {
                            ItemStack clickedItem = packet.getItemModifier().read(0);
                            if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                                Bukkit.getScheduler().runTask(Abstractify.getInstance(), () -> {
                                    // Callback
                                    callback.onInput(player, clickedItem.getItemMeta().getDisplayName());

                                    try {
                                        // Restore default container
                                        ReflectionUtils.setFieldValue(entityPlayer, "activeContainer",
                                                ReflectionUtils.getFieldValue(entityPlayer, "defaultContainer"));
                                        player.closeInventory();
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                });
                            }
                            pm.removePacketListener(this);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        pm.removePacketListener(this);
                        player.closeInventory();
                    }
                }
            };
            pm.addPacketListener(packetListener);

            // ----------------------------
            // 9. Remove listener if player closes inventory without submitting
            // ----------------------------
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @org.bukkit.event.EventHandler
                public void onClose(InventoryCloseEvent e) {
                    if (!e.getPlayer().equals(player)) return;
                    if (packetListener != null) {
                        pm.removePacketListener(packetListener);
                    }
                    HandlerList.unregisterAll(this);
                }
            }, Abstractify.getInstance());

        } catch (Exception e) {
            throw new RuntimeException("Failed to open AbstractAnvil for player " + player.getName(), e);
        }
    }

    /**
     * Callback interface for Anvil input.
     */
    public interface AnvilCallback {
        /**
         * Called when the player submits text.
         *
         * @param player Player
         * @param text   Entered text
         */
        void onInput(Player player, String text);
    }
}