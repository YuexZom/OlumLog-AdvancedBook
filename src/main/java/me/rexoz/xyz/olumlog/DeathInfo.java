package me.rexoz.xyz.olumlog;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DeathInfo implements Serializable {
    public final long timestamp;
    public final String world;
    public final double x, y, z;
    public final String reason;
    public final String killer;
    public final int xp;
    public final ItemStack[] items;

    public DeathInfo(long timestamp, Location loc, String reason, String killer, int xp, ItemStack[] items) {
        this.timestamp = timestamp;
        this.world = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.reason = reason;
        this.killer = killer;
        this.xp = xp;
        this.items = items;
    }

    public Location getLocation() {
        return new Location(org.bukkit.Bukkit.getWorld(world), x, y, z);
    }

    public String getFormattedDate() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
    }

    public String getCoord() {
        return (int)x + ", " + (int)y + ", " + (int)z;
    }
}
