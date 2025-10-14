package fr.arnaud.abstractify.util;

import io.netty.buffer.ByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.*;
import java.util.*;

/**
 * Utility class for reflection and version handling in Minecraft server internals.
 * This class allows you to dynamically access NMS (net.minecraft.server) and CraftBukkit
 * classes, methods, and fields without hardcoding server versions.
 *
 */
public final class ReflectionUtils {

    // Prevent instantiation
    private ReflectionUtils() {}

    /**
     * Gets a net.minecraft.server (NMS) class for the current server version.
     *
     * @param name Simple class name (e.g. "PacketPlayOutBlockChange")
     * @return The corresponding Class object
     */
    public static Class<?> getNMSClass(String name) {
        String version = getServerVersion();
        try {
            return Class.forName("net.minecraft.server." + version + "." + name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("NMS class not found: " + name, e);
        }
    }

    /**
     * Gets a CraftBukkit class for the current server version.
     *
     * @param name Simple class name (e.g. "CraftWorld")
     * @return The corresponding Class object
     */
    public static Class<?> getCraftClass(String name) {
        String version = getServerVersion();
        try {
            return Class.forName("org.bukkit.craftbukkit." + version + "." + name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("CraftBukkit class not found: " + name, e);
        }
    }

    /**
     * Returns the server version string (e.g. "v1_20_R3").
     *
     * @return Version string used in NMS/CraftBukkit packages
     */
    public static String getServerVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    /**
     * Invokes a no-parameter method on an object using reflection.
     *
     * @param instance   Target object
     * @param methodName Method name to call
     * @return Return value of the method
     */
    public static Object invokeMethod(Object instance, String methodName) {
        return invokeMethod(instance, methodName, new Class<?>[0], new Object[0]);
    }

    /**
     * Invokes a method with parameters on an object using reflection.
     *
     * @param instance    Target object
     * @param methodName  Method name to call
     * @param paramTypes  Parameter types
     * @param args        Arguments
     * @return Return value of the method
     */
    public static Object invokeMethod(Object instance, String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            Method method = instance.getClass().getMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(instance, args);
        } catch (Exception e) {
            throw new RuntimeException("Error invoking method " + methodName + " on " + instance.getClass().getName(), e);
        }
    }

    /**
     * Reads the value of a field by name using reflection.
     * This method will search for the field in the class and all its superclasses.
     *
     * @param instance  Target object
     * @param fieldName Field name
     * @return Field value
     */
    public static Object getFieldValue(Object instance, String fieldName) {
        try {
            Field field = null;
            Class<?> clazz = instance.getClass();
            // Search for the field in the class and its superclasses
            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    break; // Field found
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass(); // Check superclass
                }
            }
            if (field == null) {
                throw new NoSuchFieldException("Field " + fieldName + " not found in " + instance.getClass().getName() + " or its superclasses.");
            }
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception e) {
            throw new RuntimeException("Error reading field " + fieldName + " from " + instance.getClass().getName(), e);
        }
    }

    /**
     * Sets the value of a field by name using reflection.
     * This method will search for the field in the class and all its superclasses.
     *
     * @param instance  Target object
     * @param fieldName Field name
     * @param value     The new value to set for the field
     */
    public static void setFieldValue(Object instance, String fieldName, Object value) {
        try {
            Field field = null;
            Class<?> clazz = instance.getClass();
            // Search for the field in the class and its superclasses
            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    break; // Field found
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass(); // Check superclass
                }
            }
            if (field == null) {
                throw new NoSuchFieldException("Field " + fieldName + " not found in " + instance.getClass().getName() + " or its superclasses.");
            }
            field.setAccessible(true);
            field.set(instance, value);
        } catch (Exception e) {
            throw new RuntimeException("Error setting field " + fieldName + " on " + instance.getClass().getName(), e);
        }
    }

    /**
     * Extracts the GameProfile object from a Player instance.
     *
     * @param player Target player
     * @return The player's GameProfile object
     */
    public static Object getGameProfile(Player player) {
        try {
            Object craftPlayer = player;
            Object handle = invokeMethod(craftPlayer, "getHandle");
            return invokeMethod(handle, "getProfile");
        } catch (Exception e) {
            throw new RuntimeException("Unable to get GameProfile for player " + player.getName(), e);
        }
    }

    /**
     * Extracts the skin texture and signature from a GameProfile.
     *
     * @param gameProfile The GameProfile object
     * @return A map with keys "value" and "signature" if found
     */
    public static Map<String, String> getSkinDataFromGameProfile(Object gameProfile) {
        try {
            Map<String, String> skinData = new HashMap<>();

            // Get PropertyMap from GameProfile
            Object propertyMap = invokeMethod(gameProfile, "getProperties");

            // Call get("textures") to retrieve skin properties
            Collection<?> textures = (Collection<?>) propertyMap.getClass()
                    .getMethod("get", Object.class)
                    .invoke(propertyMap, "textures");

            if (textures != null && !textures.isEmpty()) {
                Object texture = textures.iterator().next();
                String value = (String) texture.getClass().getMethod("getValue").invoke(texture);
                String signature = (String) texture.getClass().getMethod("getSignature").invoke(texture);
                skinData.put("value", value);
                skinData.put("signature", signature);
            }

            return skinData;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract skin data from GameProfile", e);
        }
    }

    /**
     * Builds the fully qualified NMS class name string for the given simple class name.
     *
     * @param className Simple class name (e.g. "PacketPlayOutChat")
     * @return Full class name with version (e.g. "net.minecraft.server.v1_20_R3.PacketPlayOutChat")
     */
    public static String getNMSClassName(String className) {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        return "net.minecraft.server." + version + "." + className;
    }

    /**
     * Creates a new instance of PacketDataSerializer for the given Netty ByteBuf.
     *
     * @param buffer Netty ByteBuf
     * @return New PacketDataSerializer instance
     * @throws Exception if the class or constructor cannot be found
     */
    public static Object newPacketDataSerializer(ByteBuf buffer) throws Exception {
        Class<?> serializerClass = getNMSClass("PacketDataSerializer");
        return serializerClass.getConstructor(ByteBuf.class).newInstance(buffer);
    }
}