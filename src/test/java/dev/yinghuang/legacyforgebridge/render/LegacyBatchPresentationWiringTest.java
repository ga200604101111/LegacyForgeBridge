package dev.yinghuang.legacyforgebridge.render;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import dev.yinghuang.legacyforgebridge.mixin.client.LegacyHeldItemVisibilityMixin;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LegacyBatchPresentationWiringTest {
    private static ClassNode node(Class<?> type)throws Exception{
        try(var in=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){
            assertNotNull(in);ClassNode n=new ClassNode();new ClassReader(in).accept(n,0);return n;
        }
    }
    private static List<String> calls(MethodNode method){
        List<String> calls=new ArrayList<>();for(var i:method.instructions)if(i instanceof MethodInsnNode m)calls.add(m.name);return calls;
    }
    @Test void nativeGeometryWrapperHonoursModelOwnershipBeforeReplacingMesh()throws Exception{
        boolean found=false;
        for(var method:node(ConvertedGeometryClient.class).methods){
            int ownership=-1,replace=-1,index=0;
            for(var i:method.instructions){
                if(i instanceof MethodInsnNode m&&m.owner.endsWith("LegacyGeometrySpec$Rule")&&m.name.equals("modelOwned"))ownership=index;
                if(i instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&t.desc.endsWith("ConvertedGeometryClient$GeometryModel"))replace=index;
                index++;
            }
            if(replace>=0){found=true;assertTrue(ownership>=0&&ownership<replace);}
        }
        assertTrue(found);
    }
    @Test void pickingReadsCurrentHandBeforeLegacyOutlineMetadata()throws Exception{
        var method=node(ConvertedLegacyBlock.class).methods.stream().filter(m->m.name.equals("getShape")).findFirst().orElseThrow();
        var calls=calls(method);assertTrue(calls.contains("isHoldingItem"));assertTrue(calls.contains("selectionShape"));
        assertTrue(calls.indexOf("selectionShape")<calls.indexOf("legacyMeta"));
        var collision=node(ConvertedLegacyBlock.class).methods.stream().filter(m->m.name.equals("getCollisionShape")).findFirst().orElseThrow();
        assertTrue(calls(collision).contains("emptyCollision"));assertFalse(calls(collision).contains("selectionShape"));
    }
    @Test void clientOutlineFallbackUsesEmptyContextAndHandWithoutWorldMutation()throws Exception{
        var method=node(LegacyHeldItemVisibilityMixin.class).methods.stream().filter(m->m.name.equals("lfb$currentHandSelection")).findFirst().orElseThrow();
        var calls=calls(method);assertTrue(calls.contains("empty"));assertTrue(calls.contains("getMainHandItem"));
        assertTrue(calls.contains("selectionShape"));assertFalse(calls.contains("setBlock"));
        assertFalse(calls.contains("legacyMeta"));
        assertTrue(Arrays.stream(method.instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&f.name.equals("level")),"Must be scoped to the active client level");
    }
}
