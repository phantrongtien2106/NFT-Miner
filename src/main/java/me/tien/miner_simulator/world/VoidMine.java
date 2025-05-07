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
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
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
                }
            }
            if (mineWorld != null) {
                File schematicFile = new File(plugin.getDataFolder(), "schematics/mine_template.schem");
                Location pasteLocation = new Location(mineWorld, PASTE_X, PASTE_Y, PASTE_Z);
                pasteSchematic(schematicFile, pasteLocation);
                int spawnX = PASTE_X - 30;
                int spawnY = PASTE_Y - 9;
                int spawnZ = PASTE_Z - 19;
                spawnLocation = new Location(mineWorld, spawnX, spawnY, spawnZ);
                mineWorld.setSpawnLocation(spawnLocation);
                fillMiningBox();
            } else {
                getLogger().severe("Không thể tạo hoặc tải thế giới: " + worldName + " cho người chơi " + playerName);
            }
        }

        private void pasteSchematic(File schematicFile, Location location) {
            try {
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
            }
        }

        private void fillMiningBox() {
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection box = config.getConfigurationSection("mine-box");
            if (box == null) return;
            minX = box.getInt("min-x");
            minY = box.getInt("min-y");
            minZ = box.getInt("min-z");
            int width = box.getInt("width");
            int height = box.getInt("height");
            int length = box.getInt("length");

            maxX = minX + width;
            maxY = minY + height;
            maxZ = minZ + length;
            
            //Thiết lập region cho mining box
            String regionName = "box_" + playerUUID.toString().replace("-", "");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    String.format("rg define %s %d %d %d %d %d %d %s",
                            regionName, minX, minY, minZ, maxX - 1, maxY - 1, maxZ - 1, worldName));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    String.format("rg flag %s block-break allow %s", regionName, worldName));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    String.format("rg flag %s block-place deny %s", regionName, worldName));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    String.format("rg setpriority %s 10 %s", regionName, worldName));

            Random random = new Random();
            for (int x = minX; x < maxX; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        OreType ore = getRandomOre(random);
                        mineWorld.getBlockAt(x, y, z).setType(ore.getMaterial());
                    }
                }
            }
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
            teleportPlayer(Bukkit.getPlayer(playerUUID));
            fillMiningBox();
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
