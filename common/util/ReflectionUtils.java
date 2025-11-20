package fr.arnaud.craftkit.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.*;

/**
 * Utility class for reflection and version handling in Minecraft server internals.
 * This class allows you to dynamically access NMS (net.minecraft.server) and CraftBukkit
 * classes, methods, and fields without hardcoding server versions.
 *
 */
public final class ReflectionUtils {

    // Cache the version string to improve performance
    private static final String VERSION = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

    /**
     * Gets a net.minecraft.server (NMS) class for the current server version.
     * @param name Simple class name (e.g. "PacketPlayOutBlockChange") for 1.16-, or full path for 1.17+
     * @return The corresponding Class object
     */
    public static Class<?> getNMSClass(String name) {
        try {
            // If the name contains a dot, treat it as a full path (1.17+ support)
            if (name.contains(".")) {
                return Class.forName(name);
            }
            // Legacy support (1.8 - 1.16.5)
            return Class.forName("net.minecraft.server." + VERSION + "." + name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("NMS class not found: " + name, e);
        }
    }

    /**
     * Gets a CraftBukkit class for the current server version.
     *
     * @param name Simple class name (e.g. "CraftWorld" or "entity.CraftPlayer")
     * @return The corresponding Class object
     */
    public static Class<?> getCraftClass(String name) {
        try {
            return Class.forName("org.bukkit.craftbukkit." + VERSION + "." + name);
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
        return VERSION;
    }

    /**
     * Invokes a no-parameter method on an object using reflection.
     *
     * @param instance   Target object
     * @param methodName Method name to call
     * @return Return value of the method
     */
    public static Object invokeMethod(Object instance, String methodName) {
        try {
            Method method = instance.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(instance);
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

            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
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

            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
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
     * Reads the value of a static field by name from a class.
     *
     * @param clazz     The class containing the static field.
     * @param fieldName The name of the static field to read.
     * @return The value of the static field.
     */
    public static Object getStaticFieldValue(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Error reading static field " + fieldName + " from " + clazz.getName(), e);
        }
    }

    /**
     * Gets the NMS handle (EntityPlayer/ServerPlayer) from a Bukkit Player.
     *
     * @param player The Bukkit Player instance.
     * @return The NMS player object.
     */
    public static Object getHandle(Player player) {
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            getHandle.setAccessible(true);
            return getHandle.invoke(player);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get NMS handle from player", e);
        }
    }

    /**
     * Converts a Bukkit ItemStack to an NMS ItemStack (copy).
     *
     * @param item The Bukkit ItemStack.
     * @return The NMS ItemStack object.
     */
    public static Object asNMSCopy(ItemStack item) {
        try {
            Class<?> craftItemStack = getCraftClass("inventory.CraftItemStack");
            Method asNMSCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            asNMSCopy.setAccessible(true);
            return asNMSCopy.invoke(null, item);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert ItemStack to NMS", e);
        }
    }
}