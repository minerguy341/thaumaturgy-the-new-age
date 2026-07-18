package io.github.minerguy341.new_age_thaum.core.aspect;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runtime store of loaded aspects. Not a Minecraft registry: aspects are
 * datapack-reloadable, so this is rebuilt on every reload and synced to clients.
 * Validation is fail-soft (m1 spec): invalid aspects are dropped with a log line
 * rather than crashing the reload.
 */
public final class AspectRegistry {
    private static volatile Map<ResourceLocation, Aspect> aspects = Map.of();

    private AspectRegistry() {
    }

    public static Collection<Aspect> all() {
        return aspects.values();
    }

    public static Optional<Aspect> get(ResourceLocation id) {
        return Optional.ofNullable(aspects.get(id));
    }

    public static boolean exists(ResourceLocation id) {
        return aspects.containsKey(id);
    }

    public static int count() {
        return aspects.size();
    }

    /** Replace contents from a reload or a server sync. Returns the accepted map size. */
    public static int reload(Map<ResourceLocation, Aspect> incoming) {
        aspects = Map.copyOf(filterValid(incoming));
        return aspects.size();
    }

    /**
     * Returns the subset of {@code incoming} that forms a valid aspect graph, dropping
     * (with a log line) any aspect whose component count is wrong, whose components are
     * missing, or that sits on a cycle — cascading, so a compound that loses a component
     * is dropped too. Pure: does not touch the live registry, so it is directly testable.
     */
    public static Map<ResourceLocation, Aspect> filterValid(Map<ResourceLocation, Aspect> incoming) {
        Map<ResourceLocation, Aspect> valid = new HashMap<>(incoming);

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Aspect aspect : valid.values().stream().toList()) {
                String problem = problemWith(aspect, valid);
                if (problem != null) {
                    NewAgeThaum.LOGGER.warn("Dropping invalid aspect {}: {}", aspect.id(), problem);
                    valid.remove(aspect.id());
                    changed = true;
                }
            }
        }
        return valid;
    }

    private static String problemWith(Aspect aspect, Map<ResourceLocation, Aspect> pool) {
        int size = aspect.components().size();
        if (size != 0 && size != 2) {
            return "components must be absent or exactly 2, found " + size;
        }
        if (size == 2 && aspect.components().get(0).equals(aspect.components().get(1))) {
            // [X, X] would double the graph edge (skewing walk randomness) and is the
            // cheapest fuel for depth-computation blowups.
            return "components must be two different aspects";
        }
        for (ResourceLocation component : aspect.components()) {
            if (!pool.containsKey(component)) {
                return "unknown component " + component;
            }
        }
        if (hasCycle(aspect, pool)) {
            return "component cycle detected";
        }
        return null;
    }

    private static boolean hasCycle(Aspect start, Map<ResourceLocation, Aspect> pool) {
        Set<ResourceLocation> seen = new HashSet<>();
        Deque<ResourceLocation> stack = new ArrayDeque<>(start.components());
        while (!stack.isEmpty()) {
            ResourceLocation current = stack.pop();
            if (current.equals(start.id())) {
                return true;
            }
            if (seen.add(current)) {
                Aspect resolved = pool.get(current);
                if (resolved != null) {
                    stack.addAll(resolved.components());
                }
            }
        }
        return false;
    }
}
