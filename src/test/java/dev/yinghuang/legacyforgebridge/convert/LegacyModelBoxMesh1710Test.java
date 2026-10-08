package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the exact 1.7.10 ModelBox[0..5] geometry/UV order, not a mod-name selector. */
class LegacyModelBoxMesh1710Test {
    private static LegacyFixedModelProjectileAnalyzer.Cuboid first() {
        return new LegacyFixedModelProjectileAnalyzer.Cuboid(
                "segment1", 0, 4, -1F, -1F, -1F, 4, 2, 2, -1F, 7F, 3F);
    }
    private static LegacyFixedModelProjectileAnalyzer.Proof spec(float angle,
              float x, float y, float z, List<LegacyFixedModelProjectileAnalyzer.Cuboid> parts) {
        return new LegacyFixedModelProjectileAnalyzer.Proof("renamed/client/Renderer", "renamed/client/Model",
                "renamed:textures/model/source.png", 32, 32, .075F, angle, x, y, z, parts);
    }
    private static LegacyFixedModelProjectileAnalyzer.Proof moonwormShape(float angle) {
        return spec(angle, 1, 0, 1, List.of(first(),
                new LegacyFixedModelProjectileAnalyzer.Cuboid("segment2",0,8,-1,-1,-1,2,2,4,3,7,0),
                new LegacyFixedModelProjectileAnalyzer.Cuboid("segment3",0,14,-1,-1,-1,2,2,2,2,7,-2),
                new LegacyFixedModelProjectileAnalyzer.Cuboid("head",0,0,-1,-1,-1,2,2,2,-3,7,2)));
    }
    private static void close(double expected, double actual) {
        assertEquals(expected,actual,1e-6);
    }
    @Test void exactFourPartModelHas24Quads48TrianglesAnd96Vertices() {
        var mesh=LegacyModelBoxMesh1710.build(moonwormShape(90));
        assertEquals("renamed:textures/model/source.png",mesh.texture());
        assertEquals(24,mesh.quads().size());
        assertEquals(48,mesh.triangleCount());
        assertEquals(96,mesh.vertexCount());
        assertTrue(mesh.sourceAxisAngleBaked());
        assertEquals(32,mesh.atlasWidth());
        assertEquals(32,mesh.atlasHeight());
    }
    @Test void firstFaceMatchesSourceModelBoxTextureQuadUvsAndOrder() {
        var mesh=LegacyModelBoxMesh1710.build(spec(0,1,0,0,List.of(first())));
        var east=mesh.quads().getFirst();
        assertEquals(LegacyModelBoxMesh1710.Side.EAST,east.side());
        close(8.0/32,east.a().u());close(6.0/32,east.a().v());
        close(6.0/32,east.b().u());close(6.0/32,east.b().v());
        close(6.0/32,east.c().u());close(8.0/32,east.c().v());
        close(8.0/32,east.d().u());close(8.0/32,east.d().v());
        close(.15,east.a().x());close(.45,east.a().y());close(.30,east.a().z());
        close(1,east.a().nx());close(0,east.a().ny());close(0,east.a().nz());
    }
    @Test void topAndBottomUvsPreserveLegacyUpsideDownFace() {
        var mesh=LegacyModelBoxMesh1710.build(spec(0,1,0,0,List.of(first())));
        var up=mesh.quads().get(2);
        var down=mesh.quads().get(3);
        assertEquals(LegacyModelBoxMesh1710.Side.DOWN,up.side());
        assertEquals(LegacyModelBoxMesh1710.Side.UP,down.side());
        close(6.0/32,up.a().u());close(4.0/32,up.a().v());
        close(10.0/32,down.a().u());close(6.0/32,down.a().v());
        close(4.0/32,down.c().v());
        close(0,up.a().nx());close(-1,up.a().ny());close(0,up.a().nz());
    }
    @Test void glRotateAroundOneZeroOneUsesNormalizedAxisExactlyOnce() {
        var mesh=LegacyModelBoxMesh1710.build(spec(90,1,0,1,List.of(first())));
        var a=mesh.quads().getFirst().a();
        close(-.0931980515339464,a.x());
        close(-.1060660171779821,a.y());
        close(.5431980515339464,a.z());
        close(.5,a.nx());
        close(1/Math.sqrt(2),a.ny());
        close(.5,a.nz());
    }
    @Test void originAndPivotAreScaledTogetherBeforeRotation() {
        var b=new LegacyFixedModelProjectileAnalyzer.Cuboid("edge",0,0,0,0,0,2,2,2,4,8,16);
        var face=LegacyModelBoxMesh1710.build(spec(0,0,0,1,List.of(b))).quads().getFirst();
        close((4+2)*.075,face.a().x());
        close(8*.075,face.a().y());
        close((16+2)*.075,face.a().z());
    }
    @Test void facesLeavingAtlasFailClosed() {
        var b=new LegacyFixedModelProjectileAnalyzer.Cuboid("oops",31,4,-1,-1,-1,4,2,2,-1,7,3);
        assertThrows(IllegalArgumentException.class,
                ()->LegacyModelBoxMesh1710.build(spec(0,1,0,0,List.of(b))));
    }
    @Test void nonfiniteScaleAndAxisAreRejected() {
        assertThrows(IllegalArgumentException.class,
                ()->LegacyModelBoxMesh1710.build(spec(90,0,0,0,List.of(first()))));
        var bad = new LegacyFixedModelProjectileAnalyzer.Proof("r","m","renamed:textures/model/source.png",
                32,32,Float.NaN,90,1,0,1,List.of(first()));
        assertThrows(IllegalArgumentException.class,()->LegacyModelBoxMesh1710.build(bad));
    }
    @Test void duplicatePartIdentityIsNotAValidUnambiguousMesh() {
        assertThrows(IllegalArgumentException.class,
                ()->LegacyModelBoxMesh1710.build(spec(0,1,0,0,List.of(first(),first()))));
    }
    @Test void pathTraversalAndUnboundedPartsAreRejected() {
        var invalid = new LegacyFixedModelProjectileAnalyzer.Proof("r","m","renamed:textures/../oops.png",
                32,32,.075F,0,1,0,0,List.of(first()));
        assertThrows(IllegalArgumentException.class,()->LegacyModelBoxMesh1710.build(invalid));
        var pieces=new ArrayList<LegacyFixedModelProjectileAnalyzer.Cuboid>();
        for(int i=0;i<65;i++)pieces.add(new LegacyFixedModelProjectileAnalyzer.Cuboid(
                "part"+i,0,4,-1,-1,-1,4,2,2,-1,7,3));
        assertThrows(IllegalArgumentException.class,
                ()->LegacyModelBoxMesh1710.build(spec(0,1,0,0,pieces)));
    }
    @Test void inputProofAndPartDataAreNeverMutated() {
        var proof=moonwormShape(90);
        var before=List.copyOf(proof.cuboids());
        LegacyModelBoxMesh1710.build(proof);
        assertEquals(before,proof.cuboids());
    }
}
