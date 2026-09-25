package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPlainEntityRegistrationStripPassTest {
    @TempDir Path tempDir;

    @Test void stripsProofMatchedRegistrationFromStagedCandidateAndClearsEntityIncomingReference() throws Exception {
        Path source=tempDir.resolve("source.jar");byte[] orb=orb(),bootstrap=bootstrap();
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){put(out,"foreign/Orb.class",orb);put(out,"foreign/Bootstrap.class",bootstrap);}
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        writeClass(staging,"foreign/Orb",orb);writeClass(staging,"foreign/Bootstrap",bootstrap);
        Files.writeString(staging.resolve(LegacyPlainEntityRuntimePass.OUTPUT),"""
                {
                  "schemaVersion":1,"sourceSha256":"sha","runtimeImplementationWired":true,
                  "rules":[{
                    "id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb",
                    "legacyModEntityTypeId":17,"legacyTrackingRangeBlocks":80,"updateFrequency":2,
                    "velocityUpdates":true,"runtimeComplete":true
                  }]
                }
                """,StandardCharsets.UTF_8);
        ConversionContext context=context(source,staging);

        new LegacyPlainEntityRegistrationStripPass().apply(context);

        JsonObject root=JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityRegistrationStripPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("sourceRegistrationStripWired").getAsBoolean());assertEquals(1,root.get("registrationStripCompleteRules").getAsInt());assertEquals(1,root.get("strippedRegistrationSites").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("registrationStripComplete").getAsBoolean());assertTrue(rule.getAsJsonArray("blockers").isEmpty());
        var refs=new LegacyCandidateReferenceAnalyzer().analyze(staging,Set.of("foreign/Orb"));
        assertFalse(refs.forTarget("foreign/Orb").incomingClassReferences().contains("foreign/Bootstrap"));
    }

    private ConversionContext context(Path source,Path staging)throws Exception{
        LegacyModMetadata metadata=new LegacyModMetadata("source.jar","test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis("source.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,tempDir.resolve("candidate.jar"),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
    }
    private static byte[] orb(){ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Orb",null,"net/minecraft/entity/Entity",null);w.visitEnd();return w.toByteArray();}
    private static byte[] bootstrap(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Bootstrap",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);m.visitCode();m.visitLdcInsn(Type.getObjectType("foreign/Orb"));m.visitLdcInsn("orb");m.visitIntInsn(Opcodes.BIPUSH,17);m.visitVarInsn(Opcodes.ALOAD,0);m.visitIntInsn(Opcodes.BIPUSH,80);m.visitInsn(Opcodes.ICONST_2);m.visitInsn(Opcodes.ICONST_1);m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/EntityRegistry","registerModEntity","(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void writeClass(Path root,String name,byte[] bytes)throws Exception{Path path=root.resolve(name+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
