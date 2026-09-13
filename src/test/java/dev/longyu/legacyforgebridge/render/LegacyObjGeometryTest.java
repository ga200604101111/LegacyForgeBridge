package dev.longyu.legacyforgebridge.render;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyObjGeometryTest {
    private static LegacyObjGeometry parse(String text) { return LegacyObjGeometry.parse(text.lines().toList()); }

    @Test void consecutiveTrianglesProduceIndependentFourVertexPrimitives() {
        var geometry = parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nv 10 0 0\nv 11 0 0\nv 10 1 0\nf 1 2 3\nf 4 5 6");
        var vertices = new ArrayList<LegacyObjGeometry.Vertex>();
        geometry.triangles().forEach(t -> t.emitQuad(vertices::add));
        assertEquals(8, vertices.size());
        assertSame(vertices.get(2), vertices.get(3));
        assertSame(vertices.get(6), vertices.get(7));
        assertEquals(10, vertices.get(4).x());
    }

    @Test void crossingRepeatBoundaryPreservesSurfaceAreaAndDoesNotInterpolateAcrossTheWholeTexture() {
        var geometry = parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0.99 0.8\nvt 1.01 0.8\nvt 0.99 0.6\nf 1/1 2/2 3/3");
        assertTrue(geometry.triangles().size() > 1);
        double area = 0;
        boolean lowerEdge = false, upperEdge = false;
        for (var t : geometry.triangles()) {
            area += Math.sqrt(LegacyObjGeometry.areaSquared(t.a(), t.b(), t.c()));
            double min = 1, max = 0;
            for (var v : List.of(t.a(), t.b(), t.c())) {
                min = Math.min(min, v.u()); max = Math.max(max, v.u());
                lowerEdge |= v.u() == 0; upperEdge |= v.u() == 1;
                assertTrue(v.u() >= 0 && v.u() <= 1);
                assertTrue(v.v() >= 0 && v.v() <= 1);
            }
            assertTrue(max - min < .02, "No piece may stretch .99 -> .01 across a repeat seam");
        }
        assertEquals(.5, area, 1e-9);
        assertTrue(lowerEdge && upperEdge, "Both sides of the seam must be represented");
    }

    @Test void negativeSourceVAndNegativeIndicesAreSupported() {
        var geometry = parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt .1 -.2\nvt .4 -.2\nvt .1 -.6\nf -3/-3 -2/-2 -1/-1 # comment");
        var t = geometry.triangles().getFirst();
        assertEquals(.2005, t.a().v(), 1e-7);
        assertEquals(.5995, t.c().v(), 1e-7);
        assertEquals(1, t.normal().z(), 1e-9);
    }

    @Test void concavePolygonDoesNotDrawOutsideItsBoundary() {
        var geometry = parse("v 0 0 0\nv 2 0 0\nv 2 2 0\nv 1 1 0\nv 0 2 0\nf 1 2 3 4 5");
        assertEquals(3, geometry.triangles().size());
        double area = geometry.triangles().stream().mapToDouble(t ->
                Math.sqrt(LegacyObjGeometry.areaSquared(t.a(), t.b(), t.c()))).sum();
        assertEquals(3, area, 1e-9);
    }

    @Test void badIndicesAndNonFiniteCoordinatesAreRejectedAsWholeModels() {
        for (String face : List.of("f 0 2 3", "f 1 2 4", "f -4 -2 -1", "f 1/1 2/1 3/1")) {
            assertThrows(IllegalArgumentException.class, () -> parse("v 0 0 0\nv 1 0 0\nv 0 1 0\n" + face));
        }
        assertThrows(IllegalArgumentException.class, () -> parse("v NaN 0 0"));
        assertThrows(IllegalArgumentException.class, () -> parse("v Infinity 0 0"));
    }

    @Test void veryLargeTilingCannotCreateUnboundedGeometry() {
        assertThrows(IllegalArgumentException.class, () -> parse(
                "v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 10000 0\nvt 0 10000\nf 1/1 2/2 3/3"));
    }
}
