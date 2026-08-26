package cn.simpmc.banitem;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import cn.simpmc.banitem.command.BanItemCommand;
import cn.simpmc.banitem.config.PluginConfigLoader;
import cn.simpmc.banitem.config.PluginSettings;
import cn.simpmc.banitem.listener.ItemEnforcementListener;
import cn.simpmc.banitem.log.ViolationLogger;
import cn.simpmc.banitem.service.EnforcementService;
import cn.simpmc.banitem.service.MessageService;

public final class BanItemPlugin extends JavaPlugin {
    private ViolationLogger violationLogger;

    @Override
    public void onEnable() {
        try {
            installDefaultFiles();
            AtomicReference<PluginSettings> settings = new AtomicReference<>(
                    PluginConfigLoader.load(getDataFolder()));

            violationLogger = new ViolationLogger(getDataFolder().toPath().resolve("violations.yml"),
                    failure -> getLogger().log(Level.SEVERE,
                            "Violation logging failed; audit output may be incomplete", failure));
            violationLogger.start();
            MessageService messages = new MessageService(this);
            EnforcementService enforcement = new EnforcementService(settings, violationLogger, messages, getLogger());
            getServer().getPluginManager().registerEvents(new ItemEnforcementListener(this, enforcement), this);

            BanItemCommand executor = new BanItemCommand(this, settings, messages);
            PluginCommand command = Objects.requireNonNull(getCommand("banitem"), "banitem command");
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            getLogger().info("SimpMC-BanItem is enabled with Folia 26.1.2 support.");
        } catch (Throwable throwable) {
            getLogger().log(Level.SEVERE, "SimpMC-BanItem failed to start", throwable);
            if (violationLogger != null) {
                try {
                    violationLogger.close();
                } catch (RuntimeException closeFailure) {
                    throwable.addSuppressed(closeFailure);
                }
            }
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (violationLogger == null) {
            return;
        }
        try {
            violationLogger.close();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "One or more violation records could not be flushed during shutdown", exception);
        } finally {
            violationLogger = null;
        }
    }

    private void installDefaultFiles() {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Could not create plugin data directory: " + folder);
        }
        saveResourceIfMissing("config.yml");
        saveResourceIfMissing("banned-items.yml");
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).isFile()) {
            saveResource(name, false);
        }
    }
}
