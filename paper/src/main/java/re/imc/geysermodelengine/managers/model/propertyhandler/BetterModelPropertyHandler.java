package re.imc.geysermodelengine.managers.model.propertyhandler;

import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.RunningAnimation;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.nms.ModelDisplay;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.Tracker;
import me.zimzaza4.geyserutils.spigot.api.EntityUtils;
import org.bukkit.entity.Player;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.util.BooleanPacker;

import java.awt.Color;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;

public class BetterModelPropertyHandler implements PropertyHandler {

    private static final int NO_TINT = 0xFFFFFF;
    // Prefixes the extension leaves out when it sorts bones into property bits.
    private static final List<String> SORT_STRIPPED = List.of("pbody_", "plarm_", "prarm_", "plleg_", "prleg_", "phead_", "p_");

    // BetterModel keeps a bone's tint private; read it so any tint shows on Bedrock, not only an
    // entity's damage flash (a dummy tracker is tinted through TrackerUpdateAction.tint).
    private static final VarHandle BONE_TINT = boneTint();

    private final GeyserModelEngine plugin;

    public BetterModelPropertyHandler(GeyserModelEngine plugin) {
        this.plugin = plugin;
    }

    private static VarHandle boneTint() {
        try {
            return MethodHandles.privateLookupIn(RenderedBone.class, MethodHandles.lookup()).findVarHandle(RenderedBone.class, "tint", int.class);
        } catch (ReflectiveOperationException | RuntimeException err) {
            return null;
        }
    }

    @Override
    public void sendScale(EntityData entityData, Collection<Player> players, float lastScale, boolean firstSend) {
        if (players.isEmpty()) return;

        BetterModelEntityData model = (BetterModelEntityData) entityData;
        Tracker tracker = model.getTracker();
        float scale = tracker.scaler().scale(tracker);

        if (!firstSend) {
            if (scale == lastScale) return;
            model.getEntityTask().setLastScale(scale);
        }

        players.forEach(player -> EntityUtils.sendCustomScale(player, model.getEntity().getEntityId(), scale));
    }

    @Override
    public void sendColor(EntityData entityData, Collection<Player> players, Color lastColor, boolean firstSend) {
        if (players.isEmpty()) return;

        BetterModelEntityData model = (BetterModelEntityData) entityData;
        Color color = new Color(currentTint(model));

        if (!firstSend) {
            if (color.equals(lastColor)) return;
            model.getEntityTask().setLastColor(color);
        }

        players.forEach(player -> EntityUtils.sendCustomColor(player, model.getEntity().getEntityId(), color));
    }

    private int currentTint(BetterModelEntityData model) {
        Tracker tracker = model.getTracker();

        if (BONE_TINT != null) {
            for (RenderedBone bone : tracker.bones()) {
                if (bone.getDisplay() != null) return (int) BONE_TINT.getVolatile(bone);
            }
            return NO_TINT;
        }

        // No access to the tint: fall back to flashing an entity's damage tint when it is hit.
        if (model.isHurt() && tracker instanceof EntityTracker entityTracker) {
            model.setHurt(false);
            return entityTracker.damageTintValue();
        }
        return NO_TINT;
    }

    @Override
    public void sendHitBox(EntityData entityData, Player player) {
        EntityUtils.sendCustomHitBox(player, entityData.getEntity().getEntityId(), 0.02f, 0);
    }

    @Override
    public void updateEntityProperties(EntityData entityData, Collection<Player> players, boolean firstSend, String... forceAnims) {
        BetterModelEntityData model = (BetterModelEntityData) entityData;
        Tracker tracker = model.getTracker();

        int entity = model.getEntity().getEntityId();

        // Bone bits exist only when the pack was built with part visibility on (its
        // disable_part_visibility off); sent otherwise, Geyser forwards them as unknown properties.
        Map<String, Boolean> boneUpdates = new HashMap<>();
        if (plugin.getConfigManager().getConfig().getBoolean("models.part-visibility", false)) {
            tracker.bones().forEach(bone -> processBone(bone, boneUpdates));
        }

        // Every animation the model has gets a bit, set while any bone is playing it. Both sides
        // sort the names, so the set has to be the model's full list, running or not.
        Map<String, BlueprintAnimation> animations = tracker.renderer().animations();
        Set<String> running = new HashSet<>();
        Set<String> overriding = new HashSet<>();
        for (RenderedBone bone : tracker.bones()) {
            RunningAnimation animation = bone.runningAnimation();
            if (animation == null) continue;

            BlueprintAnimation blueprint = animations.get(animation.name());
            if (blueprint == null) continue;

            running.add(animation.name());
            if (blueprint.override() && blueprint.loop() == AnimationIterator.Type.PLAY_ONCE) overriding.add(animation.name());
        }
        // A play-once animation that overrides the rest plays alone, as it does on Java.
        if (!overriding.isEmpty()) running = overriding;

        Map<String, Boolean> animUpdates = new HashMap<>();
        for (String id : animations.keySet()) animUpdates.put(id, running.contains(id));

        Set<String> lastPlayed = new HashSet<>(model.getEntityTask().getLastPlayedAnim().asMap().keySet());

        for (Map.Entry<String, Boolean> anim : animUpdates.entrySet()) {
            if (anim.getValue()) model.getEntityTask().getLastPlayedAnim().put(anim.getKey(), true);
        }

        for (String anim : lastPlayed) animUpdates.put(anim, true);

        if (boneUpdates.isEmpty() && animUpdates.isEmpty()) return;

        String namespace = plugin.getConfigManager().getConfig().getString("models.namespace");
        Map<String, Integer> intUpdates = new HashMap<>();
        int i = 0;

        for (Integer integer : BooleanPacker.mapBooleansToInts(boneUpdates)) {
            intUpdates.put(namespace + ":bone" + i, integer);
            i++;
        }

        i = 0;
        for (Integer integer : BooleanPacker.mapBooleansToInts(animUpdates)) {
            intUpdates.put(namespace + ":anim" + i, integer);
            i++;
        }

        if (!firstSend) {
            if (intUpdates.equals(model.getEntityTask().getLastIntSet())) {
                return;
            } else {
                model.getEntityTask().getLastIntSet().clear();
                model.getEntityTask().getLastIntSet().putAll(intUpdates);
            }
        }

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.animations")) plugin.getLogger().info(animUpdates.toString());

        players.forEach(player -> EntityUtils.sendIntProperties(player, entity, intUpdates));
    }

    /**
     * A bone's visibility bit, keyed the way the extension sorts its geometry's bones: by the raw
     * (tagged) name BetterModel read from the file, lowercased, with the extension's sort prefixes
     * stripped. Bones without a display (no cubes) still take a bit, as they do in the geometry.
     */
    private void processBone(RenderedBone bone, Map<String, Boolean> map) {
        String name = bone.name().rawName().toLowerCase(Locale.ROOT);
        if (name.equals("hitbox") || name.equals("shadow") || name.equals("mount") || name.startsWith("b_") || name.startsWith("ob_")) return;

        for (String prefix : SORT_STRIPPED) name = name.replace(prefix, "");

        ModelDisplay display = bone.getDisplay();
        map.put(name, display == null || !display.invisible());
    }
}
