package dev.longyu.legacyforgebridge.behavior;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Registrations are emitted by the converted mod. No source JAR or JSON is interpreted here. */
public final class LegacyBehaviorRegistry {
    public record ItemDefinition(String mod, LegacyBehaviorApi.Item item, Set<String> hooks) { }
    public record EventDefinition(String mod, String kind, LegacyBehaviorApi.EventProgram program) { }
    private static final Map<String,ItemDefinition> ITEMS=new ConcurrentHashMap<>();
    private static final List<EventDefinition> EVENTS=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Set<String> MODS=ConcurrentHashMap.newKeySet();
    private LegacyBehaviorRegistry() { }
    public static boolean begin(String mod) {
        if(!MODS.add(mod))return false;
        LegacyBehaviorApi.begin(mod,(key,args)->key);return true;
    }
    public static void finish() { LegacyBehaviorApi.end(); }
    public static void registerItem(String id,LegacyBehaviorApi.Item item,String hooks) {
        ITEMS.put(id,new ItemDefinition(id.substring(0,id.indexOf(':')),item,Set.of(hooks.split(","))));
    }
    public static void registerEvent(String mod,String kind,LegacyBehaviorApi.EventProgram program) {
        EVENTS.add(new EventDefinition(mod,kind,program));
    }
    public static ItemDefinition item(String id) { return ITEMS.get(id); }
    public static List<EventDefinition> events(String kind) { return EVENTS.stream().filter(e->e.kind().equals(kind)).toList(); }
    public static int itemCount(String mod) { return (int)ITEMS.values().stream().filter(i->i.mod().equals(mod)).count(); }
    public static void removeMod(String mod) { ITEMS.entrySet().removeIf(e->e.getValue().mod().equals(mod));EVENTS.removeIf(e->e.mod().equals(mod));MODS.remove(mod); }
}
