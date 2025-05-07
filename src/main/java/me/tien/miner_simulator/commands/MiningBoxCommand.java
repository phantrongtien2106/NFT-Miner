package me.tien.nftminer.commands;

import me.tien.nftminer.NFTMiner;
import me.tien.nftminer.world.VoidMine;
import me.tien.nftminer.world.VoidMine.PlayerMine;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MiningBoxCommand implements CommandExecutor {

    private final NFTMiner plugin;
    private final VoidMine voidMine;

    public MiningBoxCommand(NFTMiner plugin, VoidMine voidMine) {
        this.plugin = plugin;
        this.voidMine = voidMine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Chỉ người chơi mới có thể dùng lệnh này.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("nftminer.miningbox")) {
            player.sendMessage(ChatColor.RED + "Bạn không có quyền sử dụng lệnh này.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Đang kiểm tra khu đào của bạn...");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerMine mine = voidMine.getPlayerMine(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (mine == null) {
                        player.sendMessage(ChatColor.YELLOW + "Chưa có khu đào. Đang tạo...");
                        PlayerMine newMine = voidMine.new PlayerMine(player);
                        newMine.teleportPlayer(player);
                    } else {
                        mine.createMineWorld(); // đảm bảo thế giới tồn tại
                        mine.teleportPlayer(player);
                    }
                });
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "Đã xảy ra lỗi khi đưa bạn đến khu đào.");
                    plugin.getLogger().warning("Lỗi teleport " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                });
            }
        });

        return true;
    }
}
