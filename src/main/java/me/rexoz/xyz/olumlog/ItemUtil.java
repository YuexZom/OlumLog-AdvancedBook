package me.rexoz.xyz.olumlog;

import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;

public class ItemUtil {
    public static String convertToNbt(org.bukkit.inventory.ItemStack item) {
        ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        NBTTagCompound compound = new NBTTagCompound();
        nmsItem.save(compound);
        return compound.toString();
    }
}
