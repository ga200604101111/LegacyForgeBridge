package dev.yinghuang.legacyforgebridge.behavior;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Candidate-owned registrations, published atomically only after source constructors succeed. */
public final class LegacyBehaviorRegistry {
    public record ItemDefinition(String mod, LegacyBehaviorApi.Item item, Set<String> hooks, boolean presentationOnly) {
        public ItemDefinition { hooks = Set.copyOf(hooks); Objects.requireNonNull(item); }
        public ItemDefinition(String mod, LegacyBehaviorApi.Item item, Set<String> hooks) { this(mod,item,hooks,false); }
    }
    public record EventDefinition(String mod, String kind, String targetItemId, LegacyBehaviorApi.EventProgram program) {
        public EventDefinition(String mod, String kind, LegacyBehaviorApi.EventProgram program) { this(mod,kind,null,program); }
    }
    private record Pending(String mod, Map<String, ItemDefinition> items, List<EventDefinition> events) { }
    private static final Map<String,ItemDefinition> ITEMS = new ConcurrentHashMap<>();
    private static final List<EventDefinition> EVENTS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Set<String> MODS = ConcurrentHashMap.newKeySet();
    private static final Set<String> INITIALIZING = new HashSet<>();
    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();
    private LegacyBehaviorRegistry() { }

    public static synchronized boolean begin(String mod) {
        if (MODS.contains(mod)) return false;
        if (PENDING.get() != null || INITIALIZING.contains(mod)) throw new IllegalStateException("Nested/concurrent source bootstrap: " + mod);
        LegacyBehaviorApi.begin(mod, (key,args) -> key);
        PENDING.set(new Pending(mod, new LinkedHashMap<>(), new ArrayList<>()));
        INITIALIZING.add(mod);
        return true;
    }
    public static synchronized void finish() {
        Pending pending = pending();
        for (String id : pending.items().keySet()) {
            if (ITEMS.containsKey(id)) throw new IllegalArgumentException("Behavior identity already registered: " + id);
        }
        ITEMS.putAll(pending.items());
        EVENTS.addAll(pending.events());
        MODS.add(pending.mod());
        abort();
    }
    public static synchronized void abort() {
        Pending pending = PENDING.get();
        if (pending != null) {
            INITIALIZING.remove(pending.mod());
            PENDING.remove();
            LegacyBehaviorApi.end();
        }
    }
    private static Pending pending() {
        Pending pending = PENDING.get();
        if (pending == null) throw new IllegalStateException("Behavior registration outside bootstrap");
        return pending;
    }
    public static void registerItem(String id, LegacyBehaviorApi.Item item, String hooks) {
        Pending pending = pending();
        if (!id.startsWith(pending.mod() + ':')) throw new IllegalArgumentException("Behavior item owner mismatch");
        var definition = new ItemDefinition(pending.mod(), item, hooks.isEmpty() ? Set.of() : Set.of(hooks.split(",")), false);
        if (pending.items().putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate behavior item " + id);
    }
    public static void registerPresentationItem(String id, LegacyBehaviorApi.Item item) {
        Pending pending = pending();
        if (!id.startsWith(pending.mod() + ':')) throw new IllegalArgumentException("Behavior presentation item owner mismatch");
        var definition = new ItemDefinition(pending.mod(), Objects.requireNonNull(item), Set.of(), true);
        if (pending.items().putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate behavior item " + id);
    }
    /**
     * Returns an item already constructed in the current bootstrap transaction.
     * Generated event adapters use this to bind a source listener back to the
     * exact source Item instance that registered itself, instead of invoking its
     * legacy constructor a second time. The value is never visible outside the
     * pending transaction.
     */
    public static ItemDefinition bootstrapItem(String id) {
        Pending pending = pending();
        ItemDefinition definition = pending.items().get(id);
        if (definition == null) throw new IllegalArgumentException("Behavior bootstrap item not registered yet: " + id);
        if (!definition.mod().equals(pending.mod())) throw new IllegalArgumentException("Behavior bootstrap item owner mismatch");
        return definition;
    }
    public static void registerEvent(String mod, String kind, LegacyBehaviorApi.EventProgram program) {
        registerEvent(mod,kind,null,program);
    }
    public static void registerEvent(String mod, String kind, String targetItemId, LegacyBehaviorApi.EventProgram program) {
        Pending pending = pending();
        if (!pending.mod().equals(mod)) throw new IllegalArgumentException("Behavior event owner mismatch");
        if (targetItemId != null) {
            if (!targetItemId.startsWith(pending.mod() + ':')) throw new IllegalArgumentException("Behavior event target owner mismatch");
            if (!pending.items().containsKey(targetItemId)) throw new IllegalArgumentException("Behavior event target not registered yet: " + targetItemId);
        }
        pending.events().add(new EventDefinition(mod, kind, targetItemId, Objects.requireNonNull(program)));
    }
    public static synchronized ItemDefinition item(String id) { return ITEMS.get(id); }
    public static synchronized Optional<String> identity(LegacyBehaviorApi.Item item) {
        return ITEMS.entrySet().stream().filter(entry -> entry.getValue().item() == item).map(Map.Entry::getKey).findFirst();
    }
    public static synchronized List<EventDefinition> events(String kind) {
        return EVENTS.stream().filter(event -> event.kind().equals(kind)).toList();
    }
    public static synchronized int itemCount(String mod) {
        return (int) ITEMS.values().stream().filter(item -> item.mod().equals(mod) && !item.presentationOnly()).count();
    }
    public static synchronized void removeMod(String mod) {
        ITEMS.entrySet().removeIf(entry -> entry.getValue().mod().equals(mod));
        EVENTS.removeIf(event -> event.mod().equals(mod)); MODS.remove(mod);
    }
}
