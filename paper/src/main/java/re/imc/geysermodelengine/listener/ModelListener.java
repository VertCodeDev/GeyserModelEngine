package re.imc.geysermodelengine.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.events.GeyserModelEngineEntitySpawnEvent;
import re.imc.geysermodelengine.util.BedrockUtils;

public class ModelListener implements Listener {

    private final GeyserModelEngine plugin;

    public ModelListener(GeyserModelEngine plugin) {
        this.plugin = plugin;
    }

    /*
     / Folia: iterating a whole world's entities (the old WorldInitEvent sweep) requires owning
     / every region and throws under region threading. EntitiesLoadEvent fires per chunk ON the
     / owning region, and also covers persisted model entities loading in later - strictly more
     / correct on Paper too.
    */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(entity -> plugin.getModelManager().getModelHandler().processEntities(entity));
    }

    /*
     / xSquishyLiam:
     / A runDelay makes sure the client doesn't see pigs on login due to the client resyncing themselves back to normal
    */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!BedrockUtils.isBedrockPlayer(player)) return;
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> plugin.getModelManager().getPlayerJoinedCache().add(player.getUniqueId()), 20);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!BedrockUtils.isBedrockPlayer(player)) return;
        plugin.getModelManager().getPlayerJoinedCache().remove(player.getUniqueId());
    }
}
