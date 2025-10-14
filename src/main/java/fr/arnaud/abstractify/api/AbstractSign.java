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
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Handles virtual sign input using ProtocolLib and reflection.
 * This allows you to prompt a player with a sign editor UI without
 * physically placing a sign in the world. Once the player confirms,
 * their text input is returned via a callback.
 */
public class AbstractSign {

    private final Player player;
    private final SignCallback callback;

    /**
     * Creates a new sign input prompt for a player.
     *
     * @param player   Player who will see the sign editor
     * @param callback Callback to receive the player's input
     */
    public AbstractSign(Player player, SignCallback callback) {
        this.player = player;
        this.callback = callback;
        openSign();
    }

    /**
     * Opens the virtual sign editor for the player.
     * This involves:
     *  - Constructing a fake sign block at (x, 0, z)
     *  - Sending packets to the client to open the editor
     *  - Listening for the sign update packet with ProtocolLib
     */
    private void openSign() {
        try {
            // ---- 1. Get NMS player and connection ----
            Object craftPlayer = player;
            Object entityPlayer = ReflectionUtils.invokeMethod(craftPlayer, "getHandle");
            Object playerConnection = ReflectionUtils.getFieldValue(entityPlayer, "playerConnection");

            // ---- 2. Get relevant NMS classes ----
            Class<?> blockPosClass = ReflectionUtils.getNMSClass("BlockPosition");
            Class<?> blockChangeClass = ReflectionUtils.getNMSClass("PacketPlayOutBlockChange");
            Class<?> openSignClass = ReflectionUtils.getNMSClass("PacketPlayOutOpenSignEditor");
            Class<?> iBlockAccess = ReflectionUtils.getNMSClass("IBlockAccess");
            Class<?> packetClass = ReflectionUtils.getNMSClass("Packet");

            // ---- 3. Create fake sign position at player's X,Z but Y=0 ----
            Object blockPos = blockPosClass
                    .getConstructor(int.class, int.class, int.class)
                    .newInstance(player.getLocation().getBlockX(), 0, player.getLocation().getBlockZ());

            // ---- 4. Get block and block data references ----
            Object signBlock = getSignBlock();
            Object airBlock = ReflectionUtils.getNMSClass("Blocks").getField("AIR").get(null);
            Object signData = ReflectionUtils.invokeMethod(signBlock, "getBlockData");
            Object airData = ReflectionUtils.invokeMethod(airBlock, "getBlockData");

            // ---- 5. Build block change + open sign packets ----
            Object craftWorld = ReflectionUtils.getCraftClass("CraftWorld").cast(player.getWorld());
            Object worldServer = ReflectionUtils.invokeMethod(craftWorld, "getHandle");

            Object blockChangePacket = createBlockChangePacket(
                    blockChangeClass,
                    iBlockAccess,
                    blockPosClass,
                    worldServer,
                    blockPos,
                    signData
            );

            Object openSignPacket = openSignClass
                    .getConstructor(blockPosClass)
                    .newInstance(blockPos);

            // ---- 6. Send packets to client ----
            Method sendPacket = playerConnection.getClass().getMethod("sendPacket", packetClass);
            sendPacket.invoke(playerConnection, blockChangePacket);
            sendPacket.invoke(playerConnection, openSignPacket);

            // ---- 7. Listen for sign update ----
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            pm.addPacketListener(new PacketAdapter(Abstractify.getInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.UPDATE_SIGN) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (!event.getPlayer().equals(player)) return;

                    // Extract the lines written by the player
                    String[] lines = extractLines(event);
                    callback.onSignUpdate(player, lines, String.join("", lines));

                    // Replace fake sign with air to clean up clientside
                    try {
                        Field field = getBlockChangeField(blockChangeClass);
                        field.setAccessible(true);
                        field.set(blockChangePacket, airData);
                        sendPacket.invoke(playerConnection, blockChangePacket);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    // Unregister listener
                    pm.removePacketListener(this);
                }
            });

        } catch (Exception e) {
            throw new RuntimeException("Failed to open sign editor", e);
        }
    }

    /**
     * Extracts the lines of text from the sign update packet.
     * Supports both modern and legacy versions of the packet.
     */
    private String[] extractLines(PacketEvent event) {
        try {
            PacketContainer container = event.getPacket();

            // Modern: uses String array directly
            if (container.getStringArrays().size() > 0) {
                return container.getStringArrays().read(0);
            }

            // Legacy: use reflection on IChatBaseComponent[]
            Object nmsPacket = container.getHandle();
            Field bField = nmsPacket.getClass().getDeclaredField("b");
            bField.setAccessible(true);
            Object[] components = (Object[]) bField.get(nmsPacket);

            String[] lines = new String[components.length];
            Class<?> chatBase = ReflectionUtils.getNMSClass("IChatBaseComponent");
            Method getText = chatBase.getMethod("getText");

            for (int i = 0; i < components.length; i++) {
                lines[i] = (String) getText.invoke(components[i]);
            }
            return lines;

        } catch (Exception e) {
            e.printStackTrace();
            return new String[0];
        }
    }

    /**
     * Retrieves the sign block reference from the Blocks class,
     * supporting both modern ("SIGN") and legacy ("STANDING_SIGN") field names.
     */
    private Object getSignBlock() throws Exception {
        Class<?> blocksClass = ReflectionUtils.getNMSClass("Blocks");
        try {
            return blocksClass.getField("SIGN").get(null);
        } catch (NoSuchFieldException e) {
            return blocksClass.getField("STANDING_SIGN").get(null);
        }
    }

    /**
     * Creates a PacketPlayOutBlockChange instance compatible with multiple versions,
     * using different constructors depending on what exists.
     */
    private Object createBlockChangePacket(
            Class<?> packetClass,
            Class<?> iBlockAccess,
            Class<?> blockPosClass,
            Object world,
            Object blockPos,
            Object blockData
    ) throws Exception {
        Object packet;

        // Try different constructor signatures depending on MC version
        try {
            packet = packetClass.getConstructor(iBlockAccess, blockPosClass).newInstance(world, blockPos);
        } catch (NoSuchMethodException e1) {
            try {
                packet = packetClass.getConstructor(blockPosClass).newInstance(blockPos);
            } catch (NoSuchMethodException e2) {
                Constructor<?> ctor = packetClass.getConstructor();
                packet = ctor.newInstance();

                Field aField = packetClass.getDeclaredField("a");
                aField.setAccessible(true);
                aField.set(packet, blockPos);
            }
        }

        // Set block field (either "block" or "blockData")
        Field blockField;
        try {
            blockField = packetClass.getDeclaredField("block");
        } catch (NoSuchFieldException e) {
            blockField = packetClass.getDeclaredField("blockData");
        }
        blockField.setAccessible(true);
        blockField.set(packet, blockData);
        return packet;
    }

    /**
     * Determines which field represents block data in the block change packet,
     * supporting both "block" and "blockData" for cross-version compatibility.
     */
    private Field getBlockChangeField(Class<?> packetClass) throws NoSuchFieldException {
        try {
            return packetClass.getDeclaredField("block");
        } catch (NoSuchFieldException e) {
            return packetClass.getDeclaredField("blockData");
        }
    }

    /**
     * Callback interface used to return the sign input result.
     */
    public interface SignCallback {
        /**
         * Called when the player confirms the sign input.
         *
         * @param player        The player who filled the sign
         * @param lines         Array of the 4 lines entered
         * @param combinedLines All lines concatenated without separator
         */
        void onSignUpdate(Player player, String[] lines, String combinedLines);
    }
}