package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * rev240 replacement of the rev239 helper; the class name preserves the existing
 * ConvertedLegacyBlock binary ABI. Production namespace: Fabric intermediary 1.21.11.
 *
 * BlockState caches its FluidState before generated client liquid rules load.
 * Updating only Block#getFluidState does not invalidate that cache. Admit WATER
 * only from the source-proven rule, publish all metadata mappings, then invoke
 * BlockState#initShapeCache on every existing state. Never hide the fallback
 * model unless BlockState#getFluidState actually returns the expected water.
 * No world writes, server simulation, registry replacement, or mod-name dispatch.
 */
public final class Rev239VanillaLiquidBridge {
    private static final String CARRIER =
            "dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock";
    private record Binding(Object block, Object fluid) {}
    private static final Map<Object, Binding> WATER_STATES = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Api api;
    private static volatile MethodHandle superFluidState;
    private static volatile MethodHandle superRenderType;

    private Rev239VanillaLiquidBridge() {}

    /** Exact replacement call site for Rev233LiquidCompat.registerLiquid. */
    public static void registerLiquid(Object block, Object id, Object rule) {
        // Retain rev233 collision/selection registration and rev237 fallback tint.
        Rev233LiquidCompat.registerLiquid(block, id, rule);
        if (block == null || id == null || rule == null) return;
        try {
            Object kind = rule.getClass().getMethod("get", String.class).invoke(rule, "kind");
            if (kind == null || !"WATER".equals(
                    kind.getClass().getMethod("getAsString").invoke(kind))) return;
            Api a = api();
            if (!a.carrier.isInstance(block)) return;
            Object manager = a.getStateManager.invoke(block);
            Object values = a.getStates.invoke(manager);
            if (!(values instanceof Iterable<?> iterable)) {
                throw new IllegalStateException("StateManager#getStates is not iterable");
            }
            List<Object> states = new ArrayList<>();
            List<Binding> bindings = new ArrayList<>();
            for (Object state : iterable) {
                int meta = ((Number) a.legacyMeta.invoke(null, state)).intValue();
                vanillaStateIndexForTest(meta); // Reject bad metadata, do not clamp silently.
                states.add(state);
                bindings.add(new Binding(block, a.waterStates[meta]));
            }
            if (states.isEmpty()) throw new IllegalStateException("No carrier block states");
            // Publish every mapping BEFORE cache initialization calls back into the block.
            for (int i = 0; i < states.size(); i++) WATER_STATES.put(states.get(i), bindings.get(i));
            int refreshed = 0;
            for (Object state : states) {
                try {
                    a.initCache.invoke(state);
                    if (a.cachedFluid.invoke(state) != WATER_STATES.get(state).fluid()) {
                        throw new IllegalStateException("Cached FluidState did not refresh");
                    }
                    refreshed++;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    warn("cache:" + id, "Failed to refresh water cache for " + id
                            + "; states without real water retain their block model", failure);
                }
            }
            System.getLogger("LegacyForgeBridge/liquid").log(System.Logger.Level.INFO,
                    "rev240 water cache: " + id + ", refreshed=" + refreshed + "/" + states.size());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn("register:" + id, "Unable to initialize water rendering for " + id
                    + "; retaining visible fallback", failure);
        }
    }

    /** Called by the existing ConvertedLegacyBlock#getFluidState override. */
    public static Object fluidStateOrSuper(Object block, Object state) {
        Binding binding = state == null ? null : WATER_STATES.get(state);
        if (binding != null && binding.block() == block) return binding.fluid();
        try {
            return superFluidState(block, state);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            throw new IllegalStateException("Unable to resolve carrier superclass FluidState", failure);
        }
    }

    /**
     * Test the renderer's CACHED state, not fluidStateOrSuper(block,state).
     * Calling the block method here would recreate the rev239 invisible+EMPTY bug.
     */
    public static Object renderTypeOrSuper(Object block, Object state) {
        Binding binding = state == null ? null : WATER_STATES.get(state);
        if (binding != null && binding.block() == block) {
            try {
                Api a = api();
                if (a.cachedFluid.invoke(state) == binding.fluid()) return a.invisible;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                warn("render-cache", "Cannot verify cached water; retaining block model", failure);
            }
        }
        try {
            return superRenderType(block, state);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            throw new IllegalStateException("Unable to resolve carrier superclass render type", failure);
        }
    }

    // Preserve the rev239 diagnostic ABI and exact vanilla LiquidBlock level rules.
    public static int vanillaStateIndexForTest(int meta) {
        if (meta < 0 || meta > 15) throw new IllegalArgumentException("legacy meta outside 0..15");
        return Math.min(meta, 8);
    }
    public static int flowingAmountForTest(int meta) {
        int index = vanillaStateIndexForTest(meta);
        return index == 0 || index == 8 ? 8 : 8 - index;
    }
    public static boolean fallingForTest(int meta) {
        return vanillaStateIndexForTest(meta) == 8;
    }

    private static Api api() throws ReflectiveOperationException {
        Api a = api;
        if (a == null) synchronized (Rev239VanillaLiquidBridge.class) {
            a = api;
            if (a == null) api = a = new Api();
        }
        return a;
    }

    /** Cached, exact-signature lookups; no ambiguous name/arity method scanning. */
    private static final class Api {
        final Class<?> carrier = Class.forName(CARRIER);
        final Method getStateManager;
        final Method getStates;
        final Method legacyMeta;
        final Method initCache;
        final Method cachedFluid;
        final Object invisible;
        final Object[] waterStates = new Object[16];

        Api() throws ReflectiveOperationException {
            Class<?> block = Class.forName("net.minecraft.class_2248");
            Class<?> state = Class.forName("net.minecraft.class_2680");
            Class<?> abstractState = Class.forName("net.minecraft.class_4970$class_4971");
            Class<?> manager = Class.forName("net.minecraft.class_2689");
            Class<?> flowable = Class.forName("net.minecraft.class_3609");
            getStateManager = block.getMethod("method_9595");
            getStates = manager.getMethod("method_11662");
            legacyMeta = carrier.getMethod("legacyMeta", state);
            initCache = abstractState.getMethod("method_26200");
            cachedFluid = abstractState.getMethod("method_26227");
            invisible = Class.forName("net.minecraft.class_2464").getField("field_11455").get(null);
            Object water = Class.forName("net.minecraft.class_3612").getField("field_15910").get(null);
            Method still = flowable.getMethod("method_15729", boolean.class);
            Method flowing = flowable.getMethod("method_15728", int.class, boolean.class);
            waterStates[0] = still.invoke(water, false);
            for (int meta = 1; meta < 8; meta++) waterStates[meta] = flowing.invoke(water, 8 - meta, false);
            Object falling = flowing.invoke(water, 8, true);
            for (int meta = 8; meta < 16; meta++) waterStates[meta] = falling;
            for (Object fluid : waterStates) {
                if (fluid == null) throw new IllegalStateException("Vanilla water factory returned null");
            }
        }
    }

    private static Object superFluidState(Object block, Object state) throws Throwable {
        MethodHandle handle = superFluidState;
        if (handle == null) synchronized (Rev239VanillaLiquidBridge.class) {
            handle = superFluidState;
            if (handle == null) superFluidState = handle = superMethod("method_9545", "net.minecraft.class_3610");
        }
        return handle.invoke(block, state);
    }
    private static Object superRenderType(Object block, Object state) throws Throwable {
        MethodHandle handle = superRenderType;
        if (handle == null) synchronized (Rev239VanillaLiquidBridge.class) {
            handle = superRenderType;
            if (handle == null) superRenderType = handle = superMethod("method_9604", "net.minecraft.class_2464");
        }
        return handle.invoke(block, state);
    }
    private static MethodHandle superMethod(String name, String result) throws ReflectiveOperationException {
        Class<?> carrier = Class.forName(CARRIER);
        return MethodHandles.privateLookupIn(carrier, MethodHandles.lookup()).findSpecial(
                Class.forName("net.minecraft.class_2248"), name,
                MethodType.methodType(Class.forName(result), Class.forName("net.minecraft.class_2680")), carrier);
    }
    @SuppressWarnings("removal")
    private static void rethrowFatal(Throwable failure) {
        Throwable root = failure;
        while (root instanceof InvocationTargetException && root.getCause() != null) root = root.getCause();
        if (root instanceof VirtualMachineError fatal) throw fatal;
        if (root instanceof ThreadDeath fatal) throw fatal;
    }
    private static void warn(String key, String message, Throwable failure) {
        rethrowFatal(failure);
        if (WARNED.add(key)) System.getLogger("LegacyForgeBridge/liquid")
                .log(System.Logger.Level.WARNING, "rev240: " + message, failure);
    }
}
