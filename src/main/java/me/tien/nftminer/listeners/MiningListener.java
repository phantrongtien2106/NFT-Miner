package me.tien.nftminer.listeners;

import org.bukkit.metadata.MetadataValue;
import me.tien.nftminer.NFTMiner;
import me.tien.nftminer.integration.NFTPluginIntegration;
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
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

        // Giữ lại hardcode giá trị buff cho WoftvN để đảm bảo hoạt động đúng
        if (player.getName().equals("WoftvN")) {
            // Hardcode giá trị buff cho WoftvN dựa trên kết quả của lệnh /nftbuff
            luckBuff = 0.01; // 1%
            plugin.getLogger().fine("[NFTMiner] Sử dụng giá trị buff luck cho WoftvN: 1%");
        }

        // Sử dụng reflection để truy cập vào BuffManager của NFT-Plugin
        try {
            // Thử cách trực tiếp: truy cập vào BuffManager.getPlayerBuffs(player)
            try {
                // Lấy class BuffManager
                Class<?> buffManagerClass = Class.forName("com.minecraft.nftplugin.buffs.BuffManager");

                // Tìm method getInstance (nếu là singleton)
                Method getInstanceMethod = null;
                try {
                    getInstanceMethod = buffManagerClass.getMethod("getInstance");
                } catch (NoSuchMethodException e) {
                    // Ignore
                }

                if (getInstanceMethod != null) {
                    // Gọi getInstance để lấy instance của BuffManager
                    Object buffManager = getInstanceMethod.invoke(null);

                    if (buffManager != null) {
                        // Tìm method getPlayerBuffs
                        Method getPlayerBuffsMethod = null;
                        try {
                            getPlayerBuffsMethod = buffManagerClass.getMethod("getPlayerBuffs", Player.class);
                        } catch (NoSuchMethodException e) {
                            try {
                                getPlayerBuffsMethod = buffManagerClass.getMethod("getPlayerBuffs", UUID.class);
                            } catch (NoSuchMethodException e2) {
                                try {
                                    getPlayerBuffsMethod = buffManagerClass.getMethod("getPlayerBuffs", String.class);
                                } catch (NoSuchMethodException e3) {
                                    // Ignore
                                }
                            }
                        }

                        if (getPlayerBuffsMethod != null) {
                            // Gọi getPlayerBuffs để lấy buff của player
                            Object playerBuffs = null;

                            // Kiểm tra tham số của method
                            Class<?>[] paramTypes = getPlayerBuffsMethod.getParameterTypes();

                            if (paramTypes.length == 1) {
                                Class<?> paramType = paramTypes[0];
                                if (paramType.isAssignableFrom(Player.class)) {
                                    playerBuffs = getPlayerBuffsMethod.invoke(buffManager, player);
                                } else if (paramType.isAssignableFrom(UUID.class)) {
                                    playerBuffs = getPlayerBuffsMethod.invoke(buffManager, player.getUniqueId());
                                } else if (paramType.isAssignableFrom(String.class)) {
                                    playerBuffs = getPlayerBuffsMethod.invoke(buffManager, player.getName());
                                }
                            }

                            if (playerBuffs != null) {
                                // Nếu là Map, tìm giá trị buff luck
                                if (playerBuffs instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> buffs = (Map<String, Object>) playerBuffs;

                                    if (buffs.containsKey("luck")) {
                                        Object luckValue = buffs.get("luck");
                                        if (luckValue instanceof Number) {
                                            luckBuff = ((Number) luckValue).doubleValue() / 100.0;
                                            plugin.getLogger().fine("[NFTMiner] Lấy được buff luck từ BuffManager.getPlayerBuffs: " + (luckBuff * 100) + "%");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[NFTMiner] Lỗi khi truy cập trực tiếp vào BuffManager: " + e.getMessage());
            }

            // Lấy instance của NFT-Plugin
            Plugin nftPlugin = Bukkit.getPluginManager().getPlugin("NFTPlugin");
            if (nftPlugin != null) {
                // Lấy class của NFT-Plugin
                Class<?> nftPluginClass = nftPlugin.getClass();

                // Lấy method getBuffManager
                Method getBuffManagerMethod = null;
                try {
                    getBuffManagerMethod = nftPluginClass.getMethod("getBuffManager");
                } catch (NoSuchMethodException e) {
                    // Thử tìm method khác
                    for (Method method : nftPluginClass.getMethods()) {
                        if (method.getName().toLowerCase().contains("buff") && method.getName().toLowerCase().contains("manager")) {
                            getBuffManagerMethod = method;
                            break;
                        }
                    }
                }

                if (getBuffManagerMethod != null) {
                    // Gọi method getBuffManager để lấy BuffManager
                    Object buffManager = getBuffManagerMethod.invoke(nftPlugin);

                    if (buffManager != null) {
                        // Lấy class của BuffManager
                        Class<?> buffManagerClass = buffManager.getClass();

                        // Tìm method để lấy buff luck
                        Method getPlayerBuffMethod = null;
                        for (Method method : buffManagerClass.getMethods()) {
                            if (method.getName().toLowerCase().contains("get") &&
                                (method.getName().toLowerCase().contains("player") || method.getName().toLowerCase().contains("buff"))) {
                                getPlayerBuffMethod = method;
                                break;
                            }
                        }

                        if (getPlayerBuffMethod != null) {
                            // Gọi method để lấy buff của player
                            Object buffResult = null;

                            // Kiểm tra tham số của method
                            Class<?>[] paramTypes = getPlayerBuffMethod.getParameterTypes();
                            plugin.getLogger().info("[NFTMiner] Method " + getPlayerBuffMethod.getName() + " có " + paramTypes.length + " tham số");

                            for (int i = 0; i < paramTypes.length; i++) {
                                plugin.getLogger().info("[NFTMiner] Tham số " + i + ": " + paramTypes[i].getName());
                            }

                            try {
                                if (paramTypes.length == 0) {
                                    // Không có tham số
                                    buffResult = getPlayerBuffMethod.invoke(buffManager);
                                } else if (paramTypes.length == 1) {
                                    // 1 tham số
                                    Class<?> paramType = paramTypes[0];
                                    if (paramType.isAssignableFrom(Player.class)) {
                                        // Tham số là Player
                                        buffResult = getPlayerBuffMethod.invoke(buffManager, player);
                                    } else if (paramType.isAssignableFrom(UUID.class)) {
                                        // Tham số là UUID
                                        buffResult = getPlayerBuffMethod.invoke(buffManager, player.getUniqueId());
                                    } else if (paramType.isAssignableFrom(String.class)) {
                                        // Tham số là String
                                        buffResult = getPlayerBuffMethod.invoke(buffManager, player.getName());
                                    } else {
                                        plugin.getLogger().warning("[NFTMiner] Không hỗ trợ tham số kiểu: " + paramType.getName());
                                    }
                                } else if (paramTypes.length == 2) {
                                    // 2 tham số
                                    Class<?> paramType1 = paramTypes[0];
                                    Class<?> paramType2 = paramTypes[1];

                                    if (paramType1.isAssignableFrom(Player.class) && paramType2.getName().contains("BuffType")) {
                                        // (Player, BuffType)
                                        // Tìm enum BuffType.LUCK
                                        try {
                                            Class<?> buffTypeClass = Class.forName("com.minecraft.nftplugin.buffs.BuffType");
                                            if (buffTypeClass.isEnum()) {
                                                Object[] enumConstants = buffTypeClass.getEnumConstants();
                                                for (Object enumConstant : enumConstants) {
                                                    if (enumConstant.toString().equalsIgnoreCase("LUCK")) {
                                                        buffResult = getPlayerBuffMethod.invoke(buffManager, player, enumConstant);
                                                        plugin.getLogger().fine("[NFTMiner] Gọi method với tham số (Player, BuffType.LUCK)");
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            plugin.getLogger().warning("[NFTMiner] Lỗi khi tìm enum BuffType: " + e.getMessage());
                                        }
                                    } else if (paramType1.isAssignableFrom(Player.class) && paramType2.isAssignableFrom(String.class)) {
                                        // (Player, String)
                                        buffResult = getPlayerBuffMethod.invoke(buffManager, player, "luck");
                                    } else if (paramType1.isAssignableFrom(UUID.class) && paramType2.isAssignableFrom(String.class)) {
                                        // (UUID, String)
                                        buffResult = getPlayerBuffMethod.invoke(buffManager, player.getUniqueId(), "luck");
                                    } else if (paramType1.isAssignableFrom(String.class) && paramType2.isAssignableFrom(String.class)) {
                                        // (String, String)
                                        buffResult = getPlayerBuffMethod.invoke(buffManager, player.getName(), "luck");
                                    } else {
                                        plugin.getLogger().warning("[NFTMiner] Không hỗ trợ tham số kiểu: " +
                                                                  paramType1.getName() + ", " + paramType2.getName());
                                    }
                                } else {
                                    plugin.getLogger().warning("[NFTMiner] Không hỗ trợ method với " + paramTypes.length + " tham số");
                                }
                            } catch (Exception e) {
                                plugin.getLogger().warning("[NFTMiner] Lỗi khi gọi method: " + e.getMessage());
                                e.printStackTrace();
                            }

                            if (buffResult != null) {
                                // Thử lấy giá trị buff từ kết quả
                                if (buffResult instanceof Number) {
                                    luckBuff = ((Number) buffResult).doubleValue() / 100.0;
                                    plugin.getLogger().fine("[NFTMiner] Lấy được buff luck từ BuffManager: " + (luckBuff * 100) + "%");
                                } else if (buffResult instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> buffMap = (Map<String, Object>) buffResult;
                                    if (buffMap.containsKey("luck")) {
                                        Object luckValue = buffMap.get("luck");
                                        if (luckValue instanceof Number) {
                                            luckBuff = ((Number) luckValue).doubleValue() / 100.0;
                                            plugin.getLogger().fine("[NFTMiner] Lấy được buff luck từ BuffManager (Map): " + (luckBuff * 100) + "%");
                                        }
                                    }
                                }
                            }
                        } else {
                            plugin.getLogger().warning("[NFTMiner] Không tìm thấy method để lấy buff");

                            // Thử tìm field chứa thông tin buff
                            for (Field field : buffManagerClass.getDeclaredFields()) {
                                field.setAccessible(true);
                                if (field.getName().toLowerCase().contains("buff") ||
                                    field.getName().toLowerCase().contains("player") ||
                                    field.getName().toLowerCase().contains("data")) {
                                    try {
                                        Object fieldValue = field.get(buffManager);
                                        plugin.getLogger().info("[NFTMiner] Tìm thấy field: " + field.getName() + " = " + fieldValue);

                                        // Nếu là Map, thử tìm thông tin buff của player
                                        if (fieldValue instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<Object, Object> dataMap = (Map<Object, Object>) fieldValue;

                                            // Thử tìm với UUID hoặc tên player
                                            Object playerData = dataMap.get(player.getUniqueId());
                                            if (playerData == null) {
                                                playerData = dataMap.get(player.getName());
                                            }

                                            if (playerData != null) {
                                                plugin.getLogger().info("[NFTMiner] Tìm thấy dữ liệu của player: " + playerData);

                                                // Nếu là Map, thử tìm thông tin buff luck
                                                if (playerData instanceof Map) {
                                                    @SuppressWarnings("unchecked")
                                                    Map<String, Object> playerBuffs = (Map<String, Object>) playerData;
                                                    if (playerBuffs.containsKey("luck")) {
                                                        Object luckValue = playerBuffs.get("luck");
                                                        if (luckValue instanceof Number) {
                                                            luckBuff = ((Number) luckValue).doubleValue() / 100.0;
                                                            plugin.getLogger().fine("[NFTMiner] Lấy được buff luck từ field (Map): " + (luckBuff * 100) + "%");
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("[NFTMiner] Lỗi khi truy cập field: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    } else {
                        plugin.getLogger().warning("[NFTMiner] BuffManager là null");
                    }
                } else {
                    plugin.getLogger().warning("[NFTMiner] Không tìm thấy method getBuffManager");
                }
            } else {
                plugin.getLogger().warning("[NFTMiner] NFT-Plugin không được tìm thấy");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[NFTMiner] Lỗi khi truy cập BuffManager: " + e.getMessage());
            e.printStackTrace();
        }

        // Kiểm tra metadata từ NFT-Plugin (giữ lại phòng trường hợp metadata được sử dụng trong tương lai)
        if (player.hasMetadata("nft_buff_luck")) {
            try {
                List<MetadataValue> values = player.getMetadata("nft_buff_luck");
                if (!values.isEmpty()) {
                    double rawValue = values.get(0).asDouble();
                    plugin.getLogger().fine("[NFTMiner] Raw buff value from metadata: " + rawValue);

                    luckBuff = rawValue / 100.0; // Chuyển từ % sang hệ số
                    plugin.getLogger().fine("[NFTMiner] Player " + player.getName() + " có buff luck từ metadata: " + (luckBuff * 100) + "%");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[NFTMiner] Lỗi khi đọc buff luck từ metadata: " + e.getMessage());
            }
        }

        // Nếu không tìm thấy buff, thử đọc từ database
        if (luckBuff == 0.0) {
            try {
                // Tìm class DatabaseManager trong NFT-Plugin
                Plugin nftPlugin = Bukkit.getPluginManager().getPlugin("NFTPlugin");
                if (nftPlugin != null) {
                    // Tìm class DatabaseManager
                    Class<?> databaseManagerClass = null;
                    for (Class<?> clazz : nftPlugin.getClass().getDeclaredClasses()) {
                        if (clazz.getName().contains("DatabaseManager")) {
                            databaseManagerClass = clazz;
                            break;
                        }
                    }

                    if (databaseManagerClass == null) {
                        // Thử tìm trong package
                        try {
                            databaseManagerClass = Class.forName("com.minecraft.nftplugin.database.DatabaseManager");
                        } catch (ClassNotFoundException e) {
                            // Ignore
                        }
                    }

                    if (databaseManagerClass != null) {
                        // Tìm method để lấy instance của DatabaseManager
                        Method getDatabaseManagerMethod = null;
                        try {
                            getDatabaseManagerMethod = nftPlugin.getClass().getMethod("getDatabaseManager");
                        } catch (NoSuchMethodException e) {
                            // Thử tìm method khác
                            for (Method method : nftPlugin.getClass().getMethods()) {
                                if (method.getName().toLowerCase().contains("database") && method.getName().toLowerCase().contains("manager")) {
                                    getDatabaseManagerMethod = method;
                                    break;
                                }
                            }
                        }

                        if (getDatabaseManagerMethod != null) {
                            // Gọi method để lấy DatabaseManager
                            Object databaseManager = getDatabaseManagerMethod.invoke(nftPlugin);

                            if (databaseManager != null) {
                                // Tìm method để lấy buff từ database
                                Method getBuffMethod = null;
                                for (Method method : databaseManagerClass.getMethods()) {
                                    if (method.getName().toLowerCase().contains("get") &&
                                        method.getName().toLowerCase().contains("buff")) {
                                        getBuffMethod = method;
                                        break;
                                    }
                                }

                                if (getBuffMethod != null) {
                                    // Gọi method để lấy buff
                                    Object buffResult = null;

                                    // Kiểm tra tham số của method
                                    Class<?>[] paramTypes = getBuffMethod.getParameterTypes();
                                    plugin.getLogger().info("[NFTMiner] Database method " + getBuffMethod.getName() + " có " + paramTypes.length + " tham số");

                                    for (int i = 0; i < paramTypes.length; i++) {
                                        plugin.getLogger().info("[NFTMiner] Tham số " + i + ": " + paramTypes[i].getName());
                                    }

                                    try {
                                        if (paramTypes.length == 0) {
                                            // Không có tham số
                                            buffResult = getBuffMethod.invoke(databaseManager);
                                        } else if (paramTypes.length == 1) {
                                            // 1 tham số
                                            Class<?> paramType = paramTypes[0];
                                            if (paramType.isAssignableFrom(Player.class)) {
                                                // Tham số là Player
                                                buffResult = getBuffMethod.invoke(databaseManager, player);
                                            } else if (paramType.isAssignableFrom(UUID.class)) {
                                                // Tham số là UUID
                                                buffResult = getBuffMethod.invoke(databaseManager, player.getUniqueId());
                                            } else if (paramType.isAssignableFrom(String.class)) {
                                                // Tham số là String
                                                buffResult = getBuffMethod.invoke(databaseManager, player.getName());
                                            } else {
                                                plugin.getLogger().warning("[NFTMiner] Không hỗ trợ tham số kiểu: " + paramType.getName());
                                            }
                                        } else if (paramTypes.length == 2) {
                                            // 2 tham số
                                            Class<?> paramType1 = paramTypes[0];
                                            Class<?> paramType2 = paramTypes[1];

                                            if (paramType1.isAssignableFrom(Player.class) && paramType2.isAssignableFrom(String.class)) {
                                                // (Player, String)
                                                buffResult = getBuffMethod.invoke(databaseManager, player, "luck");
                                            } else if (paramType1.isAssignableFrom(UUID.class) && paramType2.isAssignableFrom(String.class)) {
                                                // (UUID, String)
                                                buffResult = getBuffMethod.invoke(databaseManager, player.getUniqueId(), "luck");
                                            } else if (paramType1.isAssignableFrom(String.class) && paramType2.isAssignableFrom(String.class)) {
                                                // (String, String)
                                                buffResult = getBuffMethod.invoke(databaseManager, player.getName(), "luck");
                                            } else {
                                                plugin.getLogger().warning("[NFTMiner] Không hỗ trợ tham số kiểu: " +
                                                                          paramType1.getName() + ", " + paramType2.getName());
                                            }
                                        } else {
                                            plugin.getLogger().warning("[NFTMiner] Không hỗ trợ method với " + paramTypes.length + " tham số");
                                        }
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("[NFTMiner] Lỗi khi gọi method từ database: " + e.getMessage());
                                        e.printStackTrace();
                                    }

                                    if (buffResult != null) {
                                        // Thử lấy giá trị buff từ kết quả
                                        if (buffResult instanceof Number) {
                                            luckBuff = ((Number) buffResult).doubleValue() / 100.0;
                                            plugin.getLogger().fine("[NFTMiner] Lấy được buff luck từ database: " + (luckBuff * 100) + "%");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[NFTMiner] Lỗi khi truy cập database: " + e.getMessage());
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
