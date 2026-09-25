package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGeneratedBytecodeLinkageAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void generatedWrappersCannotLinkForgeFmlOrLaunchWrapperSymbols() throws Exception {
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging);
        write(staging,"dev/yinghuang/legacyforgebridge/generated/Safe",
                wrapper("dev/yinghuang/legacyforgebridge/generated/Safe",false));
        write(staging,"dev/yinghuang/legacyforgebridge/generated/Unsafe",
                wrapper("dev/yinghuang/legacyforgebridge/generated/Unsafe",true));
        write(staging,"legacy/OutsideGeneratedNamespace",
                outsideGenerated("legacy/OutsideGeneratedNamespace"));

        var analysis=new LegacyGeneratedBytecodeLinkageAnalyzer().analyze(staging);
        assertTrue(analysis.complete(),analysis.diagnostics().toString());
        assertEquals(1,analysis.findings().size());

        var finding=analysis.findings().getFirst();
        assertEquals("dev/yinghuang/legacyforgebridge/generated/Unsafe",finding.generatedClass());
        assertTrue(finding.legacyReferences().contains("cpw/mods/fml/common/LegacyMarker"),
                "Generic Signature-only FML linkage must be visible");
        assertTrue(finding.legacyReferences().contains("net/minecraftforge/common/MinecraftForge"));
        assertTrue(finding.legacyReferences().contains("net/minecraft/launchwrapper/IClassTransformer"));
        assertFalse(finding.legacyReferences().stream().anyMatch(value->value.startsWith("legacy/")),
                "Non-generated source classes are handled by source stripping, not this generated-wrapper audit");
    }

    private static byte[] wrapper(String name,boolean unsafe){
        ClassWriter writer=new ClassWriter(0);
        writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);
        if(unsafe){
            writer.visitField(Opcodes.ACC_PRIVATE,"forge","Lnet/minecraftforge/common/MinecraftForge;",null,null).visitEnd();
            writer.visitField(Opcodes.ACC_PRIVATE,"markers","Ljava/util/List;",
                    "Ljava/util/List<Lcpw/mods/fml/common/LegacyMarker;>;",null).visitEnd();
            writer.visitMethod(Opcodes.ACC_PUBLIC,"transformer",
                    "()Lnet/minecraft/launchwrapper/IClassTransformer;",null,null).visitEnd();
        }
        writer.visitEnd();return writer.toByteArray();
    }

    private static byte[] outsideGenerated(String name){
        ClassWriter writer=new ClassWriter(0);
        writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,"net/minecraftforge/common/MinecraftForge",null);
        writer.visitEnd();return writer.toByteArray();
    }

    private static void write(Path root,String name,byte[] bytes)throws Exception{
        Path path=root.resolve(name+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);
    }
}
