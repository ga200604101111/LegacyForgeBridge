package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooSpaUnitPresentationExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactSpaUnitRetainsStandardContainerPresentationAndMetadataPistonIcons()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var registry=new LegacyRegistryAnalyzer().analyze(source);
        var registration=registry.blocks().stream()
                .filter(value->"ruby/bamboo/block/BlockSpaUnit".equals(value.implementationClass()))
                .findFirst().orElseThrow();
        var result=new LegacyIconTableAnalyzer().analyze(source,registry.registrations()).stream()
                .filter(value->value.registryName().equals(registration.registryName()))
                .findFirst().orElseThrow();

        assertEquals(16,result.variants().size(),result.limitation());
        var off=result.variants().get(0);
        var on=result.variants().get(8);
        assertEquals(0,off.renderType());assertEquals(0,on.renderType());
        assertEquals(6,off.faceIcons().size());assertEquals(6,on.faceIcons().size());
        assertEquals("minecraft:block/piston_top",off.faceIcons().get(1));
        assertEquals("minecraft:block/piston_inner",on.faceIcons().get(1));
        for(int side:new int[]{0,2,3,4,5}){
            assertEquals("minecraft:block/piston_top",off.faceIcons().get(side));
            assertEquals("minecraft:block/piston_top",on.faceIcons().get(side));
        }
    }
}
