package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityConstantOverrideAdmissionTest {
    @TempDir Path tempDir;

    @Test void exactMappedCanPushCallbackIsAdmittedAndCarriedToCodegenIr() throws Exception {
        Path staging=tempDir.resolve("accepted");ConversionContext context=context(staging,"accepted.jar");writeBaseSidecars(staging);writeConstants(staging,true);
        new LegacyEntityRuntimeAdmissionPass().apply(context);
        JsonObject root=read(staging),rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(root.get("constantBehaviorOverrideAdmissionWired").getAsBoolean());assertEquals(1,root.get("admittedRegistrations").getAsInt());assertEquals(1,root.get("admittedConstantBehaviorOverrides").getAsInt());
        assertTrue(rule.get("admitted").getAsBoolean());assertEquals(1,rule.get("constantBehaviorOverrideCount").getAsInt());
        JsonObject mapped=rule.getAsJsonArray("constantBehaviorOverrides").get(0).getAsJsonObject();assertEquals("CAN_PUSH",mapped.get("sourceKind").getAsString());assertFalse(mapped.get("constantBoolean").getAsBoolean());assertEquals("isPushable",mapped.get("targetMethod").getAsString());
    }

    @Test void canPushCallbackWithoutExactConstantProofRemainsBlocked() throws Exception {
        Path staging=tempDir.resolve("blocked");ConversionContext context=context(staging,"blocked.jar");writeBaseSidecars(staging);writeConstants(staging,false);
        new LegacyEntityRuntimeAdmissionPass().apply(context);
        JsonObject rule=read(staging).getAsJsonArray("rules").get(0).getAsJsonObject();assertFalse(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream().anyMatch(value->value.getAsString().equals("constant-override-proof-missing:CAN_PUSH")));
    }

    private ConversionContext context(Path staging,String jarName)throws Exception{
        Files.createDirectories(staging.resolve("legacyforgebridge"));Path source=tempDir.resolve(jarName);try(JarOutputStream ignored=new JarOutputStream(Files.newOutputStream(source))){}
        LegacyModMetadata metadata=new LegacyModMetadata(jarName,"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis analysis=new LegacyJarAnalyzer.Analysis(jarName,0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,tempDir.resolve(jarName+".candidate.jar"),"sha",Files.size(source),metadata,analysis,new DiagnosticCollector(),"generic-test");
    }
    private static JsonObject read(Path staging)throws Exception{return JsonParser.parseString(Files.readString(staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void writeBaseSidecars(Path staging)throws Exception{
        Files.writeString(staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT),"""
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{
                  "id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","legacyNumericId":17,
                  "trackingRange":80,"updateFrequency":2,"velocityUpdates":true,"synchedDataMappingComplete":true,
                  "sourceWideDataWatcherCallClosureComplete":true,"sourceOwnedDataWatcherReadCount":0,"sourceOwnedDataWatcherWriteCount":0,
                  "postInitSourceDataWatcherMutationFree":true,"synchedDataEntries":[]
                }]}
                """,StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(LegacyEntityBehaviorSurfacePass.OUTPUT),"""
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{
                  "id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","externalBaseClass":"net/minecraft/entity/Entity",
                  "sourceOwnedBehaviorInventoryComplete":true,"unclassifiedSourceMethodCount":0,
                  "callbacks":[
                    {"kind":"ENTITY_INIT","owner":"foreign/Orb","method":"func_70088_a","descriptor":"()V"},
                    {"kind":"READ_NBT","owner":"foreign/Orb","method":"func_70037_a","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V"},
                    {"kind":"WRITE_NBT","owner":"foreign/Orb","method":"func_70014_b","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V"},
                    {"kind":"CAN_PUSH","owner":"foreign/Orb","method":"canBePushed","descriptor":"()Z"}
                  ],
                  "sourceMethods":[
                    {"owner":"foreign/Orb","method":"func_70088_a","descriptor":"()V","callbackKind":"ENTITY_INIT","trivialNoOp":false},
                    {"owner":"foreign/Orb","method":"func_70037_a","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V","callbackKind":"READ_NBT","trivialNoOp":true},
                    {"owner":"foreign/Orb","method":"func_70014_b","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V","callbackKind":"WRITE_NBT","trivialNoOp":true},
                    {"owner":"foreign/Orb","method":"canBePushed","descriptor":"()Z","callbackKind":"CAN_PUSH","trivialNoOp":false}
                  ]
                }]}
                """,StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(LegacyEntityConstructionPass.OUTPUT),"""
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{
                  "id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","externalBaseClass":"net/minecraft/entity/Entity",
                  "worldConstructorPresent":true,"constructorChainComplete":true,"constructorControlFlowSimple":true,"sourceSetSizeOverridePresent":false,
                  "sizeProofComplete":true,"width":0.5,"height":0.75,"unmappedConstructorEffectCount":0
                }]}
                """,StandardCharsets.UTF_8);
    }
    private static void writeConstants(Path staging,boolean proven)throws Exception{
        String mapped=proven?"""
                [{"sourceKind":"CAN_PUSH","sourceOwner":"foreign/Orb","sourceMethod":"canBePushed","sourceDescriptor":"()Z",
                  "targetOwner":"net/minecraft/world/entity/Entity","targetMethod":"isPushable","targetDescriptor":"()Z",
                  "mappingSemantics":"PUSHABILITY_BOOLEAN_IDENTITY","sourceConstantProofComplete":true,"constantBoolean":false,"runtimeCodegenReady":true}]
                """:"[]";
        Files.writeString(staging.resolve(LegacyEntityConstantOverridePass.OUTPUT),"""
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{
                  "id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","constantOverrides":%s,"blockedOverrides":%s
                }]}
                """.formatted(mapped,proven?"[]":"[{\"sourceKind\":\"CAN_PUSH\",\"proofReason\":\"boolean-callback-not-exact-constant-return\"}]"),StandardCharsets.UTF_8);
    }
}
