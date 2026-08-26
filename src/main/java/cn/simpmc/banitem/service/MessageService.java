package cn.simpmc.banitem.service;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MessageService {
    private final Plugin plugin;

    public MessageService(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Safe to call from configuration and log worker callbacks. */
    public void sendScheduled(CommandSender sender, String template, Map<String, String> values) {
        String message = render(template, values);
        if (sender instanceof Player player) {
            player.getScheduler().execute(plugin, () -> player.sendMessage(message), null, 1L);
        } else if (sender instanceof BlockCommandSender blockSender) {
            var block = blockSender.getBlock();
            Bukkit.getRegionScheduler().execute(plugin, block.getWorld(), block.getX() >> 4, block.getZ() >> 4,
                    () -> sender.sendMessage(message));
        } else if (sender instanceof Entity entity) {
            entity.getScheduler().execute(plugin, () -> sender.sendMessage(message), null, 1L);
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(message));
        }
    }

    /** Call only from an event already executing on the player's region. */
    public void sendNow(Player player, String template, Map<String, String> values) {
        player.sendMessage(render(template, values));
    }

    @SuppressWarnings("deprecation")
    static String render(String template, Map<String, String> values) {
        String result = Objects.requireNonNull(template, "template");
        for (Map.Entry<String, String> value : values.entrySet()) {
            result = result.replace("{" + value.getKey() + "}", value.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }
}
