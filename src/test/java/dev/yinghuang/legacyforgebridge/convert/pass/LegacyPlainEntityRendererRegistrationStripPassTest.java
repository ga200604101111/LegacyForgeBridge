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

class LegacyPlainEntityRendererRegistrationStripPassTest {
    @TempDir Path tempDir;

    @Test void stripsOnlyNoOpRendererWithProvenTrivialConstructorAndClearsRegistrarReferences() throws Exception {
        Path source=tempDir.resolve("source.jar");byte[] entity=entity(),renderer=renderer(false),registrar=registrar();
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){put(out,"third/entity/Orb.class",entity);put(out,"third/client/RenderEmpty.class",renderer);put(out,"third/client/ClientRegistrar.class",registrar);}
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        writeClass(staging,"third/entity/Orb",entity);writeClass(staging,"third/client/RenderEmpty",renderer);writeClass(staging,"third/client/ClientRegistrar",registrar);
        writeRuntime(staging);writePresentation(staging);

        new LegacyPlainEntityRendererRegistrationStripPass().apply(context(source,staging));

        JsonObject root=JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityRendererRegistrationStripPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1,root.get("rendererRegistrationStripCompleteRules").getAsInt());assertEquals(1,root.get("strippedRendererRegistrationSites").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("rendererRegistrationStripComplete").getAsBoolean());assertTrue(rule.get("rendererConstructorChainSideEffectFree").getAsBoolean());assertEquals("net/minecraft/client/renderer/entity/Render",rule.get("rendererConstructorTerminalBase").getAsString());
        var refs=new LegacyCandidateReferenceAnalyzer().analyze(staging,Set.of("third/entity/Orb","third/client/RenderEmpty"));
        assertFalse(refs.forTarget("third/client/RenderEmpty").incomingClassReferences().contains("third/client/ClientRegistrar"));
        assertFalse(refs.forTarget("third/entity/Orb").incomingClassReferences().contains("third/client/ClientRegistrar"));
        assertTrue(refs.forTarget("third/entity/Orb").incomingClassReferences().contains("third/client/RenderEmpty"),"renderer callback descriptor remains the permitted retirement-cohort edge");
    }

    @Test void sideEffectingRendererConstructorKeepsRegistrationUntouched() throws Exception {
        Path source=tempDir.resolve("blocked.jar");byte[] entity=entity(),renderer=renderer(true),registrar=registrar();
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){put(out,"third/entity/Orb.class",entity);put(out,"third/client/RenderEmpty.class",renderer);put(out,"third/client/ClientRegistrar.class",registrar);}
        Path staging=tempDir.resolve("blocked-staging");Files.createDirectories(staging.resolve("legacyforgebridge"));writeClass(staging,"third/entity/Orb",entity);writeClass(staging,"third/client/RenderEmpty",renderer);writeClass(staging,"third/client/ClientRegistrar",registrar);writeRuntime(staging);writePresentation(staging);

        new LegacyPlainEntityRendererRegistrationStripPass().apply(context(source,staging));

        JsonObject rule=JsonParser.parseString(Files.readString(staging.resolve(LegacyPlainEntityRendererRegistrationStripPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("rendererRegistrationStripComplete").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream().anyMatch(value->value.getAsString().contains("renderer-constructor-chain-not-proven-side-effect-free:noarg-constructor-has-side-effects")));
        var refs=new LegacyCandidateReferenceAnalyzer().analyze(staging,Set.of("third/client/RenderEmpty"));
        assertTrue(refs.forTarget("third/client/RenderEmpty").incomingClassReferences().contains("third/client/ClientRegistrar"));
    }

    private ConversionContext context(Path source,Path staging)throws Exception{
        LegacyModMetadata metadata=new LegacyModMetadata(source.getFileName().toString(),"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(source.getFileName().toString(),0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,tempDir.resolve(source.getFileName()+".candidate.jar"),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
    }
    private static void writeRuntime(Path staging)throws Exception{Files.writeString(staging.resolve(LegacyPlainEntityRuntimePass.OUTPUT),"""
            {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","sourceClass":"third/entity/Orb","runtimeComplete":true}]}
            """,StandardCharsets.UTF_8);}
    private static void writePresentation(Path staging)throws Exception{Files.writeString(staging.resolve(LegacyEntityPresentationPass.OUTPUT),"""
            {
              "schemaVersion":1,"sourceSha256":"sha","rules":[{
                "id":"foreign:orb","sourceClass":"third/entity/Orb","sourceNoOpRendererProven":true,
                "registrations":[{
                  "rendererClass":"third/client/RenderEmpty","sourceOwner":"third/client/ClientRegistrar","sourceMethod":"register","sourceDescriptor":"()V",
                  "rendererClassPresent":true,"noOpRenderProven":true
                }]
              }]
            }
            """,StandardCharsets.UTF_8);}
    private static byte[] entity(){ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"third/entity/Orb",null,"net/minecraft/entity/Entity",null);w.visitEnd();return w.toByteArray();}
    private static byte[] renderer(boolean sideEffect){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"third/client/RenderEmpty",null,"net/minecraft/client/renderer/entity/Render",null);if(sideEffect)w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"TOUCHED","Z",null,null).visitEnd();MethodVisitor ctor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/renderer/entity/Render","<init>","()V",false);if(sideEffect){ctor.visitInsn(Opcodes.ICONST_1);ctor.visitFieldInsn(Opcodes.PUTSTATIC,"third/client/RenderEmpty","TOUCHED","Z");}ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();MethodVisitor render=w.visitMethod(Opcodes.ACC_PUBLIC,"doRender","(Lthird/entity/Orb;DDDFF)V",null,null);render.visitCode();render.visitInsn(Opcodes.RETURN);render.visitMaxs(0,0);render.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] registrar(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"third/client/ClientRegistrar",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","()V",null,null);m.visitCode();m.visitLdcInsn(Type.getObjectType("third/entity/Orb"));m.visitTypeInsn(Opcodes.NEW,"third/client/RenderEmpty");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"third/client/RenderEmpty","<init>","()V",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry","registerEntityRenderingHandler","(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void writeClass(Path root,String name,byte[] bytes)throws Exception{Path path=root.resolve(name+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
