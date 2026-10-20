package re.imc.geysermodelengine;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import re.imc.geysermodelengine.hooks.FloodgateAPIHook;
import re.imc.geysermodelengine.listener.ModelListener;
import re.imc.geysermodelengine.listener.MountPacketListener;
import re.imc.geysermodelengine.managers.ConfigManager;
import re.imc.geysermodelengine.commands.GeyserModelEngineCommand;
import re.imc.geysermodelengine.managers.model.EntityTaskManager;
import re.imc.geysermodelengine.managers.model.ModelManager;
import re.imc.geysermodelengine.runnables.BedrockMountControlRunnable;
import re.imc.geysermodelengine.runnables.UpdateTaskRunnable;
import re.imc.geysermodelengine.util.BedrockUtils;

import java.util.concurrent.*;

public class GeyserModelEngine extends JavaPlugin {

    private ConfigManager configManager;


    private ModelManager modelManager;
    private EntityTaskManager entityTaskManager;

    private ScheduledExecutorService schedulerPool;

    @Override
    public void onLoad() {
        preLoadManagers();
    }

    @Override
    public void onEnable() {
        loadHooks();
        loadManagers();
        loadRunnables();

        loadBStats();

        PacketEvents.getAPI().getEventManager().registerListener(new MountPacketListener(this), PacketListenerPriority.NORMAL);

        Bukkit.getPluginManager().registerEvents(new ModelListener(this), this);

        // Players already online (a late load) have no join to put them in the Bedrock set.
        Bukkit.getOnlinePlayers().forEach(BedrockUtils::isBedrockPlayer);
    }

    @Override
    public void onDisable() {
        // Nothing to take down when enabling failed half way.
        if (this.modelManager != null) {
            this.modelManager.removeEntities();
        }

    }

    private void loadHooks() {
        FloodgateAPIHook.loadHook(this);
    }

    private void loadBStats() {
        if (this.configManager.getConfig().getBoolean("metrics.bstats", true)) new Metrics(this, 26981);
    }

    private void preLoadManagers() {
        this.configManager = new ConfigManager(this);
    }

    private void loadManagers() {
        GeyserModelEngineCommand.register(this);

        this.modelManager = new ModelManager(this);
        this.entityTaskManager = new EntityTaskManager(this);
    }

    private void loadRunnables() {
        this.schedulerPool = Executors.newScheduledThreadPool(configManager.getConfig().getInt("models.thread-pool-size", 4));
        this.schedulerPool.scheduleAtFixedRate(new UpdateTaskRunnable(this), 10, configManager.getConfig().getLong("models.entity-position-update-period", 35), TimeUnit.MILLISECONDS);
        this.schedulerPool.scheduleAtFixedRate(new BedrockMountControlRunnable(this), 1, 1, TimeUnit.MILLISECONDS);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ModelManager getModelManager() {
        return modelManager;
    }

    public EntityTaskManager getEntityTaskManager() {
        return entityTaskManager;
    }

    public ScheduledExecutorService getSchedulerPool() {
        return schedulerPool;
    }
}
