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
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPlainEntityRetirementPassTest {
    @TempDir Path tempDir;

    @Test void deletesReadyEntityRendererCohortOnlyAfterFreshPreAndPostReferenceChecks() throws Exception {
        Path staging=tempDir.resolve("ready");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Path entity=writeClass(staging,"foreign/Orb",plain("foreign/Orb","net/minecraft/entity/Entity"));
        Path renderer=writeClass(staging,"foreign/client/RenderEmpty",renderer("foreign/client/RenderEmpty","foreign/Orb"));
        writeReadiness(staging,true);

        new LegacyPlainEntityRetirementPass().apply(context(staging,"ready.jar"));

        assertFalse(Files.exists(entity));assertFalse(Files.exists(renderer));
        JsonObject root=read(staging);assertTrue(root.get("retirementAuthorizationWired").getAsBoolean());assertTrue(root.get("sourceClassDeletionWired").getAsBoolean());assertTrue(root.get("preDeleteReferenceRecheckWired").getAsBoolean());assertTrue(root.get("postDeleteReferenceRecheckWired").getAsBoolean());assertEquals(1,root.get("retirementAuthorizedCohorts").getAsInt());assertEquals(2,root.get("deletedSourceClasses").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("sourceClassDeletionAuthorized").getAsBoolean());assertTrue(rule.get("rendererClassDeletionAuthorized").getAsBoolean());assertTrue(rule.get("retirementComplete").getAsBoolean());assertFalse(rule.get("restoredAfterFailedRetirement").getAsBoolean());assertTrue(rule.getAsJsonArray("blockers").isEmpty());
        assertTrue(rule.getAsJsonArray("freshPreDeleteEntityIncomingReferences").asList().stream().anyMatch(value->value.getAsString().equals("foreign/client/RenderEmpty")));
    }

    @Test void staleReadySidecarCannotDeleteCohortWhenFreshBootstrapReferenceExists() throws Exception {
        Path staging=tempDir.resolve("stale");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Path entity=writeClass(staging,"foreign/Orb",plain("foreign/Orb","net/minecraft/entity/Entity"));
        Path renderer=writeClass(staging,"foreign/client/RenderEmpty",renderer("foreign/client/RenderEmpty","foreign/Orb"));
        writeClass(staging,"foreign/Bootstrap",bootstrap());writeReadiness(staging,true);

        new LegacyPlainEntityRetirementPass().apply(context(staging,"stale.jar"));

        assertTrue(Files.isRegularFile(entity));assertTrue(Files.isRegularFile(renderer));
        JsonObject rule=read(staging).getAsJsonArray("rules").get(0).getAsJsonObject();assertFalse(rule.get("retirementComplete").getAsBoolean());assertFalse(rule.get("sourceClassDeletionAuthorized").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream().anyMatch(value->value.getAsString().equals("fresh-predelete-entity-incoming-reference:foreign/Bootstrap")));
    }

    @Test void readinessBlockedCohortIsNeverReinterpretedAsDeletable() throws Exception {
        Path staging=tempDir.resolve("blocked");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Path entity=writeClass(staging,"foreign/Orb",plain("foreign/Orb","net/minecraft/entity/Entity"));
        Path renderer=writeClass(staging,"foreign/client/RenderEmpty",renderer("foreign/client/RenderEmpty","foreign/Orb"));
        writeReadiness(staging,false);

        new LegacyPlainEntityRetirementPass().apply(context(staging,"blocked.jar"));

        assertTrue(Files.isRegularFile(entity));assertTrue(Files.isRegularFile(renderer));
        JsonObject rule=read(staging).getAsJsonArray("rules").get(0).getAsJsonObject();assertFalse(rule.get("retirementComplete").getAsBoolean());assertTrue(rule.getAsJsonArray("blockers").asList().stream().anyMatch(value->value.getAsString().equals("retirement-cohort-readiness-not-complete")));
        assertTrue(rule.getAsJsonArray("blockers").asList().stream().anyMatch(value->value.getAsString().equals("readiness-entity:source-instantiation-rewrite-required")));
    }

    private ConversionContext context(Path staging,String jarName)throws Exception{
        Path source=tempDir.resolve(jarName);try(JarOutputStream ignored=new JarOutputStream(Files.newOutputStream(source))){}
        LegacyModMetadata metadata=new LegacyModMetadata(jarName,"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(jarName,0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,tempDir.resolve(jarName+".candidate.jar"),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
    }
    private static void writeReadiness(Path staging,boolean ready)throws Exception{Files.writeString(staging.resolve(LegacyPlainEntityRetirementReadiness.OUTPUT),"""
            {
              "schemaVersion":1,"sourceSha256":"sha","retirementReadinessAnalysisWired":true,
              "candidateClassReferenceClosureComplete":true,"candidateResourceReferenceClosureComplete":true,
              "rules":[{
                "id":"foreign:orb","sourceClass":"foreign/Orb","rendererClass":"foreign/client/RenderEmpty",
                "retirementCohortCandidateReady":%s,
                "entityBlockers":%s,"rendererBlockers":[]
              }]
            }
            """.formatted(Boolean.toString(ready),ready?"[]":"[\"source-instantiation-rewrite-required\"]"),StandardCharsets.UTF_8);}
    private static JsonObject read(Path staging)throws Exception{return JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityRetirementPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();}
    private static Path writeClass(Path root,String name,byte[] bytes)throws Exception{Path path=root.resolve(name+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);return path;}
    private static byte[] plain(String name,String superName){ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,superName,null);w.visitEnd();return w.toByteArray();}
    private static byte[] renderer(String name,String entity){ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"render","(L"+entity+";)V",null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,2);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] bootstrap(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Bootstrap",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"touch","()V",null,null);m.visitCode();m.visitLdcInsn(Type.getObjectType("foreign/Orb"));m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
}
