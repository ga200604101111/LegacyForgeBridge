package dev.yinghuang.legacyforgebridge.convert.shared;

import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer.Analysis;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer.StaticFieldReference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Shares immutable registry results AND the two stateful public query surfaces.
 * Never caches or shares mutable ClassNode/Frame/MethodNode objects.
 * Only the exact current source scope participates; other calls keep the original behavior.
 */
public final class SharedRegistryAnalysis {
    private SharedRegistryAnalysis() { }

    /** Implemented by the guarded binary transform, without reflective private-field access. */
    public interface Access {
        Analysis lfb$rev312AnalyzeUncached(Path source) throws IOException;
        String lfb$rev312ClassifyUncached(String name);
        Set<String> lfb$rev312HiddenUncached();
        Set<String> lfb$rev312ClassNames();
        Snapshot lfb$rev312Snapshot();
        void lfb$rev312Snapshot(Snapshot snapshot);
        void lfb$rev312ClearWorkingState();
    }

    public static final class Snapshot {
        final Analysis analysis;
        final Map<String,String> classifications;
        final Set<String> hidden;
        Snapshot(Analysis analysis, Map<String,String> classifications, Set<String> hidden) {
            this.analysis = Objects.requireNonNull(analysis);
            this.classifications = Collections.unmodifiableMap(new LinkedHashMap<>(classifications));
            this.hidden = Set.copyOf(hidden);
        }
    }

    public static Analysis analyze(LegacyRegistryAnalyzer analyzer, Path source) throws IOException {
        Access access = (Access)(Object) analyzer;
        synchronized (analyzer) {
            // Reusing an analyzer for another source must not leak its old classification snapshot.
            access.lfb$rev312Snapshot(null);
            SharedSourceSession.Session s = SharedSourceSession.current(source);
            if (s == null) return access.lfb$rev312AnalyzeUncached(source);
            s.registryRequests.increment();
            if (!s.cacheEnabled) {
                s.registryBypasses.increment(); s.registryComputations.increment();
                long start = System.nanoTime();
                try { return access.lfb$rev312AnalyzeUncached(source); }
                finally { s.registryComputeNanos.add(System.nanoTime()-start); }
            }
            CompletableFuture<Snapshot> future; boolean compute = false, reentrant = false;
            synchronized (s.registryLock) {
                future = s.registry;
                if (future == null) {
                    future = new CompletableFuture<>(); s.registry = future;
                    s.registryOwner = Thread.currentThread(); compute = true;
                } else if (!future.isDone() && s.registryOwner == Thread.currentThread()) reentrant = true;
                else {
                    s.registryHits.increment(); if (!future.isDone()) s.registryWaits.increment();
                }
            }
            if (reentrant) {
                s.registryBypasses.increment();
                return access.lfb$rev312AnalyzeUncached(source);
            }
            if (compute) {
                long start = System.nanoTime(); s.registryComputations.increment();
                try {
                    Analysis value = access.lfb$rev312AnalyzeUncached(source);
                    LinkedHashMap<String,String> kinds = new LinkedHashMap<>();
                    for (String name : access.lfb$rev312ClassNames())
                        kinds.put(name, access.lfb$rev312ClassifyUncached(name));
                    Snapshot result = new Snapshot(value, kinds, access.lfb$rev312HiddenUncached());
                    s.verifyStamp();
                    if (!safeResult(value)) {
                        // Retain semantics for any future extension with mutable constructor values.
                        s.registryBypasses.increment();
                        future.complete(null);
                        synchronized (s.registryLock) { if (s.registry == future) s.registry = null; }
                        return value;
                    }
                    future.complete(result);
                } catch (Throwable failure) {
                    s.registryFailures.increment(); future.completeExceptionally(failure);
                    synchronized (s.registryLock) { if (s.registry == future) s.registry = null; }
                    rethrow(failure); throw new AssertionError("unreachable");
                } finally {
                    s.registryComputeNanos.add(System.nanoTime()-start);
                    synchronized (s.registryLock) { if (s.registryOwner == Thread.currentThread()) s.registryOwner = null; }
                }
            }
            Snapshot result;
            try { result = future.get(); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IOException("Interrupted waiting for source registry analysis", interrupted);
            } catch (ExecutionException failed) {
                rethrow(failed.getCause()); throw new AssertionError("unreachable");
            }
            if (result == null) { s.registryBypasses.increment(); return access.lfb$rev312AnalyzeUncached(source); }
            s.ensureOpen();
            // Heavy private analysis graphs can be discarded: both public queries use the snapshot.
            access.lfb$rev312ClearWorkingState();
            access.lfb$rev312Snapshot(result);
            return result.analysis;
        }
    }

    public static String classify(LegacyRegistryAnalyzer analyzer, String name) {
        Access a = (Access)(Object) analyzer;
        Snapshot s = a.lfb$rev312Snapshot();
        // null/unknown/platform types use the original implementation, not guessed class names.
        return s != null && name != null && s.classifications.containsKey(name)
                ? s.classifications.get(name) : a.lfb$rev312ClassifyUncached(name);
    }
    public static Set<String> hidden(LegacyRegistryAnalyzer analyzer) {
        Access a = (Access)(Object) analyzer;
        Snapshot s = a.lfb$rev312Snapshot();
        return s == null ? a.lfb$rev312HiddenUncached() : s.hidden;
    }
    private static boolean safeResult(Analysis a) {
        for (var registration : a.registrations()) for (var arg : registration.constructorArguments()) {
            Object v = arg.value();
            if (v != null && !(v instanceof String || v instanceof Boolean || v instanceof Character
                    || v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long
                    || v instanceof Float || v instanceof Double || v instanceof StaticFieldReference)) return false;
        }
        return true;
    }
    private static void rethrow(Throwable failure) throws IOException {
        if (failure instanceof IOException e) throw e;
        if (failure instanceof RuntimeException e) throw e;
        if (failure instanceof Error e) throw e;
        throw new IOException("Registry analysis failed", failure);
    }
}
