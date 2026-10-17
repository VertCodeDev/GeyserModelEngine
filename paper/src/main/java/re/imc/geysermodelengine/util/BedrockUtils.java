package re.imc.geysermodelengine.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import re.imc.geysermodelengine.hooks.FloodgateAPIHook;

public class BedrockUtils {

    private static final FloodgateApi floodgateAPIHook = FloodgateAPIHook.getAPI();

    /** The name prefix Floodgate gives Bedrock players on networks that use one. */
    private static final String BEDROCK_PREFIX = ".";

    // Asked for every viewer of every model on every tick, so each player's answer is kept until
    // they leave. A Java player is only remembered once their client brand arrived, since it
    // comes after the join and is the one sign Geyser may give.
    private static final Map<UUID, Boolean> KNOWN = new ConcurrentHashMap<>();

    public static boolean isBedrockPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Boolean known = KNOWN.get(playerId);
        if (known != null) return known;

        if (floodgateAPIHook != null) {
            boolean bedrock = floodgateAPIHook.isFloodgatePlayer(playerId);
            KNOWN.put(playerId, bedrock);
            return bedrock;
        }

        // Without Floodgate on this server (it runs on the proxy), what Floodgate leaves on the
        // player still tells: its uuid, whose upper half is zero, and the name prefix.
        if (playerId.getMostSignificantBits() == 0L || player.getName().startsWith(BEDROCK_PREFIX)) {
            KNOWN.put(playerId, true);
            return true;
        }

        String clientBrand = player.getClientBrandName();
        if (clientBrand == null) return false;

        boolean bedrock = clientBrand.contains("Geyser");
        KNOWN.put(playerId, bedrock);
        return bedrock;
    }

    /**
     * Forget a player who left, so a rejoin is asked again.
     *
     * @param playerId the player
     */
    public static void forget(UUID playerId) {
        KNOWN.remove(playerId);
    }
}
