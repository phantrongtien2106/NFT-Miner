package me.tien.miner_simulator;

import me.tien.miner_simulator.commands.*;
import me.tien.miner_simulator.listeners.MiningListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import me.tien.miner_simulator.gui.ShopGUI;
import me.tien.miner_simulator.integration.MinePathIntegration;
import me.tien.miner_simulator.integration.NFTPluginIntegration;
import me.tien.miner_simulator.listeners.InventoryListener;
import me.tien.miner_simulator.listeners.PlayerListener;
import me.tien.miner_simulator.listeners.ShopListener;
import me.tien.miner_simulator.scoreboard.CustomScoreboardManager;
import me.tien.miner_simulator.token.TokenManager;
import me.tien.miner_simulator.upgrade.InventoryUpgrade;
import me.tien.miner_simulator.upgrade.SpeedUpgrade;
import me.tien.miner_simulator.upgrade.TokenValueUpgrade;
import me.tien.miner_simulator.upgrade.UpgradeManager;
import me.tien.miner_simulator.world.VoidMine;

public class Miner_Simulator extends JavaPlugin {
    private VoidMine voidMine;
    private NFTPluginIntegration nftIntegration;
    private MinePathIntegration minePathIntegration;
    private TokenManager tokenManager;
    private UpgradeManager upgradeManager;
    private InventoryUpgrade inventoryUpgrade;
    private ShopGUI shopGUI;
    private SpeedUpgrade speedUpgrade;
    private CustomScoreboardManager scoreboardManager;
    private TokenValueUpgrade tokenValueUpgrade;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
        // Khởi tạo tích hợp NFTPlugin
        nftIntegration = new NFTPluginIntegration(this);
        // Khởi tạo VoidMine
        voidMine = new VoidMine(this);
        // 1) Tạo trước TokenManager tạm (không dùng upgradeManager)
        tokenManager = new TokenManager(this);
        getLogger().info("TokenManager initialized successfully");
        // 2) Tạo UpgradeManager, truyền vào TokenManager
        upgradeManager = new UpgradeManager(this, tokenManager);
        getLogger().info("UpgradeManager initialized successfully");
        // 3) Giờ TokenManager có thể lấy được tokenValueUpgrade
        tokenManager.setTokenValueUpgrade(upgradeManager.getTokenValueUpgrade());
        tokenValueUpgrade = upgradeManager.getTokenValueUpgrade();
        // Khởi tạo GUI
        shopGUI = new ShopGUI(this, tokenManager, upgradeManager);
        inventoryUpgrade = new InventoryUpgrade(this, tokenManager);
        speedUpgrade = upgradeManager.getSpeedUpgrade();
        // Lưu config mặc định
        saveDefaultConfig();
        // Đăng ký lệnh
        getCommand("claim").setExecutor(new ClaimCommand(this, tokenManager,tokenValueUpgrade));
        getCommand("token").setExecutor(new TokenCommand(this, tokenManager));
        getCommand("shop").setExecutor(new ShopCommand(this, shopGUI));
        getCommand("miningbox").setExecutor(new MiningBoxCommand(this, voidMine));
        getCommand("resetmine").setExecutor(new ResetMineCommand(voidMine));
        getCommand("resetupgrades").setExecutor(new ResetUpgradeCommand(this, upgradeManager));
        getCommand("help").setExecutor(new HelpCommand(this));
        // Đăng ký listener
        getServer().getPluginManager().registerEvents(new ShopListener(shopGUI), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(upgradeManager), this);
        getServer().getPluginManager().registerEvents(new MiningListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this, inventoryUpgrade), this);
        if (nftIntegration.isNFTPluginAvailable()) {
            getLogger().info("Tìm thấy NFTPlugin - Chức năng NFT đã được kích hoạt!");
        } else {
            getLogger().warning("Không tìm thấy NFTPlugin - Chức năng NFT bị vô hiệu hóa!");
        }
        // Khởi tạo tích hợp SolanaCoin
            Plugin minePath = getServer().getPluginManager().getPlugin("MinePath");
            if (minePath != null && minePath.isEnabled()) {
                getLogger().info("MinePath đã sẵn sàng!");
                this.minePathIntegration = new MinePathIntegration(this);
                if (minePathIntegration.isMinePathAvailable()) {
                    scoreboardManager = new CustomScoreboardManager(this, minePathIntegration);
                    getLogger().info("Đã kết nối thành công với MinePath!");

                } else {
                    getLogger().warning("Không thể kết nối với MinePath.");
                }
            } else {
                getLogger().warning("MinePath chưa sẵn sàng!");
            }
        }, 40L); // chờ 1 giây (20 ticks) sau khi server load
    }

    /**
     * Lấy NFT Integration
     */
    public NFTPluginIntegration getNFTIntegration() {
        return nftIntegration;
    }

    /**
     * Lấy SolanaCoin Integration
     */
    public MinePathIntegration getMinePathIntegration() {
        return minePathIntegration;
    }
    /**
     * Lấy VoidMine
     */
    public VoidMine getVoidMine() {
        return voidMine;
    }
    /** Thêm getter này: */
    public UpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    public CustomScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}