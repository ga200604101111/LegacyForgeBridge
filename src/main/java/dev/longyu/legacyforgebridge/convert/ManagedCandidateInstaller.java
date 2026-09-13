package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.convert.runtime.ConvertedContentRuntime;
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
    public static final String MANAGED_PREFIX = "legacyforgebridge-converted-";

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

    public Path managedJar(String fabricId) {
        String safe = fabricId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (safe.isBlank()) {
            safe = "legacy_mod";
        }
        return modsDir.resolve(MANAGED_PREFIX + safe + ".jar");
    }

    public boolean isLoaderSafeCandidate(Path candidate) throws IOException {
        if (!Files.isRegularFile(candidate)) {
            return false;
        }
        boolean hasFabricMetadata = false;
        boolean hasConvertedContent = false;
        boolean hasClass = false;
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
                } else if (name.equals(ConvertedContentRuntime.MANIFEST_PATH)) {
                    hasConvertedContent = true;
                } else if (name.endsWith(".class")) {
                    hasClass = true;
                    break;
                }
            }
        }
        return hasFabricMetadata && hasConvertedContent && !hasClass;
    }

    public StageResult stage(Path candidate, String fabricId, boolean currentlyLoaded) throws IOException {
        Files.createDirectories(modsDir);
        Files.createDirectories(cacheDir.resolve("pending"));

        Path target = managedJar(fabricId);
        String desiredHash = Hashing.sha256(candidate);
        String currentHash = Files.isRegularFile(target) ? Hashing.sha256(target) : "";
        if (desiredHash.equalsIgnoreCase(currentHash)) {
            return new StageResult(target, desiredHash, false, false, false, true,
                    "Managed candidate already matches the desired conversion output.");
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
                            ? "Updated candidate is pending an automatic post-exit swap."
                            : "Updated candidate is pending; post-exit helper was unavailable and shutdown-hook fallback was registered."
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
                "Managed candidate was staged in mods/ for the next Minecraft launch."
        );
    }

    public RemovalResult remove(Path target, boolean currentlyLoaded) throws IOException {
        if (target == null || !Files.exists(target)) {
            return new RemovalResult(false, false, false, "Managed candidate was already absent.");
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
                            ? "Managed candidate will be removed automatically after Minecraft exits."
                            : "Managed candidate removal is deferred until JVM exit."
            );
        }

        Files.deleteIfExists(target);
        return new RemovalResult(true, false, true, "Managed candidate was removed immediately.");
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
