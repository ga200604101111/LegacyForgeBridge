package dev.yinghuang.legacyforgebridge.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Pure-Java Forge OBJ geometry preparation. No Minecraft, renderer, or mod-specific identities. */
public final class LegacyObjGeometry {
    private static final double UV_INSET = 0.0005;
    private static final double EPS = 1.0e-10;
    private static final long MAX_TILES_PER_TRIANGLE = 4096;
    private static final int MAX_OUTPUT_TRIANGLES = 2_000_000;
    private final List<Point> positions;
    private final List<Triangle> triangles;

    private LegacyObjGeometry(List<Point> positions, List<Triangle> triangles) {
        this.positions = List.copyOf(positions);
        this.triangles = List.copyOf(triangles);
    }

    public record Point(double x, double y, double z) { }
    public record Vertex(double x, double y, double z, double u, double v) {
        Vertex between(Vertex b, double t) {
            return new Vertex(x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t,
                    u + (b.u - u) * t, v + (b.v - v) * t);
        }
        Vertex rebase(double tileU, double tileV) {
            return new Vertex(x, y, z, clamp(u - tileU), clamp(v - tileV));
        }
    }
    public record Triangle(Vertex a, Vertex b, Vertex c, Point normal) {
        /** Entity RenderTypes consume QUADS. A,B,C,C emits one triangle plus a degenerate one. */
        public void emitQuad(Consumer<Vertex> output) {
            output.accept(a);
            output.accept(b);
            output.accept(c);
            output.accept(c);
        }
    }
    private record Ref(int position, int texture) { }
    private record Face(List<Ref> refs) { }

    public List<Point> positions() { return positions; }
    public List<Triangle> triangles() { return triangles; }

    public static LegacyObjGeometry parse(List<String> lines) {
        List<Point> positions = new ArrayList<>();
        List<double[]> textures = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        int normalCount = 0;
        for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
            String raw = lines.get(lineNumber);
            int comment = raw.indexOf('#');
            String line = (comment < 0 ? raw : raw.substring(0, comment)).trim();
            if (line.isEmpty()) continue;
            String[] tokens = line.split("\\s+");
            try {
                switch (tokens[0]) {
                    case "v" -> positions.add(new Point(number(tokens, 1), number(tokens, 2), number(tokens, 3)));
                    case "vt" -> textures.add(new double[]{number(tokens, 1),
                            1.0 - (tokens.length > 2 ? number(tokens, 2) : 0.0)});
                    case "vn" -> {
                        number(tokens, 1); number(tokens, 2); number(tokens, 3);
                        normalCount++;
                    }
                    case "f" -> {
                        if (tokens.length < 4) throw new IllegalArgumentException("face has fewer than three vertices");
                        List<Ref> refs = new ArrayList<>();
                        for (int i = 1; i < tokens.length; i++) {
                            String[] ref = tokens[i].split("/", -1);
                            if (ref.length > 3) throw new IllegalArgumentException("invalid face reference " + tokens[i]);
                            int p = index(ref[0], positions.size());
                            int t = ref.length > 1 && !ref[1].isEmpty() ? index(ref[1], textures.size()) : -1;
                            if (ref.length > 2 && !ref[2].isEmpty()) index(ref[2], normalCount);
                            refs.add(new Ref(p, t));
                        }
                        faces.add(new Face(List.copyOf(refs)));
                    }
                    default -> { /* Forge WavefrontObject ignores mtllib/usemtl; callers bind textures. */ }
                }
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("OBJ line " + (lineNumber + 1) + ": " + ex.getMessage(), ex);
            }
        }
        List<Triangle> triangles = new ArrayList<>();
        for (Face face : faces) {
            double au = 0, av = 0;
            int textured = 0;
            for (Ref ref : face.refs) {
                if (ref.texture < 0) continue;
                double[] uv = textures.get(ref.texture);
                au += uv[0]; av += uv[1]; textured++;
            }
            if (textured > 0) { au /= textured; av /= textured; }
            List<Vertex> polygon = new ArrayList<>();
            for (Ref ref : face.refs) {
                Point p = positions.get(ref.position);
                double u = 0, v = 0;
                if (ref.texture >= 0) {
                    double[] uv = textures.get(ref.texture);
                    u = uv[0] + (uv[0] > au ? -UV_INSET : UV_INSET);
                    v = uv[1] + (uv[1] > av ? -UV_INSET : UV_INSET);
                }
                polygon.add(new Vertex(p.x, p.y, p.z, u, v));
            }
            Point normal = normal(polygon);
            if (normal == null) continue;
            for (Triangle triangle : triangulate(polygon, normal)) {
                tile(triangle, triangles);
                if (triangles.size() > MAX_OUTPUT_TRIANGLES) {
                    throw new IllegalArgumentException("OBJ exceeds prepared geometry limit");
                }
            }
        }
        return new LegacyObjGeometry(positions, triangles);
    }

    /** Preserve interpolation: split at integer UV boundaries, then rebase an entire piece. */
    private static void tile(Triangle triangle, List<Triangle> output) {
        List<Vertex> source = List.of(triangle.a, triangle.b, triangle.c);
        double minU = Math.min(triangle.a.u, Math.min(triangle.b.u, triangle.c.u));
        double maxU = Math.max(triangle.a.u, Math.max(triangle.b.u, triangle.c.u));
        double minV = Math.min(triangle.a.v, Math.min(triangle.b.v, triangle.c.v));
        double maxV = Math.max(triangle.a.v, Math.max(triangle.b.v, triangle.c.v));
        if (Math.max(Math.max(Math.abs(minU), Math.abs(maxU)),
                Math.max(Math.abs(minV), Math.abs(maxV))) > 1_000_000) {
            throw new IllegalArgumentException("OBJ texture coordinates exceed supported range");
        }
        int u0 = (int) Math.floor(minU), v0 = (int) Math.floor(minV);
        int u1 = Math.max(u0, (int) Math.ceil(maxU) - 1);
        int v1 = Math.max(v0, (int) Math.ceil(maxV) - 1);
        if ((long) (u1 - u0 + 1) * (v1 - v0 + 1) > MAX_TILES_PER_TRIANGLE) {
            throw new IllegalArgumentException("OBJ triangle exceeds UV repeat tile limit");
        }
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                List<Vertex> piece = source;
                if (u0 != u1) {
                    piece = clip(piece, 0, u, true);
                    piece = clip(piece, 0, u + 1.0, false);
                }
                if (v0 != v1) {
                    piece = clip(piece, 1, v, true);
                    piece = clip(piece, 1, v + 1.0, false);
                }
                if (piece.size() < 3) continue;
                Vertex a = piece.getFirst().rebase(u, v);
                for (int i = 1; i + 1 < piece.size(); i++) {
                    Vertex b = piece.get(i).rebase(u, v), c = piece.get(i + 1).rebase(u, v);
                    if (areaSquared(a, b, c) > 1.0e-24) output.add(new Triangle(a, b, c, triangle.normal));
                }
            }
        }
    }

    private static List<Vertex> clip(List<Vertex> polygon, int axis, double boundary, boolean greater) {
        List<Vertex> result = new ArrayList<>();
        if (polygon.isEmpty()) return result;
        Vertex a = polygon.getLast();
        double ac = axis == 0 ? a.u : a.v;
        boolean ai = greater ? ac >= boundary : ac <= boundary;
        for (Vertex b : polygon) {
            double bc = axis == 0 ? b.u : b.v;
            boolean bi = greater ? bc >= boundary : bc <= boundary;
            if (ai != bi) result.add(a.between(b, (boundary - ac) / (bc - ac)));
            if (bi) result.add(b);
            a = b; ac = bc; ai = bi;
        }
        return result;
    }

    /** Ear clipping preserves concave polygons instead of fanning outside their boundary. */
    private static List<Triangle> triangulate(List<Vertex> polygon, Point normal) {
        if (polygon.size() == 3) return List.of(new Triangle(polygon.get(0), polygon.get(1), polygon.get(2), normal));
        int axis = Math.abs(normal.x) > Math.abs(normal.y)
                ? (Math.abs(normal.x) > Math.abs(normal.z) ? 0 : 2)
                : (Math.abs(normal.y) > Math.abs(normal.z) ? 1 : 2);
        List<Vertex> work = new ArrayList<>(polygon);
        List<Triangle> result = new ArrayList<>();
        double area = 0;
        for (int i = 0; i < work.size(); i++) {
            Vertex a = work.get(i), b = work.get((i + 1) % work.size());
            area += px(a, axis) * py(b, axis) - px(b, axis) * py(a, axis);
        }
        double winding = Math.signum(area);
        if (winding == 0) throw new IllegalArgumentException("OBJ polygon has no stable projected winding");
        while (work.size() > 3) {
            boolean cut = false;
            for (int i = 0; i < work.size(); i++) {
                Vertex a = work.get((i + work.size() - 1) % work.size());
                Vertex b = work.get(i), c = work.get((i + 1) % work.size());
                if (cross2(a, b, c, axis) * winding <= EPS) continue;
                boolean occupied = false;
                for (Vertex p : work) {
                    if (p == a || p == b || p == c) continue;
                    if (cross2(a, b, p, axis) * winding >= -EPS
                            && cross2(b, c, p, axis) * winding >= -EPS
                            && cross2(c, a, p, axis) * winding >= -EPS) { occupied = true; break; }
                }
                if (!occupied) {
                    result.add(new Triangle(a, b, c, normal));
                    work.remove(i); cut = true; break;
                }
            }
            if (!cut) throw new IllegalArgumentException("Unsupported self-intersecting/degenerate OBJ polygon");
        }
        result.add(new Triangle(work.get(0), work.get(1), work.get(2), normal));
        return result;
    }

    private static double px(Vertex v, int drop) { return drop == 0 ? v.y : v.x; }
    private static double py(Vertex v, int drop) { return drop == 2 ? v.y : v.z; }
    private static double cross2(Vertex a, Vertex b, Vertex c, int axis) {
        return (px(b, axis) - px(a, axis)) * (py(c, axis) - py(a, axis))
                - (py(b, axis) - py(a, axis)) * (px(c, axis) - px(a, axis));
    }
    private static Point normal(List<Vertex> vertices) {
        Vertex a = vertices.getFirst();
        for (int i = 1; i + 1 < vertices.size(); i++) {
            Vertex b = vertices.get(i), c = vertices.get(i + 1);
            double ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
            double vx = c.x - a.x, vy = c.y - a.y, vz = c.z - a.z;
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0e-12) return new Point(nx / length, ny / length, nz / length);
        }
        return null;
    }
    public static double areaSquared(Vertex a, Vertex b, Vertex c) {
        double ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
        double vx = c.x - a.x, vy = c.y - a.y, vz = c.z - a.z;
        double x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
        return (x * x + y * y + z * z) / 4.0;
    }
    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static double number(String[] tokens, int at) {
        if (at >= tokens.length) throw new IllegalArgumentException("missing coordinate");
        double result = Double.parseDouble(tokens[at]);
        if (!Double.isFinite(result) || Math.abs(result) > 1.0e12) throw new IllegalArgumentException("non-finite/out-of-range coordinate");
        return result;
    }
    private static int index(String token, int count) {
        int value = Integer.parseInt(token);
        if (value == 0) throw new IllegalArgumentException("OBJ indices are one-based; zero is invalid");
        int resolved = value > 0 ? value - 1 : count + value;
        if (resolved < 0 || resolved >= count) throw new IllegalArgumentException("OBJ index out of range: " + value);
        return resolved;
    }
}
