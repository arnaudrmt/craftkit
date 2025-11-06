package fr.arnaud.craftkit.util;

import org.bukkit.Bukkit;

import java.lang.reflect.*;

/**
 * Utility class for reflection and version handling in Minecraft server internals.
 * This class allows you to dynamically access NMS (net.minecraft.server) and CraftBukkit
 * classes, methods, and fields without hardcoding server versions.
 *
 */
public final class ReflectionUtils {

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
}