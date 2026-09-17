package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPlainEntityConstantOverrideCodegenPassTest {
    @TempDir Path tempDir;

    @Test void patchesGeneratedEntityWithAdmissionProvenIsPushableConstant() throws Exception {
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        ConversionContext context=context(staging,"source.jar");writeAdmission(staging,false);
        new LegacyPlainEntityCodegenPass().apply(context);
        new LegacyPlainEntityConstantOverrideCodegenPass().apply(context);

        JsonObject generated=JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityCodegenPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(generated.get("constantBehaviorOverrideCodegenWired").getAsBoolean());
        JsonObject rule=generated.getAsJsonArray("generatedClasses").get(0).getAsJsonObject();
        assertTrue(rule.get("constantBehaviorOverrideCodegenComplete").getAsBoolean());assertEquals(1,rule.get("constantBehaviorOverrideCount").getAsInt());
        String internal=rule.get("generatedInternalName").getAsString();ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(Files.readAllBytes(staging.resolve(internal+".class"))).accept(node,0);
        MethodNode method=node.methods.stream().filter(value->value.name.equals("isPushable")&&value.desc.equals("()Z")).findFirst().orElse(null);assertNotNull(method);
        var opcodes=method.instructions.iterator();assertTrue(opcodes.hasNext());var first=opcodes.next();while(first.getOpcode()<0&&opcodes.hasNext())first=opcodes.next();assertEquals(Opcodes.ICONST_0,first.getOpcode());var second=opcodes.next();while(second.getOpcode()<0&&opcodes.hasNext())second=opcodes.next();assertEquals(Opcodes.IRETURN,second.getOpcode());
        JsonObject report=JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityConstantOverrideCodegenPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();assertEquals(1,report.get("patchedConstantOverrideMethods").getAsInt());assertEquals(0,report.get("blockedCodegenClasses").getAsInt());
    }

    @Test void unsupportedTargetLeavesGeneratedClassFailClosedForRuntimeCandidate() throws Exception {
        Path staging=tempDir.resolve("blocked");Files.createDirectories(staging.resolve("legacyforgebridge"));ConversionContext context=context(staging,"blocked.jar");writeAdmission(staging,true);
        new LegacyPlainEntityCodegenPass().apply(context);new LegacyPlainEntityConstantOverrideCodegenPass().apply(context);
        JsonObject rule=JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityCodegenPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("generatedClasses").get(0).getAsJsonObject();
        assertFalse(rule.get("constantBehaviorOverrideCodegenComplete").getAsBoolean());
        JsonObject report=JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityConstantOverrideCodegenPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(report.getAsJsonArray("blockers").asList().stream().anyMatch(value->value.getAsString().equals("unsupported-or-unproven-constant-override")));
    }

    private ConversionContext context(Path staging,String jarName)throws Exception{
        Path source=tempDir.resolve(jarName);try(JarOutputStream ignored=new JarOutputStream(Files.newOutputStream(source))){}
        LegacyModMetadata metadata=new LegacyModMetadata(jarName,"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis analysis=new LegacyJarAnalyzer.Analysis(jarName,0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,tempDir.resolve(jarName+".candidate.jar"),"sha",Files.size(source),metadata,analysis,new DiagnosticCollector(),"generic-test");
    }
    private static void writeAdmission(Path staging,boolean wrongTarget)throws Exception{Files.writeString(staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT),"""
            {
              "schemaVersion":1,"sourceSha256":"sha","rules":[{
                "id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","legacyNumericId":17,
                "trackingRange":80,"updateFrequency":2,"velocityUpdates":true,"width":0.5,"height":0.75,
                "family":"PLAIN_ENTITY_SYNCHED_DATA_ONLY","admitted":true,"synchedDataEntries":[],
                "constantBehaviorOverrides":[{
                  "sourceKind":"CAN_PUSH","sourceOwner":"foreign/Orb","sourceMethod":"canBePushed","sourceDescriptor":"()Z",
                  "targetOwner":"net/minecraft/world/entity/Entity","targetMethod":"%s","targetDescriptor":"()Z",
                  "mappingSemantics":"PUSHABILITY_BOOLEAN_IDENTITY","sourceConstantProofComplete":true,"constantBoolean":false,"runtimeCodegenReady":true
                }]
              }]
            }
            """.formatted(wrongTarget?"canBeCollidedWith":"isPushable"),StandardCharsets.UTF_8);}
}
