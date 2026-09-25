package dev.yinghuang.legacyforgebridge.convert;

import java.util.ArrayList;
import java.util.List;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Operation;

/** Affine basis conversion, not per-weapon visual offsets. Matrices post-multiply, as legacy GL did. */
public final class LegacyHandSpace {
    private LegacyHandSpace() { }
    public static List<Operation> thirdPerson(boolean full3D,boolean rotates,boolean left){
        List<Operation> ops=new ArrayList<>();
        // Inverse of ItemInHandLayer's transform after the animated hand attachment.
        ops.add(Operation.of("translate",-.0625F,-.125F,.625F));
        ops.add(Operation.of("rotate",-180,0,1,0));
        ops.add(Operation.of("rotate",90,1,0,0));
        // Forge 1.7.10 RenderPlayer, relative to bipedRightArm.postRender(1/16).
        ops.add(Operation.of("translate",-.0625F,.4375F,.0625F));
        if(full3D){
            if(rotates){ops.add(Operation.of("rotate",180,0,0,1));ops.add(Operation.of("translate",0,-.125F,0));}
            ops.add(Operation.of("translate",0,.1875F,0));ops.add(Operation.of("scale",.625F,-.625F,.625F));
            ops.add(Operation.of("rotate",-100,1,0,0));ops.add(Operation.of("rotate",45,0,1,0));
        }else{
            ops.add(Operation.of("translate",.25F,.1875F,-.1875F));ops.add(Operation.of("scale",.375F,.375F,.375F));
            ops.add(Operation.of("rotate",60,0,0,1));ops.add(Operation.of("rotate",-90,1,0,0));ops.add(Operation.of("rotate",20,0,1,0));
        }
        ops.addAll(spriteHelper());
        return left?mirror(ops):ops;
    }
    public static List<Operation> firstPerson(boolean left){
        List<Operation> ops=new ArrayList<>();
        // ItemInHandRenderer supplies camera/equip/swing/use poses. Restore the old local basis only.
        ops.add(Operation.of("rotate",45,0,1,0));ops.add(Operation.of("scale",.4F,.4F,.4F));
        ops.addAll(spriteHelper());return left?mirror(ops):ops;
    }
    public static List<Operation> spriteHelper(){return List.of(
            Operation.of("translate",0,-.3F,0),Operation.of("scale",1.5F,1.5F,1.5F),
            Operation.of("rotate",50,0,1,0),Operation.of("rotate",335,0,0,1),
            Operation.of("translate",-.9375F,-.0625F,0));}
    /** Reflection-conjugate transforms without reversing triangle winding or negating model scale. */
    public static List<Operation> mirror(List<Operation> input){
        List<Operation> result=new ArrayList<>();
        for(Operation op:input){var v=op.values();
            if(op.op().equals("translate"))result.add(Operation.of("translate",-v.get(0),v.get(1),v.get(2)));
            else if(op.op().equals("rotate"))result.add(Operation.of("rotate",v.get(0),v.get(1),-v.get(2),-v.get(3)));
            else result.add(op);
        }
        return result;
    }
}
