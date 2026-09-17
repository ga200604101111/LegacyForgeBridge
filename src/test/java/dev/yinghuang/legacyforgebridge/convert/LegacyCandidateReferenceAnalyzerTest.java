package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyCandidateReferenceAnalyzerTest {
    @TempDir Path tempDir;

    @Test void scansCurrentCandidateBytecodeAndRuntimeResourcesWhileIgnoringLfbSidecars() throws Exception {
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging);
        writeClass(staging,"foreign/Orb",plain("foreign/Orb","net/minecraft/entity/Entity"));
        writeClass(staging,"foreign/client/RenderOrb",renderer("foreign/client/RenderOrb","foreign/Orb"));
        writeClass(staging,"foreign/Bootstrap",bootstrap("foreign/Orb"));
        Files.createDirectories(staging.resolve("assets/foreign"));
        Files.writeString(staging.resolve("assets/foreign/config.txt"),"renderer=foreign.client.RenderOrb\n",StandardCharsets.UTF_8);
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve("legacyforgebridge/debug.json"),"{\"sourceClass\":\"foreign/Orb\"}",StandardCharsets.UTF_8);

        LegacyCandidateReferenceAnalyzer.Analysis analysis=new LegacyCandidateReferenceAnalyzer().analyze(
                staging, Set.of("foreign/Orb","foreign/client/RenderOrb"));
        assertTrue(analysis.complete());
        var orb=analysis.forTarget("foreign/Orb");
        assertTrue(orb.incomingClassReferences().contains("foreign/Bootstrap"));
        assertTrue(orb.incomingClassReferences().contains("foreign/client/RenderOrb"));
        assertTrue(orb.resourceReferences().isEmpty(),"LFB sidecar references must not block source retirement");
        var renderer=analysis.forTarget("foreign/client/RenderOrb");
        assertTrue(renderer.resourceReferences().contains("assets/foreign/config.txt"));
    }

    @Test void rewrittenCandidateReferenceDisappearsWithoutConsultingOriginalSourceGraph() throws Exception {
        Path staging=tempDir.resolve("rewritten");Files.createDirectories(staging);
        writeClass(staging,"foreign/Orb",plain("foreign/Orb","net/minecraft/entity/Entity"));
        writeClass(staging,"foreign/Bootstrap",plain("foreign/Bootstrap","java/lang/Object"));
        var analysis=new LegacyCandidateReferenceAnalyzer().analyze(staging,Set.of("foreign/Orb"));
        assertTrue(analysis.forTarget("foreign/Orb").incomingClassReferences().isEmpty());
    }

    private static void writeClass(Path root,String name,byte[] bytes)throws Exception{
        Path path=root.resolve(name+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);
    }
    private static byte[] plain(String name,String superName){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,superName,null);w.visitEnd();return w.toByteArray();
    }
    private static byte[] renderer(String name,String entity){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"render","(L"+entity+";)V",null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] bootstrap(String entity){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"touch","()V",null,null);m.visitCode();m.visitLdcInsn(Type.getObjectType(entity));m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.RETURN);m.visitMaxs(1,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
