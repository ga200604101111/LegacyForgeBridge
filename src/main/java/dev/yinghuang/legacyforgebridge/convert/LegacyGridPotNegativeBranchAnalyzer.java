package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static dev.yinghuang.legacyforgebridge.convert.LegacyGridPotAsm.*;

/** Bounded source proof for the legacy GridPot non-positive insertion fallback branch. */
public final class LegacyGridPotNegativeBranchAnalyzer {
    private static final String WORLD="net/minecraft/world/World";
    private static final String PLAYER="net/minecraft/entity/player/EntityPlayer";
    private static final String CAPS="net/minecraft/entity/player/PlayerCapabilities";
    private static final String ITEM="net/minecraft/item/Item";
    private static final String STACK="net/minecraft/item/ItemStack";
    private static final String BLOCK_TYPE="net/minecraft/block/Block";
    private static final String DROP_DESC="(Lnet/minecraft/world/World;IIILnet/minecraft/item/ItemStack;)V";

    private enum RouteSink { POSITIVE, NEGATIVE }

    public record Proof(String registryName,String sourceBlockClass,boolean negativeBranchProven,List<String> blockers){
        public Proof{blockers=List.copyOf(blockers);}
    }
    public record Analysis(List<Proof> proofs,List<String> diagnostics){
        public Analysis{proofs=List.copyOf(proofs);diagnostics=List.copyOf(diagnostics);}
    }

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=loadClasses(jarPath);
        LegacyGridPotBlockAnalyzer.Analysis core=new LegacyGridPotBlockAnalyzer().analyze(jarPath);
        List<Proof> proofs=new ArrayList<>();
        for(LegacyGridPotBlockAnalyzer.Rule rule:core.rules()){
            ClassNode block=classes.get(rule.sourceBlockClass()),tile=classes.get(rule.sourceTileClass());
            LegacyGridPotProofs.TileShape shape=LegacyGridPotProofs.tileShape(tile);
            MethodNode activation=method(classes,rule.sourceBlockClass(),Set.of("onBlockActivated","func_149727_a"),
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z");
            List<String> blockers=proveNegativeBranch(block,activation,rule.sourceTileClass(),shape);
            proofs.add(new Proof(rule.registryName(),rule.sourceBlockClass(),blockers.isEmpty(),blockers));
        }
        LinkedHashSet<String> diagnostics=new LinkedHashSet<>(core.diagnostics());
        return new Analysis(proofs,List.copyOf(diagnostics));
    }

    static List<String> proveNegativeBranch(ClassNode block,MethodNode activation,String tileClass,LegacyGridPotProofs.TileShape shape){
        List<String> blockers=new ArrayList<>();
        if(block==null||activation==null||tileClass==null||shape==null){blockers.add("missing-block-activation-or-tile-proof");return blockers;}
        String helperDesc="(L"+WORLD+";L"+tileClass+";IIIIL"+PLAYER+";)V";
        MethodNode helper=findMethod(block,helperDesc,m->removeSlotHelper(m,tileClass,shape));
        if(helper==null)blockers.add("canonical-remove-slot-helper-missing");
        else {
            if(countCalls(activation,block.name,helper.name,helper.desc)<2)blockers.add("activation-does-not-reach-remove-slot-helper-twice");
            if(!renderPredicateRoutesToPositiveAndNegative(block,activation,tileClass,shape,helper))blockers.add("render-predicate-negative-route-unproven");
        }
        if(!LegacyGridPotProofs.canonicalContentInsertionPredicate(activation,tileClass,shape))blockers.add("positive-render-predicate-proof-missing");
        if(countCalls(activation,tileClass,shape.itemGetter().name,shape.itemGetter().desc)<3)blockers.add("fallback-item-presence-check-incomplete");
        if(countCalls(activation,tileClass,shape.removeItem().name,shape.removeItem().desc)<3)blockers.add("fallback-remove-item-path-incomplete");
        if(!hasCallDescriptor(activation,DROP_DESC))blockers.add("fallback-drop-path-missing");
        return List.copyOf(blockers);
    }

    /**
     * Proves the render-identity predicate is not merely co-located with both behaviors. Every
     * admitted positive identity (1/13/40 or a symbolic static render id) must route to the
     * insertion setter before it can reach the negative helper, and at least one rejected edge
     * must route to the negative helper before it can reach the setter.
     */
    private static boolean renderPredicateRoutesToPositiveAndNegative(ClassNode block,MethodNode activation,String tileClass,
                                                                       LegacyGridPotProofs.TileShape shape,MethodNode helper){
        int admittedComparisons=0;boolean rejectedEdgeReachesNegative=false;
        for(AbstractInsnNode insn=activation.instructions.getFirst();insn!=null;insn=insn.getNext()){
            if(!(insn instanceof MethodInsnNode call)||!BLOCK_TYPE.equals(call.owner)||!"func_149645_b".equals(call.name)||!"()I".equals(call.desc))continue;
            AbstractInsnNode identity=nextReal(call),branchNode=nextReal(identity);
            if(!(branchNode instanceof JumpInsnNode jump))continue;
            Integer constant=intConstant(identity);
            boolean admittedConstant=constant!=null&&(constant==1||constant==13||constant==40);
            boolean admittedSymbolic=identity instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&"I".equals(field.desc);
            if(!admittedConstant&&!admittedSymbolic)continue;
            if(jump.getOpcode()!=Opcodes.IF_ICMPEQ&&jump.getOpcode()!=Opcodes.IF_ICMPNE)return false;
            admittedComparisons++;

            AbstractInsnNode accepted=jump.getOpcode()==Opcodes.IF_ICMPEQ?jump.label:jump.getNext();
            AbstractInsnNode rejected=jump.getOpcode()==Opcodes.IF_ICMPEQ?jump.getNext():jump.label;
            EnumSet<RouteSink> acceptedSinks=firstRouteSinks(accepted,block,tileClass,shape.setItem(),helper);
            if(!acceptedSinks.equals(EnumSet.of(RouteSink.POSITIVE)))return false;
            EnumSet<RouteSink> rejectedSinks=firstRouteSinks(rejected,block,tileClass,shape.setItem(),helper);
            if(rejectedSinks.equals(EnumSet.of(RouteSink.NEGATIVE)))rejectedEdgeReachesNegative=true;
        }
        return admittedComparisons>=4&&rejectedEdgeReachesNegative;
    }

    private static EnumSet<RouteSink> firstRouteSinks(AbstractInsnNode start,ClassNode block,String tileClass,MethodNode setter,MethodNode helper){
        EnumSet<RouteSink> sinks=EnumSet.noneOf(RouteSink.class);
        if(start==null)return sinks;
        Set<AbstractInsnNode> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<AbstractInsnNode> work=new ArrayDeque<>();work.add(start);
        while(!work.isEmpty()){
            AbstractInsnNode current=work.removeFirst();if(current==null||!visited.add(current))continue;
            if(current instanceof MethodInsnNode call){
                if(tileClass.equals(call.owner)&&setter.name.equals(call.name)&&setter.desc.equals(call.desc)){sinks.add(RouteSink.POSITIVE);continue;}
                if(block.name.equals(call.owner)&&helper.name.equals(call.name)&&helper.desc.equals(call.desc)){sinks.add(RouteSink.NEGATIVE);continue;}
            }
            int opcode=current.getOpcode();
            if((opcode>=Opcodes.IRETURN&&opcode<=Opcodes.RETURN)||opcode==Opcodes.ATHROW||opcode==Opcodes.RET)continue;
            if(current instanceof JumpInsnNode jump){
                enqueue(work,jump.label);
                if(opcode!=Opcodes.GOTO)enqueue(work,current.getNext());
                continue;
            }
            if(current instanceof TableSwitchInsnNode table){
                enqueue(work,table.dflt);for(LabelNode label:table.labels)enqueue(work,label);continue;
            }
            if(current instanceof LookupSwitchInsnNode lookup){
                enqueue(work,lookup.dflt);for(LabelNode label:lookup.labels)enqueue(work,label);continue;
            }
            enqueue(work,current.getNext());
        }
        return sinks;
    }

    private static void enqueue(ArrayDeque<AbstractInsnNode> work,AbstractInsnNode instruction){if(instruction!=null)work.addLast(instruction);}

    private static boolean removeSlotHelper(MethodNode method,String tileClass,LegacyGridPotProofs.TileShape shape){
        return method!=null
                &&calls(method,tileClass,shape.enabledGetter().name,shape.enabledGetter().desc)
                &&calls(method,tileClass,shape.removeCell().name,shape.removeCell().desc)
                &&calls(method,tileClass,shape.emptyCheck().name,shape.emptyCheck().desc)
                &&field(method,WORLD,"field_72995_K","Z")
                &&field(method,PLAYER,"field_71075_bZ","L"+CAPS+";")
                &&field(method,CAPS,"field_75098_d","Z")
                &&hasCallDescriptor(method,DROP_DESC)
                &&calls(method,ITEM,"func_150898_a","(Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;")
                &&typed(method,Opcodes.NEW,STACK)
                &&calls(method,WORLD,"func_147480_a","(IIIZ)Z");
    }

    private static boolean hasCallDescriptor(MethodNode method,String descriptor){
        if(method==null)return false;
        for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof MethodInsnNode call&&descriptor.equals(call.desc))return true;
        return false;
    }
}
