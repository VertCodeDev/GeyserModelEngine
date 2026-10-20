package re.imc.geysermodelengine.managers.model.taskshandler;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.events.GeyserModelEngineEntityDeathEvent;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.model.Model;
import re.imc.geysermodelengine.packet.entity.PacketEntity;
import re.imc.geysermodelengine.util.CustomEntitySpawnSynchronizer;

import java.awt.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BetterModelTaskHandler implements TaskHandler {

    private final GeyserModelEngine plugin;

    private final BetterModelEntityData entityData;

    private int tick = 0;
    private int syncTick = 0;

    private float lastScale = -1.0f;
    private Color lastColor = null;

    private volatile boolean removed = false;

    private final ConcurrentHashMap<String, Integer> lastIntSet = new ConcurrentHashMap<>();
    private final Cache<String, Boolean> lastPlayedAnim = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MILLISECONDS).build();

    // Who can see the model is asked less often than it moves: every Bedrock player is measured
    // against it, and a model that shows up a few ticks late is not noticed.
    private final long viewerCheckPeriod;
    private long nextViewerCheck = 0;

    private ScheduledFuture scheduledFuture;

    public BetterModelTaskHandler(GeyserModelEngine plugin, BetterModelEntityData entityData) {
        this.plugin = plugin;
        this.entityData = entityData;
        this.viewerCheckPeriod = plugin.getConfigManager().getConfig().getLong("models.viewer-check-period", 100);

        plugin.getEntityTaskManager().sendHitBoxToAll(entityData);
        scheduledFuture = plugin.getSchedulerPool().scheduleAtFixedRate(() -> {
            try {
                runAsync();
            } catch (Throwable err) {
                err.printStackTrace();
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
    }

    @Override
    public void runAsync() {
        if (removed) return;

        PacketEntity entity = entityData.getEntity();
        if (entity.isDead()) return;

        if (entityData.isRemoved()) {
            retire();
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= nextViewerCheck) {
            nextViewerCheck = now + viewerCheckPeriod;
            plugin.getEntityTaskManager().checkViewers(entityData, entityData.getViewers());
        }

        Set<Player> viewers = entityData.getViewers();

        entityData.teleportToModel();

        if (tick % 40 == 0) {
            viewers.removeIf(viewer -> !plugin.getEntityTaskManager().canSee(viewer, entityData.getEntity(), entityData.getModelInstance()));
        }

        tick++;
        if (tick > 400) {
            tick = 0;
            plugin.getEntityTaskManager().sendHitBoxToAll(entityData);
        }

        if (viewers.isEmpty()) return;

        plugin.getEntityTaskManager().getPropertyHandler().sendScale(entityData, viewers, lastScale, false);
        plugin.getEntityTaskManager().getPropertyHandler().sendColor(entityData, viewers, lastColor, false);
    }

    /**
     * Take the model down for its viewers and forget it. Only this model leaves the entity's map:
     * an entity can carry more than one.
     */
    public synchronized void retire() {
        if (removed) return;
        removed = true;
        entityData.getEntity().remove();

        int key = entityData.getKey();
        Map<Model, EntityData> models = plugin.getModelManager().getEntitiesCache().get(key);
        if (models != null) {
            models.values().remove(entityData);
            if (models.isEmpty()) plugin.getModelManager().getEntitiesCache().remove(key, models);
        }
        plugin.getModelManager().getModelEntitiesCache().computeIfPresent(key, (k, model) -> model.getEntityData() == entityData ? null : model);

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.death")) plugin.getLogger().info(entityData.getTracker().name() + " (" + entityData.getTracker().getClass().getSimpleName() + ", key " + key + ") is gone, removing it");

        cancel();

        Bukkit.getPluginManager().callEvent(new GeyserModelEngineEntityDeathEvent(entityData));
    }

    @Override
    public void sendEntityData(EntityData entityData, Player player, int delay) {
        BetterModelEntityData betterModelEntityData = (BetterModelEntityData) entityData;
        long propertiesSendDelay = plugin.getConfigManager().getConfig().getInt("models.properties-send-delay", 1);
        String customIdentifier = plugin.getConfigManager().getConfig().getString("models.namespace") + ":" + betterModelEntityData.getTracker().name().toLowerCase();

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.send-data")) plugin.getLogger().info("Setting custom entity data for " + betterModelEntityData.getTracker().name());

        CustomEntitySpawnSynchronizer.sendAndSpawn(plugin, player, entityData.getEntity().getEntityId(), customIdentifier, delay, () -> {
            entityData.getEntity().sendSpawnPacket(Collections.singletonList(player));

            plugin.getSchedulerPool().schedule(() -> {
                plugin.getEntityTaskManager().getPropertyHandler().sendHitBox(entityData, player);

                plugin.getEntityTaskManager().getPropertyHandler().sendScale(entityData, Collections.singleton(player), lastScale, true);
                plugin.getEntityTaskManager().getPropertyHandler().sendColor(entityData, Collections.singleton(player), lastColor, true);

                plugin.getEntityTaskManager().getPropertyHandler().updateEntityProperties(entityData, Collections.singleton(player), true);
            }, propertiesSendDelay, TimeUnit.MILLISECONDS);
        });
    }

    @Override
    public void cancel() {
        scheduledFuture.cancel(true);
    }

    public void setTick(int tick) {
        this.tick = tick;
    }

    public void setSyncTick(int syncTick) {
        this.syncTick = syncTick;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    public void setLastScale(float lastScale) {
        this.lastScale = lastScale;
    }

    public int getTick() {
        return tick;
    }

    public int getSyncTick() {
        return syncTick;
    }

    public void setLastColor(Color lastColor) {
        this.lastColor = lastColor;
    }

    public float getLastScale() {
        return lastScale;
    }

    public Color getLastColor() {
        return lastColor;
    }

    public boolean isRemoved() {
        return removed;
    }

    public ConcurrentHashMap<String, Integer> getLastIntSet() {
        return lastIntSet;
    }

    public Cache<String, Boolean> getLastPlayedAnim() {
        return lastPlayedAnim;
    }

    public ScheduledFuture getScheduledFuture() {
        return scheduledFuture;
    }
}
