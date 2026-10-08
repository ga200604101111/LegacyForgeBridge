package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Bytecode-causal, SOURCE-ONLY evidence for a legacy TESR's third rotation:
 * {@code GL11.glRotatef((float) tile.sourceField, 0, 1, 0)} after a separately
 * source-proven pair of fixed X/Z facing rotations.
 *
 * <p>Unlike a generic GETFIELD/glRotatef census, the exact GL angle operand is
 * traced through a bounded, contiguous stack expression. The field must be
 * source-declared on the supplied TileEntity ancestry; the render callback's
 * TileEntity argument must be the receiver. Every entry-to-return control-flow
 * path must execute the source Y rotation. Other transforms or arithmetic between
 * the fixed rotations and the Y rotation are rejected rather than guessed.</p>
 *
 * <p>Critically, identifying this source field does NOT prove that its server
 * value reaches a 1.21.11 client, or authorize running the legacy TileEntity
 * tick logic on the client. No mod/class-name-specific dispatch is used.</p>
 */
public final class LegacyTileDynamicYawAnalyzer {
    private static final String GL="org/lwjgl/opengl/GL11";
    private static final String TILE="net/minecraft/tileentity/TileEntity";
    private static final String TESR="net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String ROTATE="(FFFF)V";
    private static final int MAX_METHOD=2048, MAX_SUPER=24, MAX_ARCHIVE=65_536;

    public enum Operand { INT_FIELD_TO_FLOAT, FLOAT_FIELD }
    public record Proof(String sourceTileClass, String sourceRendererClass, String drawMethod,
                        String drawDescriptor, String sourceFieldOwner, String sourceFieldName,
                        String sourceFieldDescriptor, Operand operand, boolean unconditionalSourceRotation,
                        boolean tileFieldSyncProven, boolean runtimeWired) {
        public Proof {
            if (!safeName(sourceTileClass) || !safeName(sourceRendererClass) || drawMethod==null
                    || drawMethod.isBlank() || drawDescriptor==null || drawDescriptor.isBlank()
                    || !safeName(sourceFieldOwner) || sourceFieldName==null || sourceFieldName.isBlank()
                    || operand==null || !unconditionalSourceRotation
                    || !((operand==Operand.INT_FIELD_TO_FLOAT && "I".equals(sourceFieldDescriptor))
                    || (operand==Operand.FLOAT_FIELD && "F".equals(sourceFieldDescriptor)))
                    || tileFieldSyncProven || runtimeWired)
                throw new IllegalArgumentException("Dynamic legacy yaw evidence must remain source-only");
        }
    }
    public record Analysis(Optional<Proof> proof, List<String> diagnostics) {
        public Analysis { proof=Objects.requireNonNull(proof); diagnostics=List.copyOf(diagnostics); }
    }

    public Analysis analyze(Path sourceJar, LegacyBlockTileModelPreflight.Candidate candidate,
                            LegacyTileFacingRotationAnalyzer.Proof facing) throws IOException {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(facing, "facing");
        if (!candidate.tileClass().equals(facing.sourceTileClass())
                || !candidate.rendererClass().equals(facing.sourceRendererClass())
                || facing.runtimeWired() || !facing.additionalSourceGlRotationPresent())
            return failed("Source-facing/candidate identities or additional rotation do not match");
        return analyze(sourceJar, candidate.tileClass(), candidate.rendererClass(), facing);
    }

    /** Re-validates the face proof against the same JAR: a forged/stale sidecar grants nothing. */
    public Analysis analyze(Path sourceJar, String sourceTileClass, String sourceRendererClass,
                            LegacyTileFacingRotationAnalyzer.Proof facing) throws IOException {
        Objects.requireNonNull(sourceJar, "sourceJar");
        if (!safeName(sourceTileClass) || !safeName(sourceRendererClass) || facing==null
                || !sourceTileClass.equals(facing.sourceTileClass())
                || !sourceRendererClass.equals(facing.sourceRendererClass())
                || facing.runtimeWired() || !facing.additionalSourceGlRotationPresent())
            return failed("Invalid or mismatched metadata-facing source proof");
        var validated = new LegacyTileFacingRotationAnalyzer()
                .analyze(sourceJar,sourceTileClass,sourceRendererClass).proof();
        if (validated.isEmpty() || !validated.get().equals(facing))
            return failed("Static metadata facing source proof is stale or was not verified");

        try (JarFile jar=new JarFile(sourceJar.toFile(),false)) {
            if (jar.size()>MAX_ARCHIVE) return failed("Unbounded legacy source archive");
            ClassNode renderer=read(jar,sourceRendererClass);
            ClassNode tile=read(jar,sourceTileClass);
            if (tile==null || renderer==null || !descends(jar,tile,TILE)
                    || !descends(jar,renderer,TESR))
                return failed("Source TileEntity/TESR ancestry unavailable");
            MethodNode draw=null;
            for (MethodNode method:renderer.methods)
                if (method.name.equals(facing.drawMethod()) && method.desc.equals(facing.drawDescriptor())) {
                    if (draw!=null) return failed("Duplicate source TESR draw method");
                    draw=method;
                }
            if (draw==null || (draw.access & (Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0
                    || !draw.tryCatchBlocks.isEmpty() || draw.instructions.size()>MAX_METHOD)
                return failed("Unbounded or invalid source TESR draw method");
            String expectedHelper="(L"+sourceTileClass+";DDDF)V";
            String entry="(L"+TILE+";DDDF)V";
            if (!draw.desc.equals(expectedHelper) && !draw.desc.equals(entry))
                return failed("Source draw method does not take the expected TileEntity parameter");
            AbstractInsnNode[] code=draw.instructions.toArray();
            List<Integer> rotations=new ArrayList<>();
            Map<LabelNode,Integer> labels=new IdentityHashMap<>();
            for (int i=0;i<code.length;i++) {
                AbstractInsnNode instruction=code[i];
                if (instruction instanceof LabelNode label) labels.put(label,i);
                if (instruction instanceof MethodInsnNode call && call.owner.equals(GL)
                        && call.name.equals("glRotatef")) {
                    if (call.getOpcode()!=Opcodes.INVOKESTATIC || !call.desc.equals(ROTATE))
                        return failed("Unexpected source GL rotation signature");
                    rotations.add(i);
                }
            }
            if (rotations.size()!=3) return failed("Exactly two fixed rotations and one dynamic rotation required");
            int begin=rotations.get(1), end=rotations.get(2);
            if (begin>=end) return failed("Dynamic yaw must occur after source facing angles");
            List<AbstractInsnNode> expression=new ArrayList<>();
            for (int i=begin+1;i<=end;i++) if (code[i].getOpcode()>=0) expression.add(code[i]);
            SourceField field=sourceOperand(jar,sourceTileClass,expression);
            if (field==null) return failed("The exact GL Y angle is not one source-owned TileEntity field");
            int first=-1;
            for (int i=begin+1;i<=end;i++) if(code[i].getOpcode()>=0){first=i;break;}
            if (first<0 || !noJumpIntoExpression(code,labels,first,end)
                    || !dominatesEveryExit(code,labels,first)
                    || !dominatesEveryExit(code,labels,end))
                return failed("Source Y rotation operand/call is not unconditional on every render path");
            return new Analysis(Optional.of(new Proof(sourceTileClass,sourceRendererClass,draw.name,draw.desc,
                    field.owner(),field.name(),field.descriptor(),field.operand(),true,false,false)),List.of());
        }
    }
    private record SourceField(String owner, String name, String descriptor, Operand operand) { }

    private static SourceField sourceOperand(JarFile jar, String tile,
                                            List<AbstractInsnNode> code) throws IOException {
        if (code.size()<6 || code.size()>10) return null;
        int i=0;
        if (!(code.get(i) instanceof VarInsnNode receiver) || receiver.getOpcode()!=Opcodes.ALOAD
                || receiver.var!=1) return null;
        i++;
        // For a direct vanilla TileEntity entry, a source class cast is necessary.
        if (i<code.size() && code.get(i) instanceof TypeInsnNode cast
                && cast.getOpcode()==Opcodes.CHECKCAST) {
            if (!cast.desc.equals(tile)) return null;
            i++;
        }
        if (i>=code.size() || !(code.get(i) instanceof FieldInsnNode read)
                || read.getOpcode()!=Opcodes.GETFIELD
                || (!read.desc.equals("I") && !read.desc.equals("F"))) return null;
        if (!declares(jar,tile,read.owner,read.name,read.desc)) return null;
        i++;
        Operand operand=read.desc.equals("I")?Operand.INT_FIELD_TO_FLOAT:Operand.FLOAT_FIELD;
        if (operand==Operand.INT_FIELD_TO_FLOAT) {
            if (i>=code.size() || code.get(i).getOpcode()!=Opcodes.I2F) return null;
            i++;
        }
        // A bounded source-local alias of the produced float is harmless, if immediately
        // stored then read without any intervening operations.
        if (i<code.size() && code.get(i) instanceof VarInsnNode store
                && store.getOpcode()==Opcodes.FSTORE) {
            int index=store.var;
            if (i+1>=code.size() || !(code.get(i+1) instanceof VarInsnNode load)
                    || load.getOpcode()!=Opcodes.FLOAD || load.var!=index) return null;
            i+=2;
        }
        if (code.size()-i!=4 || !fixedFloat(code.get(i),0F)
                || !fixedFloat(code.get(i+1),1F) || !fixedFloat(code.get(i+2),0F)) return null;
        AbstractInsnNode last=code.get(i+3);
        if (!(last instanceof MethodInsnNode call) || call.getOpcode()!=Opcodes.INVOKESTATIC
                || !call.owner.equals(GL) || !call.name.equals("glRotatef") || !call.desc.equals(ROTATE))
            return null;
        return new SourceField(read.owner,read.name,read.desc,operand);
    }

    private static boolean fixedFloat(AbstractInsnNode node,float value) {
        Float actual=switch (node.getOpcode()) {
            case Opcodes.FCONST_0 -> 0F;
            case Opcodes.FCONST_1 -> 1F;
            case Opcodes.FCONST_2 -> 2F;
            case Opcodes.LDC -> node instanceof LdcInsnNode ldc && ldc.cst instanceof Float f?f:null;
            default -> null;
        };
        return actual!=null && Float.compare(actual,value)==0;
    }
    private static boolean declares(JarFile jar,String tile,String owner,String name,String desc)throws IOException {
        if (!safeName(owner)) return false;
        // The receiver must be a source-owned TileEntity or its proven SOURCE superclass.
        Set<String> seen=new HashSet<>();
        String current=tile;
        for(int depth=0;current!=null && depth<MAX_SUPER && seen.add(current);depth++) {
            if (current.equals(TILE)) return false;
            ClassNode node=read(jar,current);
            if (node==null) return false;
            if (current.equals(owner))
                return node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc)
                        && (f.access & Opcodes.ACC_STATIC)==0);
            current=node.superName;
        }
        return false;
    }
    private static boolean descends(JarFile jar,ClassNode node,String base)throws IOException {
        Set<String> seen=new HashSet<>();
        String current=node.superName;
        for(int depth=0;current!=null && depth<MAX_SUPER && seen.add(current);depth++) {
            if(current.equals(base))return true;
            if(!safeName(current))return false;
            ClassNode next=read(jar,current);
            if(next==null)return false;
            current=next.superName;
        }
        return false;
    }
    private static ClassNode read(JarFile jar,String expected)throws IOException {
        if(!safeName(expected))return null;
        JarEntry entry=jar.getJarEntry(expected+".class");
        if(entry==null || entry.getSize()>4_000_000)return null;
        try(InputStream data=jar.getInputStream(entry)) {
            var node=new ClassNode(Opcodes.ASM9);
            new ClassReader(data).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            return expected.equals(node.name)?node:null;
        } catch(RuntimeException malformed) {return null;}
    }
    private static boolean safeName(String name) {
        return name!=null && name.matches("[A-Za-z0-9_$]+(/[A-Za-z0-9_$]+)+")
                && !name.contains("..") && !name.contains("//");
    }

    /** No branch may skip the proven TileEntity field read and enter halfway into its stack expression. */
    private static boolean noJumpIntoExpression(AbstractInsnNode[] code,
                                                 Map<LabelNode,Integer> labels,int first,int last) {
        for(AbstractInsnNode node:code) if(node instanceof JumpInsnNode jump) {
            Integer target=labels.get(jump.label);
            if(target==null)return false;
            if(target>first && target<=last)return false;
        }
        return true;
    }

    /** Reject any path that can return/throw without the exact third rotation executing. */
    private static boolean dominatesEveryExit(AbstractInsnNode[] code,
                                             Map<LabelNode,Integer> labels,int yawCall) {
        if(code.length>MAX_METHOD || yawCall<0 || yawCall>=code.length)return false;
        boolean[] reached=new boolean[code.length];
        ArrayDeque<Integer> pending=new ArrayDeque<>();pending.add(0);
        boolean sawCall=false;
        while(!pending.isEmpty()) {
            int index=pending.removeFirst();
            if(index<0 || index>=code.length)return false;
            if(index==yawCall){sawCall=true;continue;}
            if(reached[index])continue;
            reached[index]=true;
            AbstractInsnNode node=code[index];int op=node.getOpcode();
            if(op==Opcodes.RETURN || op==Opcodes.ARETURN || op==Opcodes.IRETURN
                    || op==Opcodes.LRETURN || op==Opcodes.FRETURN || op==Opcodes.DRETURN
                    || op==Opcodes.ATHROW || op==Opcodes.JSR || op==Opcodes.RET)
                return false;
            if(node instanceof TableSwitchInsnNode || node instanceof LookupSwitchInsnNode
                    || node instanceof InvokeDynamicInsnNode) return false;
            if(node instanceof JumpInsnNode jump) {
                Integer target=labels.get(jump.label);
                if(target==null || target<=index)return false; // no guessed loops
                pending.add(target);
                if(op!=Opcodes.GOTO) pending.add(index+1);
            } else pending.add(index+1);
        }
        return sawCall;
    }
    private static Analysis failed(String explanation) {
        return new Analysis(Optional.empty(),List.of(explanation));
    }
}
