package dev.yinghuang.legacyforgebridge.convert;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public record LegacyPaths(Path gameDir, Path oldModsDir, Path cacheDir, Path reportsDir, Path modsDir) {
    public static LegacyPaths resolve() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        return new LegacyPaths(
                gameDir,
                gameDir.resolve("old-mods"),
                gameDir.resolve("legacy-cache"),
                gameDir.resolve("legacy-cache").resolve("reports"),
                gameDir.resolve("mods")
        );
    }
}
