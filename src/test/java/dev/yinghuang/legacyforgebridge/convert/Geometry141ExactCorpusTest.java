package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** External original binary is not committed; run through the existing exactCorpusTest task. */
@Tag("exact-corpus")
class Geometry141ExactCorpusTest {
    @Test void originalBambooGeometryAndEmptyCurtainCollisionAreSourceDerived()throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"Explicit original corpus required");
        Path jar=Path.of(input);assertEquals("bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402",Hashing.sha256(jar));
        var analysis=new LegacyBlockGeometryAnalyzer().analyze(jar);Map<String,LegacyBlockGeometryAnalyzer.Rule> rules=new HashMap<>();
        analysis.rules().forEach(r->rules.put(r.input().registryName(),r));assertEquals(19,rules.size());
        for(String name:List.of("halfDirSquare","halfDeco","halfTwoDirDeco")){
            var v=rules.get(name).input().variants();assertEquals(16,v.size());assertEquals(.5,v.get(0).bounds().get(4));assertEquals(.5,v.get(8).bounds().get(1));assertEquals(0d,v.get(8).inventoryBounds().get(1));
        }
        for(String name:List.of("kayabukiRoof","kawara_stair","wara_stair"))assertEquals("stairs",rules.get(name).family());
        var pane=rules.get("bambooPanel");assertEquals("pane",pane.family());assertEquals(7,pane.input().variants().size());
        assertEquals("empty",pane.input().collisions().get(4));assertEquals("empty",pane.input().collisions().get(5));assertFalse(pane.input().paneEdges().get(4));
        for(String name:List.of("delude_width","delude_height","delude_stair")){
            var r=rules.get(name);assertTrue(r.family().startsWith("mimic_"));assertEquals(5,r.input().neighbourFaces().get(0));
        }
        assertFalse(rules.containsKey("singleTexDeco"));assertFalse(rules.containsKey("bambooLiangThick"));
    }
}
