package me.tien.miner_simulator.world;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class VoidMine implements Listener {
    private final Plugin plugin;
    private final Map<UUID, PlayerMine> playerMines = new HashMap<>();
    private final Map<OreType, Double> oreRates = new HashMap<>();

    // Thêm biến cho đường dẫn đến file config của DeluxeHub
    private static final String DELUXEHUB_CONFIG_PATH = "plugins/DeluxeHub/config.yml";

    public VoidMine(Plugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection oreSection = config.getConfigurationSection("ore-rates");
        if (oreSection != null) {
            for (String key : oreSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    double rate = oreSection.getDouble(key, 0.0);
                    if (rate > 0) {
                        oreRates.put(OreType.fromMaterial(material), Double.valueOf(rate));
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Material không hợp lệ trong config: " + key);
                }
            }
        }

        if (oreRates.isEmpty()) {
            oreRates.put(OreType.STONE, Double.valueOf(0.7));
            oreRates.put(OreType.IRON_ORE, Double.valueOf(0.15));
            oreRates.put(OreType.GOLD_ORE, Double.valueOf(0.1));
            oreRates.put(OreType.DIAMOND_ORE, Double.valueOf(0.05));
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }



    public class PlayerMine {
        private final UUID playerUUID;
        private final String playerName;
        private final String worldName;
        private static final int PASTE_X = 0;
        private static final int PASTE_Y = 10;
        private static final int PASTE_Z = 0;
        private World mineWorld;
        private Location spawnLocation;

        private int minX, minY, minZ, maxX, maxY, maxZ;

        public PlayerMine(Player player) {
            this.playerUUID = player.getUniqueId();
            this.playerName = player.getName();
            this.worldName = "mine_" + playerUUID.toString().replace("-", "");
            playerMines.put(playerUUID, this); // lưu lại mine
            createMineWorld();
        }

        public void createMineWorld() {
            mineWorld = Bukkit.getWorld(worldName);
            if (mineWorld == null) {
                WorldCreator worldCreator = new WorldCreator("mines/" + worldName);
                worldCreator.generator(new VoidGenerator());
                worldCreator.generateStructures(false);
                mineWorld = worldCreator.createWorld();
                if (mineWorld != null) {
                    mineWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    mineWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    mineWorld.setTime(6000);
                    mineWorld.setDifficulty(Difficulty.PEACEFUL);
                    mineWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                    
                    // Thêm thế giới mới vào danh sách disabled_worlds của DeluxeHub
                    addWorldToDeluxeHubDisabledList("mines/" + worldName);
                    
                    // Tặng cúp kim cương trực tiếp cho người chơi khi tạo mỏ mới
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null) {
                        // Tạo cúp kim cương
                        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
                        // Đặt tên cho cúp
                        ItemMeta meta = pickaxe.getItemMeta();
                        meta.setDisplayName(ChatColor.AQUA + "Cúp Thợ Mỏ");
                        pickaxe.setItemMeta(meta);
                        // Thêm vào túi đồ của người chơi
                        player.getInventory().addItem(pickaxe);
                        player.sendMessage(ChatColor.GREEN + "Bạn đã nhận được một cúp kim cương để đào!");
                        
                        // Hiệu ứng âm thanh và hạt
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
                    }
                }
            }
            if (mineWorld != null) {
                // Thiết lập các cài đặt an toàn cho thế giới
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ tnt deny -w mines/" + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ lighter deny -w mines/" + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ lava-fire deny -w mines/" + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ fire-spread deny -w mines/" + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ mob-spawning deny -w mines/" + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ block-place deny -w mines/" + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ chest-access deny -w mines/" + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ pvp deny -w mines/" + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ creeper-explosion deny -w mines/" + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag __global__ block-break deny -w mines/" + worldName);
                
                // Tiếp tục với code hiện tại
                File schematicFile = new File(plugin.getDataFolder(), "schematics/mine_template.schem");
                Location pasteLocation = new Location(mineWorld, PASTE_X, PASTE_Y, PASTE_Z);
                pasteSchematic(schematicFile, pasteLocation);
                
                // Vị trí spawn gần khu vực mining box mới (-25,-11,-26) tới (-7,-1,-8)
                // Đặt spawn ở vị trí gần khu đào nhưng không nằm trong khu đào
                int spawnX = -28; // Cách khu đào 3 khối về phía X âm
                int spawnY = 1; // Cao hơn đáy khu đào 1 khối
                int spawnZ = -16; // Cách khu đào 3 khối về phía Z âm
                spawnLocation = new Location(mineWorld, spawnX, spawnY, spawnZ);
                mineWorld.setSpawnLocation(spawnLocation);
                fillMiningBox();
            } else {
                getLogger().severe("Không thể tạo hoặc tải thế giới: " + worldName + " cho người chơi " + playerName);
            }
        }

        private void pasteSchematic(File schematicFile, Location location) {
            try {
                // Trước tiên, kiểm tra xem file có tồn tại trong thư mục plugins không
                if (!schematicFile.exists()) {
                    // Nếu không tìm thấy file, thử lấy từ bên trong JAR
                    plugin.getLogger().info("Không tìm thấy file schematic trong thư mục plugin, thử lấy từ resources...");
                }
                
                ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
                if (format == null) {
                    plugin.getLogger().warning("Không tìm thấy định dạng cho schematic: " + schematicFile.getName());
                    return;
                }
                Clipboard clipboard;
                try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                    clipboard = reader.read();
                }
                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSession(BukkitAdapter.adapt(mineWorld))) {
                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                            .ignoreAirBlocks(false)
                            .build();
                    Operations.complete(operation);
                }
                // Tạo region bảo vệ schematic
                try (ClipboardReader reader = ClipboardFormats.findByFile(schematicFile)
                        .getReader(new FileInputStream(schematicFile))) {
                    clipboard = reader.read();
                    BlockVector3 min = clipboard.getMinimumPoint();
                    BlockVector3 max = clipboard.getMaximumPoint();

                    String regionName = "schem_" + playerUUID.toString().replace("-", "");
                    int x1 = location.getBlockX() + min.getBlockX();
                    int y1 = location.getBlockY() + min.getBlockY();
                    int z1 = location.getBlockZ() + min.getBlockZ();
                    int x2 = location.getBlockX() + max.getBlockX();
                    int y2 = location.getBlockY() + max.getBlockY();
                    int z2 = location.getBlockZ() + max.getBlockZ();

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            String.format("rg define %s %d %d %d %d %d %d %s",
                                    regionName, x1, y1, z1, x2, y2, z2, location.getWorld().getName()));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            String.format("rg flag %s block-break deny %s", regionName, location.getWorld().getName()));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            String.format("rg setpriority %s 5 %s", regionName, location.getWorld().getName()));
                } catch (IOException e) {
                    plugin.getLogger().warning("Không thể tạo region bảo vệ schematic: " + e.getMessage());
                }
            } catch (IOException | com.sk89q.worldedit.WorldEditException e) {
                plugin.getLogger().warning("Lỗi khi paste schematic: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void fillMiningBox() {
            // Sử dụng tọa độ cố định thay vì đọc từ config
            // Tọa độ 1: (-25,-11,-26)
            // Tọa độ 2: (-7,-1,-8)
            
            minX = -25;
            minY = -11;
            minZ = -26;
            
            // Tính kích thước từ tọa độ đã cho
            maxX = -7;
            maxY = -1;
            maxZ = -8;
            
            // Tính toán kích thước cho thông báo
            int width = maxX - minX;
            int height = maxY - minY;
            int length = maxZ - minZ;
            
            plugin.getLogger().info(String.format("Tạo mining box cho người chơi %s từ tọa độ (%d,%d,%d) đến (%d,%d,%d)", 
                    playerName, minX, minY, minZ, maxX, maxY, maxZ));
            
            //Thiết lập region cho mining box
            String regionName = "box_" + playerUUID.toString().replace("-", "");
            String worldPath = "mines/" + worldName;
            
            plugin.getLogger().info("Tạo region cho mining box: " + regionName + " trong thế giới " + worldPath);
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    String.format("rg define %s %d %d %d %d %d %d %s",
                            regionName, minX, minY, minZ, maxX, maxY, maxZ, worldPath));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    String.format("rg flag %s block-break allow %s", regionName, worldPath));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    String.format("rg flag %s block-place deny %s", regionName, worldPath));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    String.format("rg setpriority %s 10 %s", regionName, worldPath));

            // Điền block vào khu vực đào
            Random random = new Random();
            int totalBlocks = 0;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        OreType ore = getRandomOre(random);
                        mineWorld.getBlockAt(x, y, z).setType(ore.getMaterial());
                        totalBlocks++;
                    }
                }
            }
            
            plugin.getLogger().info(String.format("Đã tạo mining box với %d block cho người chơi %s", 
                    totalBlocks, playerName));
        }

        private OreType getRandomOre(Random random) {
            double value = random.nextDouble();
            double current = 0.0;
            for (Map.Entry<OreType, Double> entry : oreRates.entrySet()) {
                current += entry.getValue();
                if (value < current) return entry.getKey();
            }
            return OreType.STONE;
        }

        public void resetMiningBox() {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null) return;
            
            // Teleport người chơi về spawn
            teleportPlayer(player);
            
            // Hiển thị thông báo
            player.sendTitle(
                ChatColor.RED + "Đang reset khu đào", 
                ChatColor.YELLOW + "Vui lòng đứng yên trong 5 giây", 
                10, 70, 20
            );
            
            // Gửi thông báo cảnh báo
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.YELLOW + "Khu đào của bạn đang được reset. Vui lòng đứng yên trong 5 giây.");
            
            // Đăng ký listener để chặn di chuyển
            final UUID playerUUID = this.playerUUID;
            
            // Tạo một event handler tạm thời
            Listener moveListener = new Listener() {
                @org.bukkit.event.EventHandler
                public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
                    if (event.getPlayer().getUniqueId().equals(playerUUID)) {
                        // Chỉ hủy nếu người chơi di chuyển vị trí (không hủy khi quay đầu)
                        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || 
                            event.getFrom().getBlockY() != event.getTo().getBlockY() || 
                            event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                            
                            event.setCancelled(true);
                            event.getPlayer().sendActionBar(
                                ChatColor.RED + "Không thể di chuyển trong khi reset khu đào!"
                            );
                        }
                    }
                }
            };
            
            // Đăng ký listener
            Bukkit.getPluginManager().registerEvents(moveListener, plugin);
            
            // Đếm ngược và reset khu đào
            final int[] countdown = {5};
            final int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (countdown[0] > 0) {
                    player.sendActionBar(ChatColor.YELLOW + "Reset khu đào trong " + ChatColor.RED + countdown[0] + ChatColor.YELLOW + " giây...");
                    countdown[0]--;
                }
            }, 0L, 20L); // Run every second
            
            // Sau 5 giây, hủy task và listener, tiến hành reset mining box
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                // Hủy task đếm ngược
                Bukkit.getScheduler().cancelTask(taskId);
                
                // Hủy đăng ký listener
                org.bukkit.event.HandlerList.unregisterAll(moveListener);
                
                // Reset mining box
                fillMiningBox();
                
                // Thông báo hoàn tất
                player.sendTitle(
                    ChatColor.GREEN + "Reset hoàn tất", 
                    ChatColor.YELLOW + "Bạn có thể bắt đầu đào!", 
                    10, 40, 20
                );
                player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.YELLOW + "Khu đào đã được reset thành công!");
                
                // Hiệu ứng âm thanh và hạt
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
                player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0);
            }, 5 * 20L); // 5 seconds
        }

        public void unloadWorld() {
            if (mineWorld != null) {
                Bukkit.unloadWorld(mineWorld, true);
            }
        }

        public void teleportPlayer(Player player) {
            if (spawnLocation != null) {
                player.teleport(spawnLocation);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                player.sendMessage(ChatColor.GREEN + "Bạn đã được teleport đến khu đào của mình!");
            } else {
                player.sendMessage(ChatColor.RED + "Không thể xác định vị trí spawn trong khu đào của bạn!");
            }
        }
    }

    /**
     * Thêm thế giới vào danh sách disabled-worlds trong config của DeluxeHub
     * @param worldName Tên thế giới cần thêm
     */
    private void addWorldToDeluxeHubDisabledList(String worldName) {
        try {
            File configFile = new File(DELUXEHUB_CONFIG_PATH);
            
            if (!configFile.exists()) {
                plugin.getLogger().warning("Không tìm thấy file config.yml của DeluxeHub!");
                return;
            }
            
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            List<String> disabledWorlds = config.getStringList("disabled-worlds.worlds");
            
            // Kiểm tra xem thế giới đã có trong danh sách chưa
            if (!disabledWorlds.contains(worldName)) {
                disabledWorlds.add(worldName);
                config.set("disabled-worlds.worlds", disabledWorlds);
                config.save(configFile);
                plugin.getLogger().info("Đã thêm thế giới " + worldName + " vào danh sách disabled-worlds của DeluxeHub");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Lỗi khi cập nhật config DeluxeHub: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static class VoidGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            ChunkData chunkData = createChunkData(world);
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    biome.setBiome(i, 0, j, org.bukkit.block.Biome.PLAINS);
                }
            }
            return chunkData;
        }
    }

    public enum OreType {
        STONE(Material.STONE),
        IRON_ORE(Material.IRON_ORE),
        GOLD_ORE(Material.GOLD_ORE),
        DIAMOND_ORE(Material.DIAMOND_ORE);

        private final Material material;

        OreType(Material material) {
            this.material = material;
        }

        public Material getMaterial() {
            return material;
        }

        public static OreType fromMaterial(Material material) {
            for (OreType type : values()) {
                if (type.getMaterial() == material) return type;
            }
            return STONE;
        }
    }

    private Logger getLogger() {
        return plugin.getLogger();
    }

    public PlayerMine getPlayerMine(Player player) {
        return playerMines.get(player.getUniqueId());
    }
}
