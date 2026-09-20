package dev.yinghuang.legacyforgebridge.compat;

/** Read bounds, not a claim about the legacy mod's configurable recursion distance. */
public record LegacyMimicReadWindow(long minX, long minY, long minZ,
                                    long maxX, long maxY, long maxZ) {
    public LegacyMimicReadWindow {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted mimic read window");
        }
    }

    /** Exact vanilla 1.21.11 RenderSectionRegion: center section plus one section on every axis. */
    public static LegacyMimicReadWindow sectionSnapshot(int x, int y, int z) {
        long bx = ((long) (x >> 4)) << 4;
        long by = ((long) (y >> 4)) << 4;
        long bz = ((long) (z >> 4)) << 4;
        return new LegacyMimicReadWindow(bx - 16, by - 16, bz - 16, bx + 31, by + 31, bz + 31);
    }

    /** Unknown renderer views never inherit vanilla's wider section-snapshot allowance. */
    public static LegacyMimicReadWindow immediateNeighbors(int x, int y, int z) {
        return new LegacyMimicReadWindow((long) x - 1, (long) y - 1, (long) z - 1,
                (long) x + 1, (long) y + 1, (long) z + 1);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
