package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyProjectileFullbright1710AnalyzerTest {
    @TempDir Path tempDir;
    private static final String ENTITY="renamed/projectiles/SourceOrb";

    private Path source(boolean srg, boolean missingLight, boolean dynamicBrightness,
                        boolean wrongLight, boolean duplicateBrightness) throws IOException {
        Path jar=tempDir.resolve("light-fixture-" + srg+missingLight+dynamicBrightness+wrongLight+duplicateBrightness+".jar");
        ClassWriter out=new ClassWriter(0);
        out.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,ENTITY,null,"net/minecraft/entity/projectile/EntityThrowable",null);
        MethodVisitor a=out.visitMethod(Opcodes.ACC_PUBLIC,srg?"func_70013_c":"getBrightness","(F)F",null,null);
        a.visitCode();
        if(dynamicBrightness)a.visitVarInsn(Opcodes.FLOAD,1);else a.visitInsn(Opcodes.FCONST_1);
        a.visitInsn(Opcodes.FRETURN);a.visitMaxs(1,2);a.visitEnd();
        if(duplicateBrightness){
            MethodVisitor duplicate=out.visitMethod(Opcodes.ACC_PUBLIC,"func_70013_c","(F)F",null,null);
            duplicate.visitCode();duplicate.visitInsn(Opcodes.FCONST_1);duplicate.visitInsn(Opcodes.FRETURN);
            duplicate.visitMaxs(1,2);duplicate.visitEnd();
        }
        if(!missingLight){
            MethodVisitor b=out.visitMethod(Opcodes.ACC_PUBLIC,srg?"func_70070_b":"getBrightnessForRender","(F)I",null,null);
            b.visitCode();b.visitLdcInsn(wrongLight?0x00D000D0:0x00F000F0);b.visitInsn(Opcodes.IRETURN);
            b.visitMaxs(1,2);b.visitEnd();
        }
        out.visitEnd();
        try(JarOutputStream output=new JarOutputStream(Files.newOutputStream(jar))){
            output.putNextEntry(new JarEntry(ENTITY+".class"));output.write(out.toByteArray());output.closeEntry();
        }
        return jar;
    }
    @Test void directConstantFullbrightIsProven() throws Exception {
        var a=new LegacyProjectileFullbright1710Analyzer().analyze(source(false,false,false,false,false),ENTITY);
        var p=a.proof().orElseThrow();
        assertEquals(1.0F,p.brightness());
        assertEquals(0xF000F0,p.packedLight());
    }
    @Test void srgNamesAreProvenWithoutModSpecificRules() throws Exception {
        var a=new LegacyProjectileFullbright1710Analyzer().analyze(source(true,false,false,false,false),ENTITY);
        assertTrue(a.proof().isPresent());
    }
    @Test void missingLightOverrideIsNotAssumedFullbright() throws Exception {
        var a=new LegacyProjectileFullbright1710Analyzer().analyze(source(false,true,false,false,false),ENTITY);
        assertTrue(a.proof().isEmpty());
    }
    @Test void dynamicLightDependsOnStateAndFailsClosed() throws Exception {
        var a=new LegacyProjectileFullbright1710Analyzer().analyze(source(false,false,true,false,false),ENTITY);
        assertTrue(a.proof().isEmpty());
    }
    @Test void otherConstantPackedLightFailsClosed() throws Exception {
        var a=new LegacyProjectileFullbright1710Analyzer().analyze(source(false,false,false,true,false),ENTITY);
        assertTrue(a.proof().isEmpty());
    }
    @Test void aliasCollisionCannotBeTrusted() throws Exception {
        var a=new LegacyProjectileFullbright1710Analyzer().analyze(source(false,false,false,false,true),ENTITY);
        assertTrue(a.proof().isEmpty());
    }
    @Test void sourceClassMustExistInJar() throws Exception {
        var a=new LegacyProjectileFullbright1710Analyzer().analyze(source(false,false,false,false,false),"renamed/missing/Other");
        assertTrue(a.proof().isEmpty());
    }
}
