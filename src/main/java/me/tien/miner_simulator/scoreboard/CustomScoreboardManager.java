package me.tien.miner_simulator.scoreboard;

import me.tien.miner_simulator.Miner_Simulator;
import me.tien.miner_simulator.integration.MinePathIntegration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.math.BigDecimal;

public class CustomScoreboardManager {
    private final Miner_Simulator plugin;
    private final MinePathIntegration minePathIntegration;

    public CustomScoreboardManager(Miner_Simulator plugin, MinePathIntegration minePathIntegration) {
        this.plugin = plugin;
        this.minePathIntegration = minePathIntegration;
    }

    public void updateScoreboard(Player player) {
        org.bukkit.scoreboard.ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null)
            return;
    
        Scoreboard scoreboard = scoreboardManager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("NFTMiner", "dummy", ChatColor.GOLD + "NFT Miner");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    
        // Lấy số dư từ MinePathIntegration
        BigDecimal balance = minePathIntegration.getBalance(player.getUniqueId());
    
        // Thêm thông tin số dư vào Scoreboard
        Score balanceScore = objective.getScore(ChatColor.GREEN + "Số dư: " + ChatColor.WHITE + balance + " token");
        balanceScore.setScore(1);
    
        // Gán Scoreboard cho người chơi
        player.setScoreboard(scoreboard);
    }
}