package me.rexoz.xyz.olumlog;

import io.netty.buffer.Unpooled;
import net.minecraft.server.v1_8_R3.PacketDataSerializer;
import net.minecraft.server.v1_8_R3.PacketPlayOutCustomPayload;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main extends JavaPlugin implements Listener {
    private FileConfiguration dataConfig;
    private File dataFile;
    private final Map<UUID, LinkedList<DeathInfo>> deathLogs = new HashMap<>();

    @Override
    public void onEnable() {

        dataFile = new File(getDataFolder(), "deathlogs.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            saveResource("deathlogs.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadDeathsFromConfig();


        getServer().getPluginManager().registerEvents(this, this);
        getCommand("olumlog").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player)) return false;
            showDeathBook((Player) sender);
            return true;
        });
        getServer().getMessenger().registerOutgoingPluginChannel(this, "MC|AdvancedBook");
    }

    @Override
    public void onDisable() {
        saveDeathsToConfig();
    }

    public void saveDeathsToConfig() {
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, LinkedList<DeathInfo>> entry : deathLogs.entrySet()) {
            List<Map<String, Object>> deathsList = new ArrayList<>();
            for (DeathInfo info : entry.getValue()) {
                Map<String, Object> map = new HashMap<>();
                map.put("timestamp", info.timestamp);
                map.put("world", info.world);
                map.put("x", info.x);
                map.put("y", info.y);
                map.put("z", info.z);
                map.put("reason", info.reason);
                map.put("killer", info.killer);
                map.put("xp", info.xp);
                map.put("items", info.items);
                deathsList.add(map);
            }
            config.set(entry.getKey().toString(), deathsList);
        }

        try {
            config.save(dataFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public void loadDeathsFromConfig() {
        for (String key : dataConfig.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            List<Map<String, Object>> rawList = (List<Map<String, Object>>) dataConfig.getList(key);
            LinkedList<DeathInfo> deathList = new LinkedList<>();

            if (rawList != null) {
                for (Map<String, Object> map : rawList) {
                    long timestamp = (long) map.get("timestamp");
                    String world = (String) map.get("world");
                    double x = (double) map.get("x");
                    double y = (double) map.get("y");
                    double z = (double) map.get("z");
                    String reason = (String) map.get("reason");
                    String killer = (String) map.get("killer");
                    int xp = (int) map.get("xp");
                    ItemStack[] items = ((List<ItemStack>) map.get("items")).toArray(new ItemStack[0]);

                    Location loc = new Location(getServer().getWorld(world), x, y, z);
                    deathList.add(new DeathInfo(timestamp, loc, reason, killer, xp, items));
                }
            }

            deathLogs.put(uuid, deathList);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location loc = player.getLocation();
        String reason = event.getDeathMessage();
        String killer = player.getKiller() != null ? player.getKiller().getName() : "Bilinmiyor";
        int xp = event.getDroppedExp();
        ItemStack[] armor = player.getInventory().getArmorContents();
        List<ItemStack> armorList = Arrays.asList(armor);
        Collections.reverse(armorList);
        armor = armorList.toArray(new ItemStack[0]);
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] all = new ItemStack[contents.length + armor.length];
        System.arraycopy(contents, 0, all, 0, contents.length);
        System.arraycopy(armor, 0, all, contents.length, armor.length);
        LinkedList<DeathInfo> deaths = deathLogs.computeIfAbsent(player.getUniqueId(), k -> new LinkedList<>());
        if (deaths.size() >= 20) deaths.removeLast();
        deaths.addFirst(new DeathInfo(System.currentTimeMillis(), loc, reason, killer, xp, all));
    }

    public void showDeathBook(Player player) {
        LinkedList<DeathInfo> logs = deathLogs.getOrDefault(player.getUniqueId(), new LinkedList<>());
        if (logs.isEmpty()) {
            player.sendMessage("§cÖlüm kaydın bulunamadı");
            return;
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"pages\": [");

        for (int i = 0; i < logs.size(); i += 2) {
            DeathInfo leftInfo = logs.get(i);
            DeathInfo rightInfo = (i + 1 < logs.size()) ? logs.get(i + 1) : null;

            StringBuilder left = new StringBuilder();
            StringBuilder right = new StringBuilder();

            left.append("{\"type\":\"TEXT\",\"x\":10,\"y\":10,\"lines\":[\"§dTarih: ")
                    .append(leftInfo.getFormattedDate())
                    .append("\", \"§7Koordinat: §9")
                    .append(leftInfo.getCoord())
                    .append("\", \"§7Ölüm Sebebi: §b")
                    .append(leftInfo.reason.replace("\"", ""))
                    .append("\", \"§7Öldüren Oyuncu: §c")
                    .append(leftInfo.killer)
                    .append("\", \"§7Kaybedilen XP: ")
                    .append(leftInfo.xp)
                    .append("\"],\"size\":1.0,\"shadow\":false,\"centered\":false},");

            left.append("{\"type\":\"TEXT\",\"x\":10,\"y\":62,\"lines\":[\"§cKaybedilen Eşyalar\"],\"size\":1.0},");
            int x = 10, y = 80;
            for (ItemStack item : leftInfo.items) {
                if (item == null || item.getType().name().equals("AIR")) continue;
                String nbt = ItemUtil.convertToNbt(item);
                left.append("{\"type\":\"ITEM_STACK\",\"x\":").append(x)
                        .append(",\"y\":").append(y)
                        .append(",\"itemStackCompound\":\"").append(nbt.replace("\"", "\\\""))
                        .append("\",\"size\":1.0,\"hoverable\":true},");
                x += 20;
                if (x > 160) {
                    x = 5;
                    y += 20;
                }
            }

            if (rightInfo != null) {
                right.append("{\"type\":\"TEXT\",\"x\":10,\"y\":10,\"lines\":[\"§dTarih: ")
                        .append(rightInfo.getFormattedDate())
                        .append("\", \"§7Koordinat: §9")
                        .append(rightInfo.getCoord())
                        .append("\", \"§7Ölüm Sebebi: §b")
                        .append(rightInfo.reason.replace("\"", ""))
                        .append("\", \"§7Öldüren Oyuncu: §c")
                        .append(rightInfo.killer)
                        .append("\", \"§7Kaybedilen XP: ")
                        .append(rightInfo.xp)
                        .append("\"],\"size\":1.0,\"shadow\":false,\"centered\":false},");

                right.append("{\"type\":\"TEXT\",\"x\":10,\"y\":62,\"lines\":[\"§cKaybedilen Eşyalar\"],\"size\":1.0},");
                x = 10; y = 80;
                for (ItemStack item : rightInfo.items) {
                    if (item == null || item.getType().name().equals("AIR")) continue;
                    String nbt = ItemUtil.convertToNbt(item);
                    right.append("{\"type\":\"ITEM_STACK\",\"x\":").append(x)
                            .append(",\"y\":").append(y)
                            .append(",\"itemStackCompound\":\"").append(nbt.replace("\"", "\\\""))
                            .append("\",\"size\":1.0,\"hoverable\":true},");
                    x += 20;
                    if (x > 160) {
                        x = 5;
                        y += 20;
                    }
                }
            }

            if (left.charAt(left.length() - 1) == ',') left.deleteCharAt(left.length() - 1);
            if (right.length() > 0 && right.charAt(right.length() - 1) == ',') right.deleteCharAt(right.length() - 1);

            json.append("{\"LEFT\": [").append(left).append("], \"RIGHT\": [").append(right).append("]},");
        }

        if (json.charAt(json.length() - 1) == ',') json.deleteCharAt(json.length() - 1);
        json.append("]}");

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(baos);
            gzipOut.write(json.toString().getBytes(StandardCharsets.UTF_8));
            gzipOut.close();
            byte[] compressed = baos.toByteArray();

            PacketDataSerializer serializer = new PacketDataSerializer(Unpooled.buffer());
            serializer.writeShort(compressed.length);
            serializer.writeBytes(compressed);
            PacketPlayOutCustomPayload packet = new PacketPlayOutCustomPayload("MC|AdvancedBook", serializer);
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
        } catch (Exception e) {
            player.sendMessage("§cbir hata oluştu.");
            e.printStackTrace();
        }
    }
}
