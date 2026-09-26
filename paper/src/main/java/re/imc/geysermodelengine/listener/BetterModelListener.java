package re.imc.geysermodelengine.listener;

import kr.toxicity.model.api.bukkit.BetterModelBukkit;
import kr.toxicity.model.api.event.CreateDummyTrackerEvent;
import kr.toxicity.model.api.event.CreateEntityTrackerEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.server.PluginEnableEvent;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.model.Model;

import java.util.concurrent.atomic.AtomicBoolean;

public class BetterModelListener implements Listener {

    private static final String BETTER_MODEL = "BetterModel";

    private final GeyserModelEngine plugin;
    private final AtomicBoolean subscribed = new AtomicBoolean();

    public BetterModelListener(GeyserModelEngine plugin) {
        this.plugin = plugin;

        // BetterModel loads after worlds and this plugin at startup, so its platform may not be
        // registered yet: subscribe once it is enabled.
        if (Bukkit.getPluginManager().isPluginEnabled(BETTER_MODEL)) subscribe();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals(BETTER_MODEL)) subscribe();
    }

    private void subscribe() {
        if (!subscribed.compareAndSet(false, true)) return;

        var eventBus = BetterModelBukkit.platform().eventBus();
        eventBus.subscribe(plugin, CreateEntityTrackerEvent.class, event -> plugin.getModelManager().getModelHandler().createModel(event.tracker()));
        eventBus.subscribe(plugin, CreateDummyTrackerEvent.class, event -> plugin.getModelManager().getModelHandler().createModel(event.tracker()));
    }

    @EventHandler
    public void onModelDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();

        Model model = plugin.getModelManager().getModelEntitiesCache().get(entity.getEntityId());
        if (model == null) return;

        ((BetterModelEntityData) model.getEntityData()).setHurt(true);
    }
}
