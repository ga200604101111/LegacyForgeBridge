package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityRenderDistanceConstantOverridePassTest {
    @TempDir Path tempDir;

    @Test void materializesExactLegacyRenderDistanceConstantMapping() throws Exception {
        Path source=tempDir.resolve("source.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){put(out,"foreign/Orb.class",entity());}
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyEntityBehaviorSurfacePass.OUTPUT),"""
                {
                  "schemaVersion":1,"sourceSha256":"sha","rules":[{
                    "id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb",
                    "callbacks":[{"kind":"RENDER_DISTANCE","owner":"foreign/Orb","method":"isInRangeToRenderDist","descriptor":"(D)Z"}]
                  }]
                }
                """,StandardCharsets.UTF_8);

        new LegacyEntityConstantOverridePass().apply(context(source,staging));

        JsonObject root=JsonParser.parseString(Files.readString(staging.resolve(LegacyEntityConstantOverridePass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1,root.get("provenConstantOverrideCount").getAsInt());assertEquals(0,root.get("blockedConstantOverrideCount").getAsInt());
        assertTrue(root.getAsJsonArray("supportedOverrideKinds").asList().stream().anyMatch(value->value.getAsString().equals("RENDER_DISTANCE")));
        JsonObject mapping=root.getAsJsonArray("rules").get(0).getAsJsonObject().getAsJsonArray("constantOverrides").get(0).getAsJsonObject();
        assertEquals("RENDER_DISTANCE",mapping.get("sourceKind").getAsString());assertFalse(mapping.get("constantBoolean").getAsBoolean());
        assertEquals("shouldRenderAtSqrDistance",mapping.get("targetMethod").getAsString());assertEquals("(D)Z",mapping.get("targetDescriptor").getAsString());
        assertEquals("RENDER_DISTANCE_CONSTANT_BOOLEAN_IDENTITY",mapping.get("mappingSemantics").getAsString());assertTrue(mapping.get("runtimeCodegenReady").getAsBoolean());
    }

    private ConversionContext context(Path source,Path staging)throws Exception{
        LegacyModMetadata metadata=new LegacyModMetadata("source.jar","test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis analysis=new LegacyJarAnalyzer.Analysis("source.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,tempDir.resolve("candidate.jar"),"sha",Files.size(source),metadata,analysis,new DiagnosticCollector(),"generic-test");
    }
    private static byte[] entity(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Orb",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"isInRangeToRenderDist","(D)Z",null,null);m.visitCode();m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
