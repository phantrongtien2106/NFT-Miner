package me.tien.miner_simulator.commands;

import me.tien.miner_simulator.Miner_Simulator;
import me.tien.miner_simulator.gui.ShopGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {
    private final Miner_Simulator plugin;
    private final ShopGUI shopGUI;

    public ShopCommand(Miner_Simulator plugin, ShopGUI shopGUI) {
        this.plugin = plugin;
        this.shopGUI = shopGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cChỉ người chơi mới có thể sử dụng lệnh này!");
            return true;
        }

        Player player = (Player) sender;
        shopGUI.openShop(player);

        return true;
    }
}