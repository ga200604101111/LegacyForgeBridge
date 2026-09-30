package dev.yinghuang.legacyforgebridge.protocol;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ViaFabricPlusEntrypointBytecodeTest {
    @Test void platformLoadInitializesThenSelects1710Family() throws Exception {
        String resource="/"+ViaFabricPlusEntrypoint.class.getName().replace('.','/')+".class";
        try(InputStream input=ViaFabricPlusEntrypoint.class.getResourceAsStream(resource)){
            assertNotNull(input);
            List<String> calls=new ArrayList<>();
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
                    if(!name.equals("onPlatformLoad"))return null;
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                            if(owner.endsWith("/ViaFabricPlusBackend"))calls.add(method+descriptor);
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertEquals(List.of(
                    "initialize(Lcom/viaversion/viafabricplus/api/ViaFabricPlusBase;)V",
                    "selectMinecraft1710ForNextConnection()V"
            ),calls);
        }
    }
}
