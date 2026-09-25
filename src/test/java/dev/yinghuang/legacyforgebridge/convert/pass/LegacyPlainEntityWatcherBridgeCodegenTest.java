package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPlainEntityWatcherBridgeCodegenTest {
    @TempDir Path tempDir;

    @Test void generatedEntityImplementsTypedSourceIndexWatcherBridge() throws Exception {
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT),"""
                {
                  "schemaVersion":1,
                  "sourceSha256":"sha",
                  "rules":[{
                    "id":"foreign:orb",
                    "legacyRegistryName":"orb",
                    "sourceClass":"foreign/Orb",
                    "legacyNumericId":23,
                    "trackingRange":80,
                    "updateFrequency":2,
                    "velocityUpdates":true,
                    "width":0.5,
                    "height":0.75,
                    "family":"PLAIN_ENTITY_SYNCHED_DATA_ONLY",
                    "admitted":true,
                    "synchedDataEntries":[
                      {"sourceIndex":12,"modernValueKind":"byte","serializer":"BYTE","adapter":"identity","defaultValue":1},
                      {"sourceIndex":13,"modernValueKind":"int","serializer":"INT","adapter":"signed_short_widen","defaultValue":-4}
                    ]
                  }]
                }
                """,StandardCharsets.UTF_8);
        ConversionContext context=context(staging);
        new LegacyPlainEntityCodegenPass().apply(context);
        var root=com.google.gson.JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityCodegenPass.OUTPUT))).getAsJsonObject();
        String internal=root.getAsJsonArray("generatedClasses").get(0).getAsJsonObject().get("generatedInternalName").getAsString();
        ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(Files.readAllBytes(staging.resolve(internal+".class"))).accept(node,0);
        assertTrue(node.interfaces.contains("dev/yinghuang/legacyforgebridge/compat/LegacyPlainEntityWatcherBridge"));
        MethodNode method=node.methods.stream().filter(value->value.name.equals("legacyforgebridge$applyWatcher")&&value.desc.equals("(IILjava/lang/Object;)Z")).findFirst().orElse(null);
        assertNotNull(method);
        boolean get=false,set=false;
        for(var insn:method.instructions)if(insn instanceof MethodInsnNode call){
            if(call.owner.equals("net/minecraft/world/entity/Entity")&&call.name.equals("getEntityData"))get=true;
            if(call.owner.equals("net/minecraft/network/syncher/SynchedEntityData")&&call.name.equals("set"))set=true;
        }
        assertTrue(get);assertTrue(set);
    }

    private ConversionContext context(Path staging)throws Exception{
        Path source=tempDir.resolve("source.jar");try(JarOutputStream ignored=new JarOutputStream(Files.newOutputStream(source))){}
        LegacyModMetadata metadata=new LegacyModMetadata("source.jar","test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis analysis=new LegacyJarAnalyzer.Analysis("source.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,tempDir.resolve("candidate.jar"),"sha",Files.size(source),metadata,analysis,new DiagnosticCollector(),"generic-test");
    }
}
