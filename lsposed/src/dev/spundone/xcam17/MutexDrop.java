package dev.spundone.xcam17;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Feature clashes come from a mutex table: for a feature change (an event holding the feature
 * id) the manager returns the rules to apply, each naming the other feature it turns off.
 * Ultra HD (feature 209, the 50MP Photo Resolution) and the photo Style (190) switch each
 * other off, so 50MP shots were always Leica Vibrant. This drops that pair from the rule
 * lists, and also keeps the Style component from being marked disabled (its disabled state
 * reports value 0, Vibrant, whatever was picked), which the Style rule applier does directly.
 */
final class MutexDrop {
    /** Feature id pairs that are allowed together. */
    static final int[][] ALLOWED = {{209, 190}};

    private MutexDrop() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.mutexManagerClass == null) return;
        try {
            Class<?> manager = cl.loadClass(build.mutexManagerClass);
            Class<?> event = cl.loadClass(build.mutexEventClass);
            Class<?> rule = cl.loadClass(build.mutexRuleClass);
            Method find = manager.getDeclaredMethod(build.mutexFindMethod, event);
            Field eventId = event.getDeclaredField(build.mutexEventIdField);
            Field ruleTarget = rule.getDeclaredField(build.mutexRuleTargetField);
            if (find.getReturnType() != java.util.ArrayList.class || eventId.getType() != int.class
                    || ruleTarget.getType() != int.class) {
                Entry.log("mutex: unexpected shape, skipping");
                return;
            }
            eventId.setAccessible(true);
            ruleTarget.setAccessible(true);
            XposedBridge.hookMethod(find, new Filter(eventId, ruleTarget));
            if (build.styleDisableClass != null) {
                Method disable = cl.loadClass(build.styleDisableClass)
                        .getDeclaredMethod(build.styleDisableMethod, int.class, boolean.class);
                XposedBridge.hookMethod(disable, new KeepEnabled());
            }
            Entry.log("mutex: Ultra HD and Style allowed together");
        } catch (Throwable t) {
            Entry.log("mutex: not hooked: " + t);
        }
    }

    static final class KeepEnabled extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            p.args[1] = Boolean.FALSE;
        }
    }

    static final class Filter extends XC_MethodHook {
        private final Field eventId;
        private final Field ruleTarget;

        Filter(Field eventId, Field ruleTarget) {
            this.eventId = eventId;
            this.ruleTarget = ruleTarget;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            Object result = p.getResult();
            if (!(result instanceof List) || p.args[0] == null) return;
            int id = eventId.getInt(p.args[0]);
            for (int[] pair : ALLOWED) {
                int other = id == pair[0] ? pair[1] : id == pair[1] ? pair[0] : -1;
                if (other < 0) continue;
                List<?> rules = (List<?>) result;
                for (int i = rules.size() - 1; i >= 0; i--) {
                    Object r = rules.get(i);
                    if (r != null && rule(r) == other) rules.remove(i);
                }
            }
        }

        private int rule(Object r) throws IllegalAccessException {
            return ruleTarget.getDeclaringClass().isInstance(r) ? ruleTarget.getInt(r) : -1;
        }
    }
}
