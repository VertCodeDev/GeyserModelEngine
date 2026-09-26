package re.imc.geysermodelengine.managers.model.entity;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.google.common.collect.Sets;
import kr.toxicity.model.api.bukkit.platform.BukkitLocation;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelRotation;
import kr.toxicity.model.api.tracker.Tracker;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.taskshandler.BetterModelTaskHandler;
import re.imc.geysermodelengine.packet.entity.PacketEntity;

import java.util.Set;

/**
 * One BetterModel tracker mirrored to Bedrock viewers as a custom entity. Covers both kinds of
 * tracker: an {@link EntityTracker} follows a real entity, a {@link kr.toxicity.model.api.tracker.DummyTracker}
 * has no entity at all and is placed and shown per player by whoever spawned it.
 */
public class BetterModelEntityData implements EntityData {

    private final GeyserModelEngine plugin;

    private final PacketEntity entity;
    private final Set<Player> viewers = Sets.newConcurrentHashSet();

    private final Tracker tracker;
    // The entity an EntityTracker follows; null for a DummyTracker.
    private final @Nullable BaseEntity entitySource;
    // The caches' key: the entity's id, or a negative id of our own for a DummyTracker.
    private final int key;

    private BetterModelTaskHandler entityTask;

    private volatile boolean hurt;

    private static final long DEAD_GRACE_MILLIS = 30_000;
    private volatile long deadSince;

    public BetterModelEntityData(GeyserModelEngine plugin, Tracker tracker, int key) {
        this.plugin = plugin;

        this.tracker = tracker;
        this.entitySource = tracker instanceof EntityTracker entityTracker ? entityTracker.sourceEntity() : null;
        this.key = key;

        this.entity = new PacketEntity(EntityTypes.PIG, viewers, modelLocation());

        runEntityTask();
    }

    /**
     * Where the model stands, facing the way BetterModel draws it: a tracker's rotation, not the
     * location's, since a dummy is often turned without being moved.
     */
    private Location modelLocation() {
        Location location = ((BukkitLocation) tracker.location()).source().clone();
        ModelRotation rotation = tracker.rotation();

        location.setYaw(rotation.y());
        location.setPitch(rotation.x());

        return location;
    }

    @Override
    public void teleportToModel() {
        Location location = modelLocation();
        entity.teleport(location);

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.location")) plugin.getLogger().info(tracker.name() + " " + location);
    }

    /**
     * Whether the tracker is gone. BetterModel closes a tracker when it is removed, reloaded or its
     * entity despawns dead, and keeps it open through an entity's death animation, which Bedrock
     * then plays too. A tracker whose entity stays dead without being closed is let go after
     * {@link #DEAD_GRACE_MILLIS}, so it cannot tick forever.
     */
    public boolean isRemoved() {
        if (tracker.isClosed()) return true;
        if (entitySource == null || !entitySource.dead()) {
            deadSince = 0;
            return false;
        }

        long now = System.currentTimeMillis();
        if (deadSince == 0) deadSince = now;
        return now - deadSince > DEAD_GRACE_MILLIS;
    }

    public void runEntityTask() {
        entityTask = new BetterModelTaskHandler(plugin, this);
    }

    @Override
    public PacketEntity getEntity() {
        return entity;
    }

    @Override
    public Set<Player> getViewers() {
        return viewers;
    }

    @Override
    public BetterModelTaskHandler getEntityTask() {
        return entityTask;
    }

    @Override
    public Object getModelInstance() {
        return tracker;
    }

    public void setHurt(boolean hurt) {
        this.hurt = hurt;
    }

    public @Nullable BaseEntity getEntitySource() {
        return entitySource;
    }

    public Tracker getTracker() {
        return tracker;
    }

    public int getKey() {
        return key;
    }

    public boolean isHurt() {
        return hurt;
    }
}
