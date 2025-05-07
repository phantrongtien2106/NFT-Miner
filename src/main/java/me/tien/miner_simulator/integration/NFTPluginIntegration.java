package me.tien.miner_simulator.integration;

import com.google.gson.JsonObject;
import me.tien.miner_simulator.Miner_Simulator;
import me.tien.miner_simulator.service.NFTMetadataService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Level;

public class NFTPluginIntegration {

    private final Miner_Simulator plugin;
    private Plugin nftPluginInstance;
    private NFTMetadataService metadataService;
    private boolean nftPluginAvailable = false;

    public NFTPluginIntegration(Miner_Simulator plugin) {
        this.plugin = plugin;
        this.metadataService = new NFTMetadataService(plugin);
        initialize();
    }

    private void initialize() {
        try {
            nftPluginInstance = Bukkit.getPluginManager().getPlugin("NFTPlugin");
            if (nftPluginInstance == null || !nftPluginInstance.isEnabled()) {
                plugin.getLogger().warning("[NFTMiner] NFTPlugin không khả dụng hoặc chưa bật.");
                return;
            }

            // Kiểm tra xem NFTPlugin có sẵn sàng không
            nftPluginAvailable = true;
            plugin.getLogger().info("[NFTMiner] Đã kết nối thành công với NFTPlugin!");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[NFTMiner] Lỗi khi kết nối NFTPlugin:", e);
        }
    }

    public boolean isNFTPluginAvailable() {
        return nftPluginAvailable;
    }

    public Plugin getNFTPluginInstance() {
        return nftPluginInstance;
    }

    /**
     * Lấy metadata của một NFT cụ thể
     * @param nftKey Key của NFT
     * @return Metadata dưới dạng JsonObject, hoặc null nếu không tìm thấy
     */
    public JsonObject getNFTMetadata(String nftKey) {
        return metadataService.getMetadata(nftKey);
    }

    /**
     * Load tất cả NFT phân loại theo rarity từ thư mục metadata của NFTPlugin
     * @return Map chứa danh sách NFT theo rarity
     */
    public Map<String, List<String>> loadNFTsByRarity() {
        return metadataService.getNFTsByRarity();
    }

    /**
     * Làm mới cache metadata
     */
    public void refreshMetadataCache() {
        metadataService.refreshCache();
    }
}
