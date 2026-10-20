package re.imc.geysermodelengine.util;

import java.util.Collection;
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

    // The online Bedrock players, so a model looks for viewers among a few hundred players
    // instead of every one online.
    private static final Map<UUID, Player> ONLINE = new ConcurrentHashMap<>();

    // Players asked before their client brand arrived, asked again until it tells.
    private static final Map<UUID, Player> PENDING = new ConcurrentHashMap<>();

    public static boolean isBedrockPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Boolean known = KNOWN.get(playerId);
        if (known != null) return known;

        if (floodgateAPIHook != null) return remember(player, floodgateAPIHook.isFloodgatePlayer(playerId));

        // Without Floodgate on this server (it runs on the proxy), what Floodgate leaves on the
        // player still tells: its uuid, whose upper half is zero, and the name prefix.
        if (playerId.getMostSignificantBits() == 0L || player.getName().startsWith(BEDROCK_PREFIX)) return remember(player, true);

        String clientBrand = player.getClientBrandName();
        if (clientBrand == null) {
            if (player.isOnline()) PENDING.put(playerId, player);
            return false;
        }

        return remember(player, clientBrand.contains("Geyser"));
    }

    /**
     * The online Bedrock players. Players still waiting on their client brand are asked again
     * first, so one who turns out to be on Bedrock joins the set as soon as it arrives.
     *
     * @return a live view of the online Bedrock players
     */
    public static Collection<Player> getBedrockPlayers() {
        if (!PENDING.isEmpty()) {
            for (Player player : PENDING.values()) {
                if (!player.isOnline()) {
                    PENDING.remove(player.getUniqueId());
                    continue;
                }

                isBedrockPlayer(player);
            }
        }

        return ONLINE.values();
    }

    private static boolean remember(Player player, boolean bedrock) {
        UUID playerId = player.getUniqueId();
        KNOWN.put(playerId, bedrock);
        PENDING.remove(playerId);

        // A player asked once more after they left stays out of the set.
        if (bedrock && player.isOnline()) ONLINE.put(playerId, player);
        return bedrock;
    }

    /**
     * Forget a player who left, so a rejoin is asked again.
     *
     * @param playerId the player
     */
    public static void forget(UUID playerId) {
        KNOWN.remove(playerId);
        ONLINE.remove(playerId);
        PENDING.remove(playerId);
    }
}
