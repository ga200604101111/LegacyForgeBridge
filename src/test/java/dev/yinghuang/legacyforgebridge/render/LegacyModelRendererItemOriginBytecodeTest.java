package dev.yinghuang.legacyforgebridge.render;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyModelRendererItemOriginBytecodeTest {
    private static final String HELPER_OWNER="dev/yinghuang/legacyforgebridge/render/LegacyRenderMath";
    private static final String HELPER_NAME="restoreLegacyModelRendererItemOrigin";
    private static final String HELPER_DESC="(Lcom/mojang/blaze3d/vertex/PoseStack;)V";

    @Test
    void centeredLegacyModelRendererSpecialFamiliesCancelModernHalfBlockOrigin() throws Exception {
        Map<Class<?>,Integer> minimumCalls=new LinkedHashMap<>();
        minimumCalls.put(ConvertedLegacyInertModelSpecialRenderer.class,1);
        minimumCalls.put(ConvertedLegacyRadialModelSpecialRenderer.class,2);
        minimumCalls.put(ConvertedLegacyOscillatingModelSpecialRenderer.class,1);
        minimumCalls.put(ConvertedMetadataRotatingSpecialRenderer.class,1);
        minimumCalls.put(ConvertedLegacyProcessorSpecialRenderer.class,1);

        for(var entry:minimumCalls.entrySet()){
            String resource="/"+entry.getKey().getName().replace('.','/')+".class";
            try(InputStream input=entry.getKey().getResourceAsStream(resource)){
                assertNotNull(input,resource);
                int[] calls={0};
                new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                        return new MethodVisitor(Opcodes.ASM9){
                            @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean isInterface){
                                if(opcode==Opcodes.INVOKESTATIC&&HELPER_OWNER.equals(owner)
                                        &&HELPER_NAME.equals(method)&&HELPER_DESC.equals(desc))calls[0]++;
                            }
                        };
                    }
                },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                assertTrue(calls[0]>=entry.getValue(),
                        entry.getKey().getSimpleName()+" must restore legacy ModelRenderer item origin");
            }
        }
    }

    @Test
    void helperUsesExactHalfBlockTranslation() throws Exception {
        String resource="/"+LegacyRenderMath.class.getName().replace('.','/')+".class";
        try(InputStream input=LegacyRenderMath.class.getResourceAsStream(resource)){
            assertNotNull(input);
            int[] halfConstants={0},translate={0};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if(!HELPER_NAME.equals(name)||!HELPER_DESC.equals(descriptor))return null;
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitLdcInsn(Object value){
                            if(value instanceof Double d&&Double.compare(d,0.5D)==0)halfConstants[0]++;
                        }
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean isInterface){
                            if("com/mojang/blaze3d/vertex/PoseStack".equals(owner)&&"translate".equals(method)
                                    &&"(DDD)V".equals(desc))translate[0]++;
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertEquals(3,halfConstants[0]);
            assertEquals(1,translate[0]);
        }
    }
}
