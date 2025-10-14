package fr.arnaud.abstractify.api;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for easier ItemStack creation and customization.
 * Supports lore, enchantments, skull textures, flags, and more.
 */
public class AbstractItemStack {

    private final ItemStack itemStack;
    private final ItemMeta itemMeta;
    private final SkullMeta skullMeta;
    private final boolean isSkull;

    public AbstractItemStack(Material material, String displayName) {
        this(material, displayName, 1);
    }

    public AbstractItemStack(Material material, String displayName, int amount) {
        this.itemStack = new ItemStack(material, amount);
        this.isSkull = material == Material.SKULL_ITEM;

        if (isSkull) {
            this.skullMeta = (SkullMeta) itemStack.getItemMeta();
            this.itemMeta = null;
            skullMeta.setDisplayName(displayName);
        } else {
            this.itemMeta = itemStack.getItemMeta();
            this.skullMeta = null;
            itemMeta.setDisplayName(displayName);
        }
    }

    public AbstractItemStack setUnbreakable() {
        (isSkull ? skullMeta : itemMeta).spigot().setUnbreakable(true);
        return this;
    }

    public AbstractItemStack setDisplayName(String name) {
        (isSkull ? skullMeta : itemMeta).setDisplayName(name);
        return this;
    }

    public AbstractItemStack setLore(String... lore) {
        (isSkull ? skullMeta : itemMeta).setLore(Arrays.asList(lore));
        return this;
    }

    public AbstractItemStack setLore(List<String> lore) {
        (isSkull ? skullMeta : itemMeta).setLore(lore);
        return this;
    }

    public AbstractItemStack addLore(String line) {
        List<String> lore = (isSkull ? skullMeta : itemMeta).getLore();
        lore.add(line);
        (isSkull ? skullMeta : itemMeta).setLore(lore);
        return this;
    }

    public AbstractItemStack setOwner(Player owner) {
        if (isSkull) {
            itemStack.setDurability((short) SkullType.PLAYER.ordinal());
            skullMeta.setOwner(owner.getName());
        }
        return this;
    }

    public AbstractItemStack addFlag(ItemFlag flag) {
        (isSkull ? skullMeta : itemMeta).addItemFlags(flag);
        return this;
    }

    public AbstractItemStack setDurability(short durability) {
        itemStack.setDurability(durability);
        return this;
    }

    public AbstractItemStack setTexture(String texture) {
        if (!isSkull) return this;

        itemStack.setDurability((short) SkullType.PLAYER.ordinal());

        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", texture));

        try {
            Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullMeta, profile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return this;
    }

    public AbstractItemStack addEnchantment(Enchantment enchantment, int level) {
        (isSkull ? skullMeta : itemMeta).addEnchant(enchantment, level, true);
        return this;
    }

    public ItemStack build() {
        itemStack.setItemMeta(isSkull ? skullMeta : itemMeta);
        return itemStack;
    }
}