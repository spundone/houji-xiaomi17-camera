package dev.spundone.xcam17.aosp;

import java.lang.reflect.Field;
import java.util.ArrayList;

/** Ports of MutexDrop and WatermarkSubimage. */
public final class Photo {
    /** Feature id pairs allowed together: Ultra HD (209, 50MP) and photo Style (190). */
    static final int[][] ALLOWED = {{209, 190}};

    private Photo() {}

    /**
     * The mutex manager's rules for a feature change: drops the rules that would switch the
     * other feature of an allowed pair off. idField is the event's feature id field, rule the
     * rule class and targetField the feature id a rule turns off.
     */
    public static ArrayList<?> mutexRules(ArrayList<?> rules, Object event, String idField, Class<?> rule,
            String targetField) {
        if (rules == null || event == null) return rules;
        try {
            Field id = event.getClass().getDeclaredField(idField);
            Field target = rule.getDeclaredField(targetField);
            id.setAccessible(true);
            target.setAccessible(true);
            int feature = id.getInt(event);
            for (int[] pair : ALLOWED) {
                int other = feature == pair[0] ? pair[1] : feature == pair[1] ? pair[0] : -1;
                if (other < 0) continue;
                for (int i = rules.size() - 1; i >= 0; i--) {
                    Object r = rules.get(i);
                    if (rule.isInstance(r) && target.getInt(r) == other) rules.remove(i);
                }
            }
        } catch (Throwable t) {
            Log.once("mutex: rules unreadable", t);
        }
        return rules;
    }

    /**
     * Watermark drawing: a frame watermark covers nothing, so there is no sub-image, but the
     * remover info is still set and the HEIF saver crashed building it. Without a sub-image the
     * remover info is dropped.
     */
    public static Object afterWatermark(Object drawn, Object job, String removerField, String subimageField) {
        if (job == null) return drawn;
        try {
            Field remover = job.getClass().getDeclaredField(removerField);
            Field subimage = job.getClass().getDeclaredField(subimageField);
            remover.setAccessible(true);
            subimage.setAccessible(true);
            if (subimage.get(job) == null) remover.set(job, null);
        } catch (Throwable t) {
            Log.once("watermark: job unreadable", t);
        }
        return drawn;
    }
}
