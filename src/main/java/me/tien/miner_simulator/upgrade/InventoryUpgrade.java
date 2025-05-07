package me.tien.nftminer.upgrade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.tien.nftminer.NFTMiner;
import me.tien.nftminer.token.TokenManager;

public class InventoryUpgrade implements Upgrade {
    private final Map<UUID, Integer> playerLevels = new HashMap<>();
    private final Map<Integer, Integer> levelCosts = new HashMap<>();
    private final NFTMiner plugin;
    private final TokenManager tokenManager;

    public InventoryUpgrade(NFTMiner plugin, TokenManager tokenManager) {
        this.plugin = plugin;
        this.tokenManager = tokenManager;
        loadConfig();
    }

    @Override
    public void loadConfig() {
        // Load chi phí cho các level từ config
        levelCosts.put(1, plugin.getConfig().getInt("upgrade.inventory.level1", 100));
        levelCosts.put(2, plugin.getConfig().getInt("upgrade.inventory.level2", 200));
        levelCosts.put(3, plugin.getConfig().getInt("upgrade.inventory.level3", 300));
    }

    @Override
    public int getLevel(UUID uuid) {
        return playerLevels.getOrDefault(uuid, 0);
    }

    @Override
    public int getLevel(Player player) {
        return getLevel(player.getUniqueId());
    }

    @Override
    public void setLevel(UUID uuid, int level) {
        playerLevels.put(uuid, Math.min(level, getMaxLevel()));
    }

    @Override
    public void setLevel(Player player, int level) {
        setLevel(player.getUniqueId(), level);
    }

    @Override
    public int getNextLevelCost(Player player) {
        int currentLevel = getLevel(player);
        return levelCosts.getOrDefault(currentLevel + 1, 0);
    }

    @Override
    public int getMaxLevel() {
        return 3; // Tối đa 3 hàng
    }

    @Override
    public String getType() {
        return "InventoryUpgrade";
    }

    @Override
    public void saveData() {
        // Lưu dữ liệu người chơi vào file
        for (Map.Entry<UUID, Integer> entry : playerLevels.entrySet()) {
            String path = "players." + entry.getKey() + ".inventory-level";
            plugin.getConfig().set(path, entry.getValue());
        }
        plugin.saveConfig();
    }

    @Override
    public void applyEffect(Player player) {
        List<Integer> lockedSlots = getLockedSlots(player);

        // Xoá các item ở các slot đã được mở khóa (nếu có)
        for (int i = 9; i <= 35; i++) {
            if (!lockedSlots.contains(i)) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && isLockBarrier(item)) {
                    player.getInventory().setItem(i, null);
                }
            }
        }

        // Đặt kính bị khóa ở các ô vẫn còn bị khóa
        for (int slot : lockedSlots) {
            player.getInventory().setItem(slot, createLockBarrier());
        }
    }

    @Override
    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        String path = "players." + uuid + ".inventory-level";
        if (plugin.getConfig().contains(path)) {
            int level = plugin.getConfig().getInt(path);
            playerLevels.put(uuid, level);
        } else {
            playerLevels.put(uuid, 0);
        }
    }

    @Override
    public int getEffectLevel(int level) {
        // Số hàng mở khóa tương ứng với level
        return level;
    }

    public List<Integer> getLockedSlots(Player player) {
        List<Integer> lockedSlots = new ArrayList<>();
        int level = getLevel(player);

        // Hàng đầu tiên (0-8) là hàng trang bị (hotbar), không khóa
        // Khóa hàng thứ 2 (9-17) nếu level < 1
        // Khóa hàng thứ 3 (18-26) nếu level < 2
        // Khóa hàng cuối (27-35) nếu level < 3

        if (level < 1) {
            for (int i = 9; i <= 17; i++) {
                lockedSlots.add(i);
            }
        }

        if (level < 2) {
            for (int i = 18; i <= 26; i++) {
                lockedSlots.add(i);
            }
        }

        if (level < 3) {
            for (int i = 27; i <= 35; i++) {
                lockedSlots.add(i);
            }
        }

        return lockedSlots;
    }

    public boolean upgrade(Player player) {
        int currentLevel = getLevel(player);
        if (currentLevel >= getMaxLevel()) {
            player.sendMessage("§c§lThông báo: §r§cBạn đã mở khóa tối đa các hàng inventory!");
            return false;
        }

        int cost = getNextLevelCost(player);
        if (tokenManager.hasTokens(player, cost)) {
            tokenManager.removeTokens(player, BigDecimal.valueOf(cost));
            setLevel(player, currentLevel + 1);
            player.sendMessage("§a§lThành công: §r§aĐã mở khóa thêm một hàng inventory!");

            // Cập nhật inventory để loại bỏ các ô đã mở khóa
            applyEffect(player);

            return true;
        } else {
            player.sendMessage(
                    "§c§lThông báo: §r§cBạn không đủ token để mở khóa hàng tiếp theo. Cần: " + cost + " token.");
            return false;
        }
    }

    private ItemStack createLockBarrier() {
        ItemStack barrier = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = barrier.getItemMeta();
        meta.setDisplayName("§c§lƯu Đãi Khoá Inventory");

        List<String> lore = new ArrayList<>();
        lore.add("§7Ô này đang bị khóa.");
        lore.add("§7Mở khóa tại §f/shop");
        lore.add("");
        lore.add("§eClick vào đây để mở shop nâng cấp.");

        meta.setLore(lore);
        barrier.setItemMeta(meta);

        return barrier;
    }

    public boolean isLockBarrier(ItemStack item) {
        if (item == null || item.getType() != Material.RED_STAINED_GLASS_PANE) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        return meta.getDisplayName().equals("§c§lƯu Đãi Khoá Inventory");
    }
}