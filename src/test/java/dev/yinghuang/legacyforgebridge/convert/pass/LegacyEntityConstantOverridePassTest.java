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

class LegacyEntityConstantOverridePassTest {
    @TempDir Path tempDir;

    @Test void materializesOnlyExactCanBePushedConstantMapping() throws Exception {
        Path source=tempDir.resolve("source.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){
            put(out,"foreign/Orb.class",entity("foreign/Orb",false,false));
            put(out,"foreign/Dynamic.class",entity("foreign/Dynamic",false,true));
        }
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyEntityBehaviorSurfacePass.OUTPUT),"""
                {
                  "schemaVersion":1,"sourceSha256":"sha","rules":[
                    {"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","callbacks":[{"kind":"CAN_PUSH","owner":"foreign/Orb","method":"canBePushed","descriptor":"()Z"}]},
                    {"id":"foreign:dynamic","legacyRegistryName":"dynamic","sourceClass":"foreign/Dynamic","callbacks":[{"kind":"CAN_PUSH","owner":"foreign/Dynamic","method":"canBePushed","descriptor":"()Z"}]}
                  ]
                }
                """,StandardCharsets.UTF_8);

        new LegacyEntityConstantOverridePass().apply(context(source,staging));

        JsonObject root=JsonParser.parseString(Files.readString(staging.resolve(LegacyEntityConstantOverridePass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("constantOverrideProofWired").getAsBoolean());assertFalse(root.get("runtimeCodegenWired").getAsBoolean());
        assertEquals(1,root.get("provenConstantOverrideCount").getAsInt());assertEquals(1,root.get("blockedConstantOverrideCount").getAsInt());
        JsonObject proven=root.getAsJsonArray("rules").get(0).getAsJsonObject();
        JsonObject mapping=proven.getAsJsonArray("constantOverrides").get(0).getAsJsonObject();
        assertEquals("CAN_PUSH",mapping.get("sourceKind").getAsString());assertFalse(mapping.get("constantBoolean").getAsBoolean());
        assertEquals("isPushable",mapping.get("targetMethod").getAsString());assertEquals("()Z",mapping.get("targetDescriptor").getAsString());assertTrue(mapping.get("runtimeCodegenReady").getAsBoolean());
        JsonObject blocked=root.getAsJsonArray("rules").get(1).getAsJsonObject();assertTrue(blocked.getAsJsonArray("constantOverrides").isEmpty());assertEquals("boolean-callback-not-exact-constant-return",blocked.getAsJsonArray("blockedOverrides").get(0).getAsJsonObject().get("proofReason").getAsString());
    }

    private ConversionContext context(Path source,Path staging)throws Exception{
        LegacyModMetadata metadata=new LegacyModMetadata("source.jar","test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis analysis=new LegacyJarAnalyzer.Analysis("source.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,tempDir.resolve("candidate.jar"),"sha",Files.size(source),metadata,analysis,new DiagnosticCollector(),"generic-test");
    }
    private static byte[] entity(String name,boolean value,boolean dynamic){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);if(dynamic)w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"PUSH","Z",null,null).visitEnd();MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"canBePushed","()Z",null,null);m.visitCode();if(dynamic)m.visitFieldInsn(Opcodes.GETSTATIC,name,"PUSH","Z");else m.visitInsn(value?Opcodes.ICONST_1:Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
