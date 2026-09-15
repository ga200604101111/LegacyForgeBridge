package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.convert.pass.GeneratedModEntrypointPass;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.jar.JarFile;

/** Owns LFB-generated JARs in mods/ and safely replaces loaded files across launches. */
public final class ManagedCandidateInstaller {
    /** Legacy alpha.19-and-earlier fallback for state records that did not persist a managed path. */
    public static final String MANAGED_PREFIX = "legacyforgebridge-converted-";
    private static final String GENERATED_CLASS_PREFIX = "dev/longyu/legacyforgebridge/generated/";

    private final Path modsDir;
    private final Path cacheDir;
    private final boolean helperEnabled;

    public ManagedCandidateInstaller(Path modsDir, Path cacheDir) {
        this(modsDir, cacheDir, true);
    }

    ManagedCandidateInstaller(Path modsDir, Path cacheDir, boolean helperEnabled) {
        this.modsDir = modsDir;
        this.cacheDir = cacheDir;
        this.helperEnabled = helperEnabled;
    }

    /**
     * Resolves the historical managed name. New conversions use the candidate's source-derived
     * filename directly (for example RPGTool1-1.1-1.7.10-lfb.jar).
     */
    public Path managedJar(String fabricId) {
        String safe = fabricId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (safe.isBlank()) {
            safe = "legacy_mod";
        }
        return modsDir.resolve(MANAGED_PREFIX + safe + ".jar");
    }

    /**
     * Loader safety is about class-path safety, not feature completeness. A candidate may be a
     * resource-only or partially semantic port and therefore have no converted-content manifest at
     * all. It is safe to hand to Fabric when it has modern metadata, the generated LFB entrypoint
     * marker/class, and no surviving source/Forge classes outside the generated namespace.
     */
    public boolean isLoaderSafeCandidate(Path candidate) throws IOException {
        if (!Files.isRegularFile(candidate)) {
            return false;
        }
        boolean hasFabricMetadata = false;
        boolean hasGeneratedMarker = false;
        boolean hasGeneratedClass = false;
        boolean hasUnexpectedClass = false;

        try (JarFile jar = new JarFile(candidate.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.equals("fabric.mod.json")) {
                    hasFabricMetadata = true;
                } else if (name.equals(GeneratedModEntrypointPass.MARKER_PATH)) {
                    hasGeneratedMarker = true;
                } else if (name.endsWith(".class")) {
                    if (name.startsWith(GENERATED_CLASS_PREFIX)) {
                        hasGeneratedClass = true;
                    } else {
                        // Any class outside LFB's generated namespace is source bytecode that has
                        // not yet been semantically migrated. Never put that on Fabric's classpath.
                        hasUnexpectedClass = true;
                    }
                }
            }
        }

        return hasFabricMetadata
                && hasGeneratedMarker
                && hasGeneratedClass
                && !hasUnexpectedClass;
    }

    /**
     * Stages a converted artifact under the exact source-derived candidate filename. The fabricId
     * parameter remains for source compatibility with the manager API but no longer controls the
     * user's file name.
     */
    public StageResult stage(Path candidate, String fabricId, boolean currentlyLoaded) throws IOException {
        Files.createDirectories(modsDir);
        Files.createDirectories(cacheDir.resolve("pending"));

        Path target = modsDir.resolve(candidate.getFileName().toString());
        String desiredHash = Hashing.sha256(candidate);
        String currentHash = Files.isRegularFile(target) ? Hashing.sha256(target) : "";
        if (desiredHash.equalsIgnoreCase(currentHash)) {
            return new StageResult(target, desiredHash, false, false, false, true,
                    "Managed converted mod already matches the desired output.");
        }

        if (currentlyLoaded && Files.exists(target)) {
            Path pending = cacheDir.resolve("pending").resolve(target.getFileName() + ".pending.jar");
            copyAtomically(candidate, pending);
            boolean helperScheduled = scheduleHelper("replace", pending, target);
            if (!helperScheduled && helperEnabled) {
                registerShutdownReplace(pending, target);
            }
            return new StageResult(
                    target,
                    desiredHash,
                    true,
                    true,
                    true,
                    helperScheduled,
                    helperScheduled
                            ? "Updated converted mod is pending an automatic post-exit swap."
                            : "Updated converted mod is pending; post-exit helper was unavailable and shutdown-hook fallback was registered."
            );
        }

        copyAtomically(candidate, target);
        return new StageResult(
                target,
                desiredHash,
                true,
                false,
                true,
                true,
                "Converted mod was staged as " + target.getFileName() + " in mods/ for the next Minecraft launch."
        );
    }

    public RemovalResult remove(Path target, boolean currentlyLoaded) throws IOException {
        if (target == null || !Files.exists(target)) {
            return new RemovalResult(false, false, false, "Managed converted mod was already absent.");
        }

        if (currentlyLoaded) {
            boolean helperScheduled = scheduleHelper("delete", target, null);
            if (!helperScheduled && helperEnabled) {
                target.toFile().deleteOnExit();
            }
            return new RemovalResult(
                    true,
                    true,
                    helperScheduled,
                    helperScheduled
                            ? "Managed converted mod will be removed automatically after Minecraft exits."
                            : "Managed converted mod removal is deferred until JVM exit."
            );
        }

        Files.deleteIfExists(target);
        return new RemovalResult(true, false, true, "Managed converted mod was removed immediately.");
    }

    private static void copyAtomically(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean scheduleHelper(String operation, Path sourceOrTarget, Path replacementTarget) {
        if (!helperEnabled) {
            return false;
        }
        try {
            Path codeSource = locateOwnClasspath();
            if (codeSource == null || !Files.exists(codeSource)) {
                return false;
            }

            Path javaHome = Path.of(System.getProperty("java.home"));
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path javaExecutable = javaHome.resolve("bin").resolve(windows ? "javaw.exe" : "java");
            if (!Files.isRegularFile(javaExecutable)) {
                javaExecutable = javaHome.resolve("bin").resolve(windows ? "java.exe" : "java");
            }
            if (!Files.isRegularFile(javaExecutable)) {
                return false;
            }

            var command = new java.util.ArrayList<String>();
            command.add(javaExecutable.toString());
            command.add("-cp");
            command.add(codeSource.toString());
            command.add(ManagedSwapHelper.class.getName());
            command.add(operation);
            command.add(Long.toString(ProcessHandle.current().pid()));
            command.add(sourceOrTarget.toAbsolutePath().normalize().toString());
            if (replacementTarget != null) {
                command.add(replacementTarget.toAbsolutePath().normalize().toString());
            }

            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.warn("Unable to schedule managed converted-mod post-exit helper", exception);
            return false;
        }
    }

    private static Path locateOwnClasspath() {
        try {
            var container = FabricLoader.getInstance().getModContainer(LegacyForgeBridge.MOD_ID);
            if (container.isPresent() && container.get().getOrigin().getKind() == ModOrigin.Kind.PATH) {
                for (Path path : container.get().getOrigin().getPaths()) {
                    if (Files.isRegularFile(path)) {
                        return path.toAbsolutePath().normalize();
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            URI codeSourceUri = ManagedCandidateInstaller.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            return Path.of(codeSourceUri).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void registerShutdownReplace(Path pending, Path target) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ManagedSwapHelper.replace(pending, target);
            } catch (IOException ignored) {
            }
        }, "LegacyForgeBridge-managed-swap"));
    }

    public record StageResult(
            Path managedJar,
            String desiredSha256,
            boolean changed,
            boolean pendingSwap,
            boolean restartRequired,
            boolean automaticSwapScheduled,
            String message
    ) {
    }

    public record RemovalResult(
            boolean changed,
            boolean restartRequired,
            boolean automaticRemovalScheduled,
            String message
    ) {
    }
}
