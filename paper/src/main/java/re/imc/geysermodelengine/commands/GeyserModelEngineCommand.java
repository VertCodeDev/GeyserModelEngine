package re.imc.geysermodelengine.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.util.ColourUtils;

/**
 * {@code /geysermodelengine reload}, registered through Paper's own command API rather than
 * CommandAPI, which does not start on every Minecraft version Paper runs.
 */
public final class GeyserModelEngineCommand {

    private static final String PERMISSION = "geysermodelengine.commands.reload";

    private GeyserModelEngineCommand() {
    }

    public static void register(GeyserModelEngine plugin) {
        ColourUtils colourUtils = new ColourUtils();
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
            Commands.literal("geysermodelengine")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("reload").executes(context -> {
                    Bukkit.getAsyncScheduler().runNow(plugin, task -> plugin.getConfigManager().load());
                    context.getSource().getSender().sendMessage(colourUtils.miniFormat(
                        plugin.getConfigManager().getLang().getString("commands.reload.successfully-reloaded")));

                    return Command.SINGLE_SUCCESS;
                }))
                .build(),
            "GeyserModelEngine's commands"
        ));
    }
}
