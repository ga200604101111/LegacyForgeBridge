package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.BehaviorFixture;
import dev.yinghuang.legacyforgebridge.convert.Hashing;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorPassTest {
    @TempDir Path temp;
    @Test void candidateInitializesItsCompiledCallbacksBeforeRegisteringNativeItems()throws Exception {
        Path source=BehaviorFixture.create(temp.resolve("fixture"),"alchemy",false);
        Path staging=temp.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve("legacyforgebridge/converted-content.json"),"""
                {"items":[{"id":"alchemy:blade","kind":"sword"},{"id":"alchemy:cloak","kind":"item"}]}
                """);
        var metadata=LegacyModMetadata.read(source);
        var context=new ConversionContext(source,staging,temp.resolve("alchemy-lfb.jar"),Hashing.sha256(source),
                Files.size(source),metadata,new LegacyJarAnalyzer().analyze(source),new DiagnosticCollector(),"fixture");
        new LegacyBehaviorPass().apply(context);
        String bootstrap=Files.readString(staging.resolve(LegacyBehaviorPass.MARKER)).trim();
        assertTrue(Files.isRegularFile(staging.resolve(bootstrap+".class")));
        assertTrue(Files.readString(staging.resolve("legacyforgebridge/behavior-analysis.json")).contains("tooltip"));
        new GeneratedSemanticCodePass().apply(context);
        Path generated=staging.resolve(GeneratedModEntrypointPass.generatedContentClass(metadata).replace('.','/')+".class");
        List<String> calls=new ArrayList<>();
        new ClassReader(Files.readAllBytes(generated)).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[]e){return new MethodVisitor(Opcodes.ASM9){
                @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){calls.add(owner+"."+name);}
            };}
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        int initialize=calls.indexOf(bootstrap+".initialize");
        int registration=-1;for(int i=0;i<calls.size();i++)if(calls.get(i).endsWith("GeneratedModSupport.registerItem")){registration=i;break;}
        assertTrue(initialize>=0&&registration>initialize,calls.toString());
        assertTrue(calls.stream().noneMatch(c->c.contains("ConvertedContentRuntime")));
    }
}
