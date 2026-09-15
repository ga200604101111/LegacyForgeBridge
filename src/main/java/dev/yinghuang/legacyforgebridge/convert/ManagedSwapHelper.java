package dev.longyu.legacyforgebridge.convert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Tiny JDK-only helper launched by the main process when Windows keeps an already-loaded managed
 * candidate JAR open. It waits for the Minecraft process to exit, then swaps or removes the file
 * before the next launch.
 */
public final class ManagedSwapHelper {
    private static final int RETRIES = 120;
    private static final long RETRY_DELAY_MILLIS = 250L;

    private ManagedSwapHelper() {
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            return;
        }

        String operation = args[0];
        long parentPid;
        try {
            parentPid = Long.parseLong(args[1]);
        } catch (NumberFormatException ignored) {
            return;
        }

        waitForParent(parentPid);

        try {
            if (operation.equals("replace") && args.length >= 4) {
                replace(Path.of(args[2]), Path.of(args[3]));
            } else if (operation.equals("delete")) {
                delete(Path.of(args[2]));
            }
        } catch (IOException failure) {
            Path marker = operation.equals("replace") && args.length >= 4
                    ? Path.of(args[2]).resolveSibling("managed-swap-error.txt")
                    : Path.of(args[2]).resolveSibling("managed-swap-error.txt");
            try {
                Files.writeString(
                        marker,
                        failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()) + "\n",
                        StandardCharsets.UTF_8
                );
            } catch (IOException ignored) {
            }
        }
    }

    static void replace(Path source, Path target) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < RETRIES; attempt++) {
            try {
                Files.createDirectories(target.getParent());
                try {
                    Files.move(
                            source,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException failure) {
                lastFailure = failure;
                sleep();
            }
        }
        throw lastFailure == null ? new IOException("Managed candidate replacement failed") : lastFailure;
    }

    static void delete(Path target) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < RETRIES; attempt++) {
            try {
                Files.deleteIfExists(target);
                return;
            } catch (IOException failure) {
                lastFailure = failure;
                sleep();
            }
        }
        throw lastFailure == null ? new IOException("Managed candidate deletion failed") : lastFailure;
    }

    private static void waitForParent(long parentPid) {
        ProcessHandle.of(parentPid).ifPresent(parent -> {
            while (parent.isAlive()) {
                sleep();
            }
        });
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
