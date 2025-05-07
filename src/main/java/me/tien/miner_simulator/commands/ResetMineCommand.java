package me.tien.miner_simulator.commands;

import me.tien.miner_simulator.world.VoidMine;
import me.tien.miner_simulator.world.VoidMine.PlayerMine;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetMineCommand implements CommandExecutor {

    private final VoidMine voidMine;

    public ResetMineCommand(VoidMine voidMine) {
        this.voidMine = voidMine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Chỉ người chơi mới có thể dùng lệnh này.");
            return true;
        }
        Player player = (Player) sender;
        PlayerMine mine = voidMine.getPlayerMine(player);
        if (mine != null) {
            mine.resetMiningBox();
            mine.teleportPlayer(player);
            player.sendMessage(ChatColor.GREEN + "Khu đào của bạn đã được reset và bạn đã được dịch chuyển về điểm spawn!");
        } else {
            player.sendMessage(ChatColor.RED + "Bạn chưa có khu đào để reset!");
        }
        return true;
    }
}