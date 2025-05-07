package me.tien.miner_simulator.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import me.tien.miner_simulator.Miner_Simulator;
import me.tien.miner_simulator.scoreboard.CustomScoreboardManager;

public class PlayerJoinListener implements Listener {
    private final Miner_Simulator plugin;
    private final CustomScoreboardManager scoreboardManager;

    public PlayerJoinListener(Miner_Simulator plugin, CustomScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.scoreboardManager = scoreboardManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Cập nhật Scoreboard cho người chơi khi họ tham gia
        scoreboardManager.updateScoreboard(event.getPlayer());
    }
}