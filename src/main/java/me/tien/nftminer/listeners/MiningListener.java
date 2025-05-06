package me.tien.nftminer.listeners;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.metadata.MetadataValue;
import me.tien.nftminer.NFTMiner;
import me.tien.nftminer.integration.NFTPluginIntegration;
import me.tien.nftminer.world.VoidMine;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MiningListener implements Listener {

    private final NFTMiner plugin;
    private final Random random = new Random();
    private final NFTPluginIntegration nftIntegration;

    private final Map<String, List<String>> nftsByRarity = new HashMap<>();
    private final Map<String, Double> rarityDropRates = new HashMap<>();
    private final Map<String, ChatColor> rarityColors = new HashMap<>();
    private final String[] rarityOrder = { "legendary", "epic", "rare", "uncommon", "common" };

    private double baseDropChance = 0.05;
    private int cooldownSeconds = 3;
    private final Map<UUID, Long> lastDropTime = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> nftRates = new HashMap<>();

    // Cấu hình buff
    private boolean enableBuffs = true;
    private double maxLuckBuff = 0.20; // Tối đa 20% buff


    public MiningListener(NFTMiner plugin) {
        this.plugin = plugin;
        this.nftIntegration = plugin.getNFTIntegration();

        setupRarityColors();
        loadConfig();
        loadNFTsByRarity();
        loadRates(); // 👈 THÊM DÒNG NÀY
    }

    private void setupRarityColors() {
        rarityColors.put("legendary", ChatColor.GOLD);
        rarityColors.put("epic", ChatColor.LIGHT_PURPLE);
        rarityColors.put("rare", ChatColor.BLUE);
        rarityColors.put("uncommon", ChatColor.GREEN);
        rarityColors.put("common", ChatColor.WHITE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        plugin.getLogger().info("[DEBUG] Đào block " + block.getType() + " tại " + block.getLocation() + " bởi " + player.getName());
        plugin.getLogger().info("[TEST] BlockBreakEvent by: " + player.getName() + ", block: " + block.getType());

        // BỎ QUA TẤT CẢ CÁC ĐIỀU KIỆN - CHỈ ĐỂ TEST
        plugin.getLogger().info("[DEBUG] Gọi handleNFTDrop cho player: " + player.getName() + " (BỎ QUA ĐIỀU KIỆN)");
        handleNFTDrop(player);

        // Phần code gốc - đã bị comment để test
        /*
        VoidMine voidMine = plugin.getVoidMine();
        if (voidMine == null) {
            plugin.getLogger().warning("[DEBUG] VoidMine là null");
            return;
        }

        if (!voidMine.isMineWorld(block.getWorld().getName())) {
            plugin.getLogger().warning("[DEBUG] Block không ở trong thế giới mine: " + block.getWorld().getName());
            return;
        }

        VoidMine.PlayerMine playerMine = voidMine.getMineByWorldName(block.getWorld().getName());
        if (playerMine == null) {
            plugin.getLogger().warning("[DEBUG] PlayerMine là null cho thế giới: " + block.getWorld().getName());
            return;
        }

        if (!playerMine.isInMiningBox(block.getLocation())) {
            plugin.getLogger().warning("[DEBUG] Block không ở trong mining box: " + block.getLocation());
            return;
        }

        if (!isMineableMaterial(block.getType())) {
            plugin.getLogger().warning("[DEBUG] Block không phải là loại có thể đào được: " + block.getType());
            return;
        }

        // Tất cả điều kiện đã được đáp ứng, gọi handleNFTDrop
        plugin.getLogger().info("[DEBUG] Gọi handleNFTDrop cho player: " + player.getName());
        handleNFTDrop(player);
        voidMine.checkAndResetMineIfEmpty(player, block.getLocation());
        */
    }

    private boolean isMineableMaterial(Material material) {
        return material == Material.STONE || material == Material.COBBLESTONE
                || material == Material.COAL_ORE || material == Material.IRON_ORE
                || material == Material.GOLD_ORE || material == Material.DIAMOND_ORE
                || material == Material.EMERALD_ORE || material == Material.LAPIS_ORE
                || material == Material.REDSTONE_ORE;
    }

    private void handleNFTDrop(Player player) {
        long now = System.currentTimeMillis();
        long lastDrop = lastDropTime.getOrDefault(player.getUniqueId(), 0L);
        long wait = cooldownSeconds * 1000L;

        plugin.getLogger().info("[TEST] Checking NFT drop for: " + player.getName());

        if (now - lastDrop < wait) {
            plugin.getLogger().info("[TEST] Cooldown active: " + (wait - (now - lastDrop)) + "ms remaining");
            return;
        }

        // Áp dụng buff luck từ NFT-Plugin nếu có
        double adjustedDropChance = getAdjustedDropChance(player);

        double rollDrop = random.nextDouble();
        plugin.getLogger().info("[TEST] Rolled baseDropChance: " + rollDrop + " vs " + adjustedDropChance +
                " (base: " + baseDropChance + ", buff: " + (adjustedDropChance - baseDropChance) + ")");

        if (rollDrop > adjustedDropChance) {
            plugin.getLogger().info("[TEST] Roll failed – no NFT this time.");
            return;
        }

        // chọn rarity
        double rarityRoll = random.nextDouble();
        plugin.getLogger().info("[TEST] Rolled rarity chance: " + rarityRoll);

        // Tìm rarity phù hợp
        String tempSelectedRarity = null;
        double cumulativeChance = 0.0;
        for (String rarity : rarityOrder) {
            cumulativeChance += rarityDropRates.getOrDefault(rarity, 0.0) / 100.0; // vì config là %, chuyển về 0.x
            if (rarityRoll <= cumulativeChance) {
                tempSelectedRarity = rarity;
                break;
            }
        }

        if (tempSelectedRarity == null) {
            plugin.getLogger().warning("[TEST] Không tìm thấy rarity phù hợp.");
            return;
        }

        // Sử dụng biến final để có thể sử dụng trong lambda
        final String selectedRarity = tempSelectedRarity;

        plugin.getLogger().info("[TEST] Selected rarity: " + selectedRarity);

        List<String> nftList = nftsByRarity.get(selectedRarity);
        if (nftList == null || nftList.isEmpty()) {
            plugin.getLogger().warning("[TEST] Không có NFT nào cho rarity: " + selectedRarity);
            return;
        }

        // Chọn NFT dựa trên tỉ lệ trong config
        final String selectedNFT = selectNFTByRates(selectedRarity) != null ?
            selectNFTByRates(selectedRarity) :
            nftList.get(random.nextInt(nftList.size()));

        plugin.getLogger().info("[TEST] Selected NFT ID: " + selectedNFT);

        // Sử dụng cách tiếp cận giống như LootBox: gọi lệnh mintnft với quyền OP
        plugin.getLogger().info("[NFTMiner] Mint NFT cho người chơi " + player.getName() + ": " + selectedNFT);

        // Gửi thông báo cho người chơi
        player.sendMessage(ChatColor.YELLOW + "NFT đang được mint... Vui lòng chờ thông báo hoàn thành!");

        // Sử dụng quyền op tạm thời để mint NFT
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean wasOp = player.isOp();
            try {
                // Ghi log
                plugin.getLogger().info("Minting NFT cho người chơi " + player.getName() + ": " + selectedNFT);

                // Cấp quyền op tạm thời
                if (!wasOp) {
                    player.setOp(true);
                }

                // Thực thi lệnh với quyền op
                String command = "mintnft " + player.getName() + " " + selectedNFT;
                plugin.getLogger().info("[NFTMiner] Thực hiện lệnh: " + command);

                boolean success = player.performCommand(command);

                if (!success) {
                    plugin.getLogger().severe("[NFTMiner] Không thể thực hiện lệnh mintnft với quyền của người chơi");
                    player.sendMessage(ChatColor.RED + "Có lỗi xảy ra khi mint NFT. Vui lòng thử lại sau.");
                }

                // Không cần gửi thông báo thêm vì lệnh /mintnft đã gửi thông báo rồi
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Không thể mint NFT: " + e.getMessage());
                plugin.getLogger().severe("Lỗi khi mint NFT: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Khôi phục trạng thái op
                if (!wasOp) {
                    player.setOp(false);
                }
            }
        }, 10L); // Chờ 0.5 giây để đảm bảo thông báo được hiển thị trước

        lastDropTime.put(player.getUniqueId(), now);
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Đọc cài đặt từ phần nft-drop trong config.yml
        ConfigurationSection nftDropSection = config.getConfigurationSection("nft-drop");

        if (nftDropSection != null) {
            // Đọc base-drop-chance
            baseDropChance = nftDropSection.getDouble("base-drop-chance", 0.05);

            // Đọc cooldown-seconds
            cooldownSeconds = nftDropSection.getInt("cooldown-seconds", 3);

            plugin.getLogger().info("[NFTMiner] base-drop-chance = " + baseDropChance);
            plugin.getLogger().info("[NFTMiner] cooldown-seconds = " + cooldownSeconds + "s");

            // Đọc rarity-drop-rates
            rarityDropRates.clear();
            ConfigurationSection raritySection = nftDropSection.getConfigurationSection("rarity-drop-rates");

            if (raritySection != null) {
                for (String rarity : raritySection.getKeys(false)) {
                    double chance = raritySection.getDouble(rarity);
                    rarityDropRates.put(rarity.toLowerCase(), chance);

                    plugin.getLogger().info("[NFTMiner] rarity " + rarity.toUpperCase() + " = " + chance + "%");
                }
            } else {
                plugin.getLogger().warning("[NFTMiner] Không tìm thấy phần 'rarity-drop-rates' trong config. Dùng mặc định.");
                // Sử dụng giá trị mặc định
                rarityDropRates.put("common", 5.0);
                rarityDropRates.put("uncommon", 2.0);
                rarityDropRates.put("rare", 1.0);
                rarityDropRates.put("epic", 0.5);
                rarityDropRates.put("legendary", 0.1);
            }

            // Đọc cài đặt buff
            ConfigurationSection buffSection = nftDropSection.getConfigurationSection("buffs");
            if (buffSection != null) {
                enableBuffs = buffSection.getBoolean("enabled", true);
                maxLuckBuff = buffSection.getDouble("max-luck-buff", 0.20);

                plugin.getLogger().info("[NFTMiner] Buffs enabled: " + enableBuffs);
                plugin.getLogger().info("[NFTMiner] Max luck buff: " + (maxLuckBuff * 100) + "%");
            } else {
                // Thêm cài đặt buff mặc định vào config
                try {
                    nftDropSection.set("buffs.enabled", true);
                    nftDropSection.set("buffs.max-luck-buff", 0.20);
                    config.save(configFile);
                    plugin.getLogger().info("[NFTMiner] Đã thêm cài đặt buff mặc định vào config.yml");
                } catch (IOException e) {
                    plugin.getLogger().severe("[NFTMiner] Không thể lưu cài đặt buff mặc định: " + e.getMessage());
                }
            }
        } else {
            plugin.getLogger().warning("[NFTMiner] Không tìm thấy phần 'nft-drop' trong config.yml. Dùng mặc định.");
            // Sử dụng giá trị mặc định
            baseDropChance = 0.05;
            cooldownSeconds = 3;

            rarityDropRates.put("common", 5.0);
            rarityDropRates.put("uncommon", 2.0);
            rarityDropRates.put("rare", 1.0);
            rarityDropRates.put("epic", 0.5);
            rarityDropRates.put("legendary", 0.1);

            enableBuffs = true;
            maxLuckBuff = 0.20;
        }
    }
    private void loadRates() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Đọc từ phần nft-drop.tiers trong config.yml
        ConfigurationSection nftDropSection = config.getConfigurationSection("nft-drop");
        if (nftDropSection == null) {
            plugin.getLogger().severe("[NFTMiner] Không tìm thấy phần 'nft-drop' trong config.yml");
            return;
        }

        ConfigurationSection tiersSection = nftDropSection.getConfigurationSection("tiers");
        if (tiersSection == null) {
            plugin.getLogger().severe("[NFTMiner] Không tìm thấy phần 'nft-drop.tiers' trong config.yml");
            return;
        }

        // Xóa dữ liệu cũ
        nftRates.clear();

        // Load tỉ lệ cho từng tier
        for (String tier : tiersSection.getKeys(false)) {
            loadNFTRates(tier, tiersSection);
        }

        plugin.getLogger().info("[NFTMiner] Đã load tỉ lệ rơi NFT cho " + nftRates.size() + " tiers");
    }

    private void loadNFTRates(String tier, ConfigurationSection section) {
        ConfigurationSection tierSection = section.getConfigurationSection(tier);
        if (tierSection == null) {
            plugin.getLogger().severe("[NFTMiner] Không tìm thấy tier " + tier + " trong config.yml");
            return;
        }

        Map<String, Integer> rates = new HashMap<>();
        for (String nft : tierSection.getKeys(false)) {
            int rate = tierSection.getInt(nft);
            rates.put(nft, rate);
            plugin.getLogger().info("[NFTMiner] Loaded NFT rate: " + nft + " = " + rate + " (" + tier + ")");
        }

        nftRates.put(tier.toLowerCase(), rates);
    }


    private void loadNFTsByRarity() {
        nftsByRarity.clear();

        if (nftIntegration == null || !nftIntegration.isNFTPluginAvailable()) {
            plugin.getLogger().warning("[NFTMiner] NFTPlugin chưa kết nối. Không thể load NFT metadata.");

            // Khởi tạo danh sách rỗng cho các rarity
            String[] rarities = {"common", "uncommon", "rare", "epic", "legendary"};
            for (String rarity : rarities) {
                nftsByRarity.put(rarity, new ArrayList<>());
            }
            return;
        }

        // Sử dụng NFTPluginIntegration để lấy danh sách NFT theo rarity
        Map<String, List<String>> loadedNFTs = nftIntegration.loadNFTsByRarity();

        // Cập nhật cache local
        nftsByRarity.putAll(loadedNFTs);

        // Log thông tin
        for (Map.Entry<String, List<String>> entry : nftsByRarity.entrySet()) {
            plugin.getLogger().info("[NFTMiner] Loaded " + entry.getValue().size() + " NFTs for rarity: " + entry.getKey());
        }
    }


    public void reload() {
        rarityDropRates.clear();
        nftsByRarity.clear();
        lastDropTime.clear();
        loadConfig();

        // Làm mới cache metadata trước khi load lại
        if (nftIntegration != null) {
            nftIntegration.refreshMetadataCache();
        }

        loadNFTsByRarity();
        loadRates();
    }

    /**
     * Lấy tỉ lệ rơi NFT đã được điều chỉnh bởi buff
     * @param player Người chơi
     * @return Tỉ lệ rơi NFT đã được điều chỉnh
     */
    private double getAdjustedDropChance(Player player) {
        if (!enableBuffs || player == null) {
            plugin.getLogger().info("[NFTMiner] Buffs disabled or player is null");
            return baseDropChance;
        }

        // Kiểm tra xem player có buff luck không
        double luckBuff = 0.0;

        // Log tất cả metadata của người chơi để debug
        plugin.getLogger().info("[NFTMiner] Checking metadata for player: " + player.getName());

        // Kiểm tra metadata từ NFT-Plugin
        if (player.hasMetadata("nft_buff_luck")) {
            plugin.getLogger().info("[NFTMiner] Player has nft_buff_luck metadata");
            try {
                List<MetadataValue> values = player.getMetadata("nft_buff_luck");
                plugin.getLogger().info("[NFTMiner] Found " + values.size() + " nft_buff_luck values");

                if (!values.isEmpty()) {
                    double rawValue = values.get(0).asDouble();
                    plugin.getLogger().info("[NFTMiner] Raw buff value: " + rawValue);

                    luckBuff = rawValue / 100.0; // Chuyển từ % sang hệ số
                    plugin.getLogger().info("[NFTMiner] Player " + player.getName() + " có buff luck: " + (luckBuff * 100) + "%");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[NFTMiner] Lỗi khi đọc buff luck: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            plugin.getLogger().info("[NFTMiner] Player does NOT have nft_buff_luck metadata");

            // Thử đọc metadata khác có thể liên quan
            if (player.hasMetadata("nft_buffs")) {
                plugin.getLogger().info("[NFTMiner] Player has nft_buffs metadata");
                try {
                    List<MetadataValue> values = player.getMetadata("nft_buffs");
                    if (!values.isEmpty()) {
                        plugin.getLogger().info("[NFTMiner] nft_buffs value: " + values.get(0).asString());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[NFTMiner] Error reading nft_buffs: " + e.getMessage());
                }
            }
        }

        // Giới hạn buff tối đa
        if (luckBuff > maxLuckBuff) {
            luckBuff = maxLuckBuff;
        }

        // Tính toán tỉ lệ rơi mới
        double adjustedChance = baseDropChance + luckBuff;

        // Giới hạn tỉ lệ rơi tối đa là 100%
        if (adjustedChance > 1.0) {
            adjustedChance = 1.0;
        }

        return adjustedChance;
    }

    /**
     * Chọn NFT dựa trên tỉ lệ trong config
     * @param rarity Độ hiếm
     * @return NFT được chọn, hoặc null nếu không tìm thấy
     */
    private String selectNFTByRates(String rarity) {
        Map<String, Integer> rates = nftRates.get(rarity.toLowerCase());
        if (rates == null || rates.isEmpty()) {
            plugin.getLogger().warning("[NFTMiner] Không tìm thấy tỉ lệ cho rarity: " + rarity);
            return null;
        }

        // Tính tổng tỉ lệ
        int totalRate = 0;
        for (int rate : rates.values()) {
            totalRate += rate;
        }

        // Chọn ngẫu nhiên dựa trên tỉ lệ
        int roll = random.nextInt(totalRate);
        int currentSum = 0;

        for (Map.Entry<String, Integer> entry : rates.entrySet()) {
            currentSum += entry.getValue();
            if (roll < currentSum) {
                plugin.getLogger().info("[NFTMiner] Chọn NFT " + entry.getKey() + " với tỉ lệ " + entry.getValue() + "/" + totalRate);
                return entry.getKey();
            }
        }

        // Nếu không chọn được, lấy NFT đầu tiên
        String firstNFT = rates.keySet().iterator().next();
        plugin.getLogger().warning("[NFTMiner] Không thể chọn NFT dựa trên tỉ lệ, chọn mặc định: " + firstNFT);
        return firstNFT;
    }
}
