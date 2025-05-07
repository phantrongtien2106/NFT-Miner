package me.tien.nftminer.listeners;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import me.tien.nftminer.NFTMiner;
import me.tien.nftminer.upgrade.InventoryUpgrade;

public class InventoryListener implements Listener {

    private final NFTMiner plugin;
    private final InventoryUpgrade inventoryUpgrade;

    public InventoryListener(NFTMiner plugin, InventoryUpgrade inventoryUpgrade) {
        this.plugin = plugin;
        this.inventoryUpgrade = inventoryUpgrade;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Tải dữ liệu người chơi khi họ đăng nhập
        inventoryUpgrade.loadPlayerData(player);

        // Áp dụng hiệu ứng khóa inventory
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            inventoryUpgrade.applyEffect(player);
        }, 10L); // Trễ nửa giây để đảm bảo inventory đã sẵn sàng
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Lưu dữ liệu khi người chơi thoát
        inventoryUpgrade.saveData();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        List<Integer> lockedSlots = inventoryUpgrade.getLockedSlots(player);

        // Nếu click vào inventory của người chơi
        if (event.getClickedInventory() == player.getInventory()) {
            int clickedSlot = event.getSlot();

            // Kiểm tra slot có bị khóa không
            if (lockedSlots.contains(clickedSlot)) {
                event.setCancelled(true);
                player.sendMessage("§c§lThông báo: §r§cÔ này đang bị khóa. Mở khóa tại §f/shop");

                // Nếu đó là ô barrier, mở shop nâng cấp
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && inventoryUpgrade.isLockBarrier(clickedItem)) {
                    // Có thể gọi lệnh mở shop ở đây
                    // player.performCommand("shop upgrade");
                }
            }
        }

        // Ngăn chặn các thao tác khác có thể ảnh hưởng đến ô khóa
        if (event.getAction().name().contains("PLACE") ||
                event.getAction().name().contains("HOTBAR_SWAP")) {

            int slot = event.getSlot();
            // Cho hotbar swap, kiểm tra hotbar slot có bị khóa không
            if (event.getAction().name().contains("HOTBAR_SWAP")) {
                int hotbarButton = event.getHotbarButton();
                if (hotbarButton != -1 && lockedSlots.contains(hotbarButton)) {
                    event.setCancelled(true);
                    player.sendMessage("§c§lThông báo: §r§cKhông thể sử dụng ô đã bị khóa.");
                    return;
                }
            }

            if (event.getClickedInventory() == player.getInventory() && lockedSlots.contains(slot)) {
                event.setCancelled(true);
                player.sendMessage("§c§lThông báo: §r§cKhông thể đặt vật phẩm vào ô bị khóa.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        List<Integer> lockedSlots = inventoryUpgrade.getLockedSlots(player);

        // Kiểm tra nếu bất kỳ slot nào bị ảnh hưởng là slot bị khóa
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot < player.getInventory().getSize()) { // Đảm bảo đây là slot trong inventory người chơi
                int slot = rawSlot;
                if (lockedSlots.contains(slot)) {
                    event.setCancelled(true);
                    player.sendMessage("§c§lThông báo: §r§cKhông thể đặt vật phẩm vào ô bị khóa.");
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        List<Integer> lockedSlots = inventoryUpgrade.getLockedSlots(player);
        ItemStack pickupItem = event.getItem().getItemStack();

        // Kiểm tra nếu inventory đầy (chỉ xét các ô không bị khóa)
        boolean canPickup = false;

        // Kiểm tra xem có thể stack với các item hiện có không
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (!lockedSlots.contains(i)) {
                ItemStack existingItem = player.getInventory().getItem(i);
                if (existingItem == null) {
                    canPickup = true;
                    break;
                } else if (existingItem.isSimilar(pickupItem) &&
                        existingItem.getAmount() < existingItem.getMaxStackSize()) {
                    canPickup = true;
                    break;
                }
            }
        }

        if (!canPickup) {
            event.setCancelled(true);
            player.sendMessage("§c§lThông báo: §r§cKhông đủ chỗ trống trong inventory. Mở khóa thêm tại §f/shop");
        }
    }
}