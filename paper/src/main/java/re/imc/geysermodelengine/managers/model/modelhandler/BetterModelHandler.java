package re.imc.geysermodelengine.managers.model.modelhandler;

import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.Tracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.listener.BetterModelListener;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.model.BetterModelModel;
import re.imc.geysermodelengine.managers.model.model.Model;
import re.imc.geysermodelengine.managers.model.propertyhandler.PropertyHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BetterModelHandler implements ModelHandler {

    private final GeyserModelEngine plugin;

    // A DummyTracker has no entity, so it gets a key of its own below zero, clear of entity ids.
    private final AtomicInteger dummyKeys = new AtomicInteger();

    public BetterModelHandler(GeyserModelEngine plugin) {
        this.plugin = plugin;
    }

    /**
     * Mirror a tracker, entity or dummy. Called from BetterModel's events, so on whichever thread
     * created the tracker: everything here is safe to run concurrently.
     */
    @Override
    public void createModel(Object... objects) {
        Tracker tracker = (Tracker) objects[0];
        int key = tracker instanceof EntityTracker entityTracker ? entityTracker.sourceEntity().id() : dummyKeys.decrementAndGet();

        Map<Model, EntityData> entityDataCache = plugin.getModelManager().getEntitiesCache().computeIfAbsent(key, k -> new ConcurrentHashMap<>());

        // One copy of a model per entity. Checked before building anything: the entity data
        // starts its task on construction, and a rejected one would tick forever.
        for (Model existing : entityDataCache.keySet()) {
            if (existing.getName().equals(tracker.name())) return;
        }

        PropertyHandler propertyHandler = plugin.getEntityTaskManager().getPropertyHandler();
        EntityData entityData = new BetterModelEntityData(plugin, tracker, key);
        Model model = new BetterModelModel(tracker, this, entityData, propertyHandler);

        plugin.getModelManager().getModelEntitiesCache().put(key, model);
        entityDataCache.put(model, entityData);

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.spawn")) plugin.getLogger().info("Creating model for " + model.getName() + " (" + tracker.getClass().getSimpleName() + ")");
    }

    @Override
    public void processEntities(Entity entity) {
        // BetterModel restores a loaded entity's tracker itself, which fires CreateEntityTrackerEvent.
    }

    @Override
    public void loadListeners() {
        Bukkit.getPluginManager().registerEvents(new BetterModelListener(plugin), plugin);
    }

    /**
     * A Bedrock player sees the model when BetterModel has spawned it to them. That is what a
     * dummy tracker's owner decides per player, and what an entity tracker works out from who
     * tracks the entity, so it is the one answer that holds for both.
     */
    @Override
    public boolean canSee(Player player, Object model) {
        Tracker tracker = (Tracker) model;
        return tracker.isSpawned(player.getUniqueId()) && !tracker.isHide(BukkitAdapter.adapt(player));
    }
}
