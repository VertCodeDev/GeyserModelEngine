package re.imc.geysermodelengine.util;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import re.imc.geysermodelengine.hooks.FloodgateAPIHook;

public class BedrockUtils {

    private static final FloodgateApi floodgateAPIHook = FloodgateAPIHook.getAPI();

    /** The name prefix Floodgate gives Bedrock players on networks that use one. */
    private static final String BEDROCK_PREFIX = ".";

    public static boolean isBedrockPlayer(Player player) {
        if (floodgateAPIHook != null) return floodgateAPIHook.isFloodgatePlayer(player.getUniqueId());

        // Without Floodgate on this server (it runs on the proxy), what Floodgate leaves on the
        // player still tells: its uuid, whose upper half is zero, and the name prefix.
        if (player.getUniqueId().getMostSignificantBits() == 0L) return true;
        if (player.getName().startsWith(BEDROCK_PREFIX)) return true;

        String clientBrand = player.getClientBrandName();
        if (clientBrand == null) return false;
        return clientBrand.contains("Geyser");
    }
}
