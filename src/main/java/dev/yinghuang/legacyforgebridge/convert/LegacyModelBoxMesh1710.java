package dev.yinghuang.legacyforgebridge.convert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic, client-side *geometry only* conversion of source-proven 1.7.10 ModelBox cuboids.
 *
 * <p>Six faces follow the original ModelBox -> TexturedQuad vertex and UV ordering, including
 * the upside-down top quad. All part pivots and the source renderer's constant GL rotation are
 * baked once into vertices. Later renderers must only apply the remote entity's translation;
 * applying the source angle a second time would visibly corrupt the result.</p>
 *
 * <p>This pure-Java mesh does not register any entity type, invoke source impact logic, control
 * lighting, or claim runtime compatibility. It is deliberately unsuitable for animated parts,
 * mirrored parts, or unproven GL state.</p>
 */
public final class LegacyModelBoxMesh1710 {
    private static final int MAX_CUBOIDS = 64;
    private static final int MAX_ATLAS = 2048;
    private static final double EPSILON = 1.0e-10;

    public enum Side { EAST, WEST, UP, DOWN, NORTH, SOUTH }

    public record Vertex(double x, double y, double z, double u, double v,
                         double nx, double ny, double nz) {
        public Vertex {
            if (!finite(x, y, z, u, v, nx, ny, nz)
                    || u < 0 || u > 1 || v < 0 || v > 1)
                throw new IllegalArgumentException("Nonfinite/out-of-atlas model vertex");
        }
    }

    public record Quad(String part, Side side, Vertex a, Vertex b, Vertex c, Vertex d) {
        public Quad {
            if (part == null || part.isBlank() || side == null || a == null || b == null
                    || c == null || d == null)
                throw new IllegalArgumentException("Invalid model quad");
        }
    }

    public record Mesh(String texture, int atlasWidth, int atlasHeight, List<Quad> quads,
                       boolean sourceAxisAngleBaked) {
        public Mesh {
            if (texture == null || texture.isBlank() || atlasWidth <= 0 || atlasHeight <= 0
                    || quads == null || quads.isEmpty())
                throw new IllegalArgumentException("Invalid fixed model mesh");
            quads = List.copyOf(quads);
        }
        public int vertexCount() { return quads.size() * 4; }
        public int triangleCount() { return quads.size() * 2; }
    }

    private record Point(double x, double y, double z) { }
    private record Rotation(double x, double y, double z, double sin, double cos) {
        Point apply(Point p) {
            double dot = x * p.x + y * p.y + z * p.z;
            return new Point(
                    p.x * cos + (y * p.z - z * p.y) * sin + x * dot * (1 - cos),
                    p.y * cos + (z * p.x - x * p.z) * sin + y * dot * (1 - cos),
                    p.z * cos + (x * p.y - y * p.x) * sin + z * dot * (1 - cos));
        }
    }

    private LegacyModelBoxMesh1710() { }

    /** Proof is from LegacyFixedModelProjectileAnalyzer, never from a guessed mod selector. */
    public static Mesh build(LegacyFixedModelProjectileAnalyzer.Proof proof) {
        Objects.requireNonNull(proof, "proof");
        int atlasW = proof.textureWidth(), atlasH = proof.textureHeight();
        if (atlasW < 1 || atlasH < 1 || atlasW > MAX_ATLAS || atlasH > MAX_ATLAS)
            throw new IllegalArgumentException("Unbounded/missing model texture atlas");
        if (!finite(proof.scale(), proof.angle(), proof.axisX(), proof.axisY(), proof.axisZ())
                || proof.scale() <= 0 || proof.scale() > 1 || Math.abs(proof.angle()) > 360)
            throw new IllegalArgumentException("Unbounded model scale or orientation");
        if (proof.cuboids() == null || proof.cuboids().isEmpty() || proof.cuboids().size() > MAX_CUBOIDS)
            throw new IllegalArgumentException("Unbounded source cuboid count");
        String texture = proof.texture();
        if (texture == null || !texture.matches("[a-z0-9_.-]+:textures/[a-zA-Z0-9_./-]+\\.png")
                || texture.contains("..") || texture.contains("//"))
            throw new IllegalArgumentException("Unsafe or unknown source texture identity");
        double axisLength = Math.sqrt(proof.axisX() * proof.axisX()
                + proof.axisY() * proof.axisY() + proof.axisZ() * proof.axisZ());
        if (!Double.isFinite(axisLength) || axisLength < EPSILON)
            throw new IllegalArgumentException("Undefined source GL rotation axis");
        double degrees = Math.toRadians(proof.angle());
        Rotation rotation = new Rotation(proof.axisX() / axisLength, proof.axisY() / axisLength,
                proof.axisZ() / axisLength, Math.sin(degrees), Math.cos(degrees));
        List<Quad> result = new ArrayList<>(proof.cuboids().size() * 6);
        Set<String> names = new HashSet<>();
        for (var box : proof.cuboids()) {
            if (box == null || box.name() == null || box.name().isBlank() || !names.add(box.name()))
                throw new IllegalArgumentException("Missing/duplicate source model part");
            result.addAll(part(box, atlasW, atlasH, proof.scale(), rotation));
        }
        return new Mesh(texture, atlasW, atlasH, result, true);
    }

    private static List<Quad> part(LegacyFixedModelProjectileAnalyzer.Cuboid b,
                                   int atlasW, int atlasH, double scale, Rotation rotate) {
        int w = b.width(), h = b.height(), d = b.depth(), u = b.u(), v = b.v();
        if (w < 1 || h < 1 || d < 1 || w > 128 || h > 128 || d > 128 || u < 0 || v < 0
                || !finite(b.x(), b.y(), b.z(), b.pivotX(), b.pivotY(), b.pivotZ())
                || Math.abs(b.x()) > 4096 || Math.abs(b.y()) > 4096 || Math.abs(b.z()) > 4096
                || Math.abs(b.pivotX()) > 4096 || Math.abs(b.pivotY()) > 4096
                || Math.abs(b.pivotZ()) > 4096)
            throw new IllegalArgumentException("Unbounded cuboid geometry");
        if ((long) u + 2L * d + 2L * w > atlasW || (long) v + d + h > atlasH)
            throw new IllegalArgumentException("ModelBox UV face falls outside source atlas");

        double x0 = (b.x() + b.pivotX()) * scale, x1 = (b.x() + b.pivotX() + w) * scale;
        double y0 = (b.y() + b.pivotY()) * scale, y1 = (b.y() + b.pivotY() + h) * scale;
        double z0 = (b.z() + b.pivotZ()) * scale, z1 = (b.z() + b.pivotZ() + d) * scale;
        // Identical eight corners to 1.7.10 ModelBox's PositionTextureVertex[0..7].
        Point[] p = {new Point(x0,y0,z0), new Point(x1,y0,z0),
                new Point(x1,y1,z0), new Point(x0,y1,z0),
                new Point(x0,y0,z1), new Point(x1,y0,z1),
                new Point(x1,y1,z1), new Point(x0,y1,z1)};
        for (int i = 0; i < p.length; ++i) p[i] = rotate.apply(p[i]);
        List<Quad> faces = new ArrayList<>(6);
        // Source ModelBox.java quadList[0..5] exact face indices and TexOffX/Y arguments.
        faces.add(quad(b.name(), Side.EAST, p, new int[]{5,1,2,6},
                u+d+w, v+d, u+d+w+d, v+d+h, atlasW, atlasH));
        faces.add(quad(b.name(), Side.WEST, p, new int[]{0,4,7,3},
                u, v+d, u+d, v+d+h, atlasW, atlasH));
        faces.add(quad(b.name(), Side.DOWN, p, new int[]{5,4,0,1},
                u+d, v, u+d+w, v+d, atlasW, atlasH));
        faces.add(quad(b.name(), Side.UP, p, new int[]{2,3,7,6},
                u+d+w, v+d, u+d+2*w, v, atlasW, atlasH));
        faces.add(quad(b.name(), Side.NORTH, p, new int[]{1,0,3,2},
                u+d, v+d, u+d+w, v+d+h, atlasW, atlasH));
        faces.add(quad(b.name(), Side.SOUTH, p, new int[]{4,5,6,7},
                u+d+w+d, v+d, u+d+w+d+w, v+d+h, atlasW, atlasH));
        return List.copyOf(faces);
    }

    private static Quad quad(String part, Side side, Point[] p, int[] indices,
                             int u0, int v0, int u1, int v1, int w, int h) {
        // TexturedQuad assigns [u1,v0], [u0,v0], [u0,v1], [u1,v1] to the supplied corners.
        double[] us = {(double) u1 / w, (double) u0 / w, (double) u0 / w, (double) u1 / w};
        double[] vs = {(double) v0 / h, (double) v0 / h, (double) v1 / h, (double) v1 / h};
        Point a = p[indices[0]], b = p[indices[1]], c = p[indices[2]];
        // 1.7.10 TexturedQuad.draw(): normal = (vertex1 - vertex2) cross (vertex1 - vertex0).
        double ex = b.x - c.x, ey = b.y - c.y, ez = b.z - c.z;
        double fx = b.x - a.x, fy = b.y - a.y, fz = b.z - a.z;
        double nx = ey*fz - ez*fy, ny = ez*fx - ex*fz, nz = ex*fy - ey*fx;
        double len = Math.sqrt(nx*nx+ny*ny+nz*nz);
        if (!(len > EPSILON) || !Double.isFinite(len))
            throw new IllegalArgumentException("Degenerate or corrupt ModelBox face");
        nx /= len;ny /= len;nz /= len;
        Vertex[] vertices = new Vertex[4];
        for (int i=0;i<4;i++) {
            Point point=p[indices[i]];
            vertices[i]=new Vertex(point.x, point.y, point.z, us[i], vs[i], nx, ny, nz);
        }
        return new Quad(part,side,vertices[0],vertices[1],vertices[2],vertices[3]);
    }

    private static boolean finite(double... values) {
        for (double v : values) if (!Double.isFinite(v)) return false;
        return true;
    }
}
