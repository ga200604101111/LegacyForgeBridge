package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyClassDependencyAnalyzer;
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

class LegacyPlainEntityRetirementReadinessTest {
    @TempDir Path tempDir;

    @Test void marksIsolatedEntityAndItsSingleNoOpRendererAsRetirementCandidatesWithoutAuthorizingDeletion() throws Exception {
        Path staging=tempDir.resolve("ready");ConversionContext context=context(staging,"ready.jar");
        writeSidecars(staging,0,0,false,false);
        writeClass(staging,"foreign/Orb",plain("foreign/Orb","net/minecraft/entity/Entity"));
        writeClass(staging,"foreign/client/RenderEmpty",renderer("foreign/client/RenderEmpty","foreign/Orb"));

        LegacyPlainEntityRetirementReadiness.materialize(context,dependencies());

        JsonObject root=read(staging);
        assertTrue(root.get("retirementReadinessAnalysisWired").getAsBoolean());
        assertTrue(root.get("candidateClassReferenceClosureComplete").getAsBoolean());
        assertTrue(root.get("candidateResourceReferenceClosureComplete").getAsBoolean());
        assertFalse(root.get("retirementAuthorizationWired").getAsBoolean());
        assertFalse(root.get("sourceClassDeletionWired").getAsBoolean());
        assertEquals(1,root.get("retirementCohortCandidateReadyCount").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("entityRetirementCandidateReady").getAsBoolean());
        assertTrue(rule.get("rendererRetirementCandidateReady").getAsBoolean());
        assertTrue(rule.get("retirementCohortCandidateReady").getAsBoolean());
        assertFalse(rule.get("sourceClassDeletionAuthorized").getAsBoolean());
        assertTrue(rule.getAsJsonArray("candidateEntityIncomingReferences").asList().stream()
                .anyMatch(value->value.getAsString().equals("foreign/client/RenderEmpty")),
                "the proven no-op renderer may reference its entity within the same retirement cohort");
        assertTrue(rule.getAsJsonArray("entityBlockers").isEmpty());
        assertTrue(rule.getAsJsonArray("rendererBlockers").isEmpty());
    }

    @Test void blocksBootstrapSpawnAndResourceReferencesAgainstCurrentCandidateBytes() throws Exception {
        Path staging=tempDir.resolve("blocked");ConversionContext context=context(staging,"blocked.jar");
        writeSidecars(staging,1,1,true,false);
        writeClass(staging,"foreign/Orb",plain("foreign/Orb","net/minecraft/entity/Entity"));
        writeClass(staging,"foreign/client/RenderEmpty",renderer("foreign/client/RenderEmpty","foreign/Orb"));
        writeClass(staging,"foreign/Bootstrap",bootstrap());
        Files.createDirectories(staging.resolve("assets/foreign"));
        Files.writeString(staging.resolve("assets/foreign/renderer.txt"),"foreign.client.RenderEmpty",StandardCharsets.UTF_8);

        LegacyPlainEntityRetirementReadiness.materialize(context,dependencies());

        JsonObject rule=read(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("entityRetirementCandidateReady").getAsBoolean());
        assertFalse(rule.get("rendererRetirementCandidateReady").getAsBoolean());
        assertFalse(rule.get("retirementCohortCandidateReady").getAsBoolean());
        assertTrue(rule.getAsJsonArray("entityBlockers").asList().stream()
                .anyMatch(value->value.getAsString().equals("source-instantiation-rewrite-required")));
        assertTrue(rule.getAsJsonArray("entityBlockers").asList().stream()
                .anyMatch(value->value.getAsString().equals("unresolved-world-spawn-arguments:1")));
        assertTrue(rule.getAsJsonArray("entityBlockers").asList().stream()
                .anyMatch(value->value.getAsString().equals("candidate-incoming-reference:foreign/Bootstrap")));
        assertTrue(rule.getAsJsonArray("rendererBlockers").asList().stream()
                .anyMatch(value->value.getAsString().equals("candidate-incoming-reference:foreign/Bootstrap")));
        assertTrue(rule.getAsJsonArray("rendererBlockers").asList().stream()
                .anyMatch(value->value.getAsString().equals("candidate-resource-reference:assets/foreign/renderer.txt")));
    }

    private ConversionContext context(Path staging,String jarName)throws Exception{
        Files.createDirectories(staging.resolve("legacyforgebridge"));Path source=tempDir.resolve(jarName);
        try(JarOutputStream ignored=new JarOutputStream(Files.newOutputStream(source))){}
        LegacyModMetadata metadata=new LegacyModMetadata(jarName,"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(jarName,0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,tempDir.resolve(jarName+".candidate.jar"),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
    }

    private static LegacyClassDependencyAnalyzer.Analysis dependencies(){
        var entity=new LegacyClassDependencyAnalyzer.ClassDependency(
                "foreign/Orb","a",List.of("entity"),"unspecified",
                LegacyClassDependencyAnalyzer.Reachability.UNRESOLVED_NOT_PROVEN_UNREACHABLE,
                LegacyClassDependencyAnalyzer.CandidateState.ORIGINAL_BYTES_RETAINED,
                List.of(),List.of(),List.of("foreign/client/RenderEmpty"),List.of(),List.of(),List.of("net/minecraft/entity/Entity"),List.of(),List.of(),"not_proven_by_reference_analysis","unresolved");
        var renderer=new LegacyClassDependencyAnalyzer.ClassDependency(
                "foreign/client/RenderEmpty","b",List.of("helper_or_unclassified"),"CLIENT",
                LegacyClassDependencyAnalyzer.Reachability.UNRESOLVED_NOT_PROVEN_UNREACHABLE,
                LegacyClassDependencyAnalyzer.CandidateState.ORIGINAL_BYTES_RETAINED,
                List.of(),List.of("foreign/Orb"),List.of(),List.of(),List.of(),List.of("net/minecraft/client/renderer/entity/Render"),List.of("rendering"),List.of(),"not_proven_by_reference_analysis","unresolved");
        return new LegacyClassDependencyAnalyzer.Analysis(List.of(entity,renderer),List.of(),List.of(),List.of());
    }

    private static void writeSidecars(Path staging,int direct,int unresolved,boolean rewriteRequired,boolean rewriteWired)throws Exception{
        Files.writeString(staging.resolve(LegacyPlainEntityRuntimePass.OUTPUT),"""
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","sourceClass":"foreign/Orb","runtimeComplete":true}]}
                """,StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(LegacyEntityInstantiationPass.OUTPUT),"""
                {
                  "schemaVersion":1,"sourceSha256":"sha","sourceInstantiationInventoryComplete":true,
                  "unresolvedWorldSpawnArgumentCount":%d,
                  "rules":[{"sourceClass":"foreign/Orb","directConstructionCount":%d,"provenWorldSpawnCount":0,
                    "sourceInstantiationRewriteRequired":%s,"sourceInstantiationRewriteWired":%s}]
                }
                """.formatted(unresolved,direct,Boolean.toString(rewriteRequired),Boolean.toString(rewriteWired)),StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(LegacyEntityPresentationPass.OUTPUT),"""
                {
                  "schemaVersion":1,"sourceSha256":"sha","rules":[{
                    "id":"foreign:orb","sourceClass":"foreign/Orb","sourceNoOpRendererProven":true,
                    "registrations":[{"rendererClass":"foreign/client/RenderEmpty","rendererClassPresent":true,"noOpRenderProven":true}]
                  }]
                }
                """,StandardCharsets.UTF_8);
    }

    private static JsonObject read(Path staging)throws Exception{
        return JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityRetirementReadiness.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static void writeClass(Path root,String name,byte[] bytes)throws Exception{Path path=root.resolve(name+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);}
    private static byte[] plain(String name,String superName){ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,superName,null);w.visitEnd();return w.toByteArray();}
    private static byte[] renderer(String name,String entity){ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"render","(L"+entity+";)V",null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,2);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] bootstrap(){ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Bootstrap",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"touch","()V",null,null);m.visitCode();m.visitLdcInsn(Type.getObjectType("foreign/Orb"));m.visitInsn(Opcodes.POP);m.visitLdcInsn(Type.getObjectType("foreign/client/RenderEmpty"));m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.RETURN);m.visitMaxs(1,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
}
