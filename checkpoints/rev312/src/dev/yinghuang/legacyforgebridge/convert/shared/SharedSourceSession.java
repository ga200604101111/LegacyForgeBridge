package dev.yinghuang.legacyforgebridge.convert.shared;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.LongAdder;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * One source-scoped, bounded, read-only archive cache. No mod classes are defined or run.
 * Stream consumers get independent cursors over private bytes, never a shared ASM tree.
 * Scope propagation is explicit; this class does not create threads or parallelize writers.
 */
public final class SharedSourceSession {
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private static final long MIB = 1024L * 1024L;
    private static final long MAX_ENTRY = 8L * MIB;
    private static final int MAX_INDEX_ENTRIES = 100_000;
    private SharedSourceSession() { }

    public static Scope open(Path source, String expectedSha256) throws IOException {
        Objects.requireNonNull(source, "source");
        Path canonical = source.toAbsolutePath().normalize().toRealPath();
        String digest = digest(canonical);
        if (expectedSha256 != null && !digest.equalsIgnoreCase(expectedSha256))
            throw new IOException("Source SHA-256 changed before shared analysis: " + source.getFileName());
        Session prior = CURRENT.get();
        if (prior != null && !prior.closed && prior.path.equals(canonical) && prior.sha256.equals(digest)) {
            prior.verifyStamp();
            return new Scope(prior, prior, false);
        }
        boolean enabled = Boolean.parseBoolean(System.getProperty("legacyforgebridge.sharedAnalysis", "true"));
        Session session = new Session(canonical, digest, enabled);
        CURRENT.set(session);
        return new Scope(prior, session, true);
    }

    /** Transparently falls through for staging files, unrelated JARs, and non-scoped callers. */
    public static InputStream openEntry(JarFile jar, ZipEntry entry) throws IOException {
        Session session = CURRENT.get();
        if (session == null || !session.cacheEnabled || !session.matches(jar.getName()))
            return jar.getInputStream(entry);
        return session.openEntry(jar, entry);
    }

    /** Called inside the original converter's failure handling, before candidate publication. */
    public static void beforePublication() throws IOException {
        Session session = CURRENT.get();
        if (session != null) {
            session.verifyStamp();
            if (!session.sha256.equals(digest(session.path)))
                throw new IOException("Source content changed during conversion: " + session.path.getFileName());
        }
    }

    static Session current(Path source) throws IOException {
        Session session = CURRENT.get();
        if (session == null || !session.matches(source.toString())) return null;
        session.verifyStamp();
        return session;
    }

    public static boolean active() { return CURRENT.get() != null; }

    public static final class Scope implements AutoCloseable {
        private final Session previous;
        private final Session session;
        private final boolean owner;
        private final Thread creatingThread = Thread.currentThread();
        private boolean closed;
        private Scope(Session previous, Session session, boolean owner) {
            this.previous = previous; this.session = session; this.owner = owner;
        }
        public String sourceSha256() { return session.sha256; }
        public List<String> entryNames() { return session.entryNames; }
        public Map<String, Long> counters() { return session.counters(); }

        /** Suitable only for independent read-only tasks; caller owns executor and joins tasks. */
        public <T> Callable<T> bind(Callable<T> task) {
            Objects.requireNonNull(task, "task");
            return () -> {
                session.ensureOpen();
                Session old = CURRENT.get();
                CURRENT.set(session);
                try { return task.call(); }
                finally { if (old == null) CURRENT.remove(); else CURRENT.set(old); }
            };
        }
        public void writeReport(Path file, boolean conversionReturned) throws IOException {
            session.writeReport(file, conversionReturned);
        }
        @Override public void close() {
            if (closed) return;
            if (Thread.currentThread() != creatingThread || CURRENT.get() != session)
                throw new IllegalStateException("Shared analysis scopes must close on their thread in LIFO order");
            closed = true;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            if (owner) session.release();
        }
    }

    private record Stamp(long size, FileTime modified, Object key) {
        static Stamp read(Path path) throws IOException {
            BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class);
            return new Stamp(a.size(), a.lastModifiedTime(), a.fileKey());
        }
    }
    private record EntryInfo(long size, long crc, boolean directory) { }

    static final class Session {
        final Path path;
        final String sha256;
        final Stamp stamp;
        final boolean cacheEnabled;
        final String disabledReason;
        final List<String> entryNames;
        final Map<String, EntryInfo> index;
        final long budget;
        final Object registryLock = new Object();
        java.util.concurrent.CompletableFuture<SharedRegistryAnalysis.Snapshot> registry;
        Thread registryOwner;
        final LongAdder registryRequests = new LongAdder(), registryComputations = new LongAdder();
        final LongAdder registryHits = new LongAdder(), registryWaits = new LongAdder(), registryFailures = new LongAdder();
        final LongAdder registryBypasses = new LongAdder(), registryComputeNanos = new LongAdder();
        final LongAdder sourceRequests = new LongAdder(), sourceHits = new LongAdder(), sourceMisses = new LongAdder();
        final LongAdder sourceBytesLoaded = new LongAdder(), sourceBytesAvoided = new LongAdder(), sourceBypasses = new LongAdder();
        final Map<String, byte[]> bytes = new LinkedHashMap<>();
        private long retainedBytes, peakBytes;
        final long started = System.nanoTime();
        volatile boolean closed;
        Session(Path path, String sha256, boolean enabled) throws IOException {
            this.path = path; this.sha256 = sha256; this.stamp = Stamp.read(path);
            long requested = Long.getLong("legacyforgebridge.sharedAnalysis.maxMiB", 64L);
            this.budget = Math.min(Math.max(0L, Math.min(256L, requested)) * MIB,
                    Math.max(0L, Runtime.getRuntime().maxMemory() / 16L));
            Map<String, EntryInfo> collected = new LinkedHashMap<>();
            List<String> names = new ArrayList<>(); String reason = enabled ? "" : "disabled-by-property";
            if (enabled) try (JarFile jar = new JarFile(path.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var e = entries.nextElement();
                    names.add(e.getName());
                    if (collected.putIfAbsent(e.getName(), new EntryInfo(e.getSize(), e.getCrc(), e.isDirectory())) != null)
                        reason = "duplicate-archive-entry";
                    if (collected.size() > MAX_INDEX_ENTRIES) { reason = "archive-index-budget"; break; }
                }
            }
            this.cacheEnabled = enabled && reason.isEmpty(); this.disabledReason = reason;
            this.index = Collections.unmodifiableMap(collected); this.entryNames = List.copyOf(names);
            verifyStamp();
        }
        boolean matches(String filename) {
            try {
                Path candidate = Path.of(filename).toAbsolutePath().normalize();
                // Original converter passes the same absolute or relative path. No directory scan.
                return candidate.equals(path) || candidate.toRealPath().equals(path);
            } catch (IOException | RuntimeException ignored) { return false; }
        }
        void ensureOpen() throws IOException { if (closed) throw new IOException("Shared analysis scope is closed"); }
        void verifyStamp() throws IOException {
            ensureOpen();
            if (!stamp.equals(Stamp.read(path))) throw new IOException("Source changed during analysis: " + path.getFileName());
        }
        InputStream openEntry(JarFile jar, ZipEntry entry) throws IOException {
            ensureOpen(); sourceRequests.increment();
            EntryInfo info = entry == null ? null : index.get(entry.getName());
            if (info == null || info.directory || info.size < 0 || info.size > MAX_ENTRY || info.size > budget
                    || info.size != entry.getSize() || info.crc != entry.getCrc()) {
                sourceBypasses.increment(); return jar.getInputStream(entry);
            }
            synchronized (bytes) {
                ensureOpen();
                byte[] cached = bytes.get(entry.getName());
                if (cached != null) {
                    sourceHits.increment(); sourceBytesAvoided.add(cached.length);
                    return new ByteArrayInputStream(cached);
                }
                if (retainedBytes + info.size > budget) {
                    sourceBypasses.increment(); return jar.getInputStream(entry);
                }
                byte[] loaded;
                try (InputStream in = jar.getInputStream(entry)) {
                    loaded = in.readNBytes(Math.toIntExact(info.size) + 1);
                }
                if (loaded.length != info.size)
                    throw new IOException("Archive entry size changed: " + entry.getName());
                bytes.put(entry.getName(), loaded); retainedBytes += loaded.length;
                peakBytes = Math.max(peakBytes, retainedBytes);
                sourceMisses.increment(); sourceBytesLoaded.add(loaded.length);
                return new ByteArrayInputStream(loaded);
            }
        }
        Map<String, Long> counters() {
            LinkedHashMap<String,Long> c = new LinkedHashMap<>();
            c.put("registryRequests", registryRequests.sum()); c.put("registryComputations", registryComputations.sum());
            c.put("registryHits", registryHits.sum()); c.put("registryWaits", registryWaits.sum());
            c.put("registryFailures", registryFailures.sum()); c.put("registryBypasses", registryBypasses.sum());
            c.put("registryComputeNanos", registryComputeNanos.sum());
            c.put("sourceEntryRequests", sourceRequests.sum()); c.put("sourceEntryLoads", sourceMisses.sum());
            c.put("sourceEntryHits", sourceHits.sum()); c.put("sourceEntryBypasses", sourceBypasses.sum());
            c.put("sourceBytesLoaded", sourceBytesLoaded.sum()); c.put("sourceBytesAvoided", sourceBytesAvoided.sum());
            synchronized (bytes) {
                c.put("retainedSourceBytes", retainedBytes); c.put("peakSourceBytes", peakBytes);
                c.put("sourceCacheLimitBytes", budget);
            }
            return Collections.unmodifiableMap(c);
        }
        void release() {
            closed = true;
            synchronized (bytes) { bytes.clear(); retainedBytes = 0; }
            synchronized (registryLock) { registry = null; registryOwner = null; }
        }
        void writeReport(Path file, boolean returned) throws IOException {
            Map<String,Long> metrics = counters();
            StringBuilder j = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"revision\": 312,\n");
            j.append("  \"sourceFile\": ").append(quote(path.getFileName().toString())).append(",\n");
            j.append("  \"sourceSha256\": ").append(quote(sha256)).append(",\n");
            j.append("  \"cacheEnabled\": ").append(cacheEnabled).append(",\n");
            j.append("  \"disabledReason\": ").append(quote(disabledReason)).append(",\n");
            j.append("  \"conversionMethodReturned\": ").append(returned).append(",\n");
            j.append("  \"durationNanos\": ").append(System.nanoTime()-started).append(",\n");
            j.append("  \"counters\": {\n"); int n = 0;
            for (var e : metrics.entrySet()) {
                if (n++ > 0) j.append(",\n");
                j.append("    ").append(quote(e.getKey())).append(": ").append(e.getValue());
            }
            j.append("\n  }\n}\n");
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = Files.createTempFile(file.toAbsolutePath().getParent(), ".source-analysis-", ".tmp");
            try {
                Files.writeString(tmp, j.toString(), StandardCharsets.UTF_8);
                try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(tmp); }
        }
    }
    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) switch(c) {
            case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\");
            case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
            default -> { if (c < 32) b.append(String.format(Locale.ROOT,"\\u%04x",(int)c)); else b.append(c); }
        }
        return b.append('"').toString();
    }
    public static String digest(Path source) throws IOException {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(Files.newInputStream(source), d)) { in.transferTo(OutputStream.nullOutputStream()); }
            return HexFormat.of().formatHex(d.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
