package cn.simpmc.banitem.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import cn.simpmc.banitem.config.PluginConfigLoader;
import cn.simpmc.banitem.config.PluginSettings;
import cn.simpmc.banitem.service.MessageService;

public final class BanItemCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final AtomicReference<PluginSettings> settings;
    private final MessageService messages;
    private final AtomicBoolean reloadRunning = new AtomicBoolean();

    public BanItemCommand(Plugin plugin, AtomicReference<PluginSettings> settings, MessageService messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginSettings current = settings.get();
        if (!sender.hasPermission("banitem.admin")) {
            messages.sendScheduled(sender, current.messages().noPermission(), Map.of());
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            messages.sendScheduled(sender, current.messages().usage(), Map.of());
            return true;
        }
        if (!reloadRunning.compareAndSet(false, true)) {
            messages.sendScheduled(sender, "&e配置正在重载，请稍候。", Map.of());
            return true;
        }

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                PluginSettings replacement = PluginConfigLoader.load(plugin.getDataFolder());
                if (!plugin.isEnabled()) {
                    return;
                }
                settings.set(replacement);
                messages.sendScheduled(sender, replacement.messages().reloaded(), Map.of());
                plugin.getLogger().info("Configuration reloaded successfully.");
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not reload configuration; the previous rules remain active", exception);
                if (plugin.isEnabled()) {
                    messages.sendScheduled(sender, settings.get().messages().reloadFailed(), Map.of());
                }
            } finally {
                reloadRunning.set(false);
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("banitem.admin") || args.length != 1) {
            return List.of();
        }
        return "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reload") : List.of();
    }
}
