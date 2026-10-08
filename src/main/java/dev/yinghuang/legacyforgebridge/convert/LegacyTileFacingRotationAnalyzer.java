package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/**
 * Source-only, fail-closed proof of a 1.7.10 TESR's fixed per-metadata face rotations.
 *
 * <p>This does NOT infer block placement, tile data synchronization, dynamically read yaw,
 * animation updates, source GL translations, or modern rendering. It recognizes one bounded
 * source-owned TESR render method (or its direct, typed helper), where two GL11.glRotatef
 * calls read floats set by constant assignments and integer comparisons of the TileEntity's
 * getBlockMetadata() value. Both fixed-axis calls must be unconditional and in order.
 * Untested instructions, operand ambiguity and unexpected side effects reject the proof.</p>
 *
 * <p>The conversion is structural: no legacy mod name, registry ID or class spelling is used
 * as a production whitelist.</p>
 */
public final class LegacyTileFacingRotationAnalyzer {
    private static final String TILE = "net/minecraft/tileentity/TileEntity";
    private static final String GL = "org/lwjgl/opengl/GL11";
    private static final String TESR = "net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String RENDER_DESC = "(L" + TILE + ";DDDF)V";
    private static final String ROTATE_DESC = "(FFFF)V";
    private static final int MAX_CLASSES = 65536;
    private static final int MAX_METHOD_INSNS = 2048;
    private static final int MAX_STEPS = 512;

    public record Facing(int metadata, float rotationXDegrees, float rotationZDegrees) {
        public Facing {
            if (metadata < 0 || metadata > 15 || !Float.isFinite(rotationXDegrees)
                    || !Float.isFinite(rotationZDegrees) || Math.abs(rotationXDegrees) > 360
                    || Math.abs(rotationZDegrees) > 360)
                throw new IllegalArgumentException("Unbounded source-facing rotation");
        }
    }
    public record Proof(String sourceTileClass, String sourceRendererClass, String drawMethod,
                        String drawDescriptor, int sourceMetadataMask, List<Facing> facing0to15,
                        boolean additionalSourceGlRotationPresent, boolean runtimeWired) {
        public Proof {
            facing0to15 = List.copyOf(facing0to15);
            if (runtimeWired || facing0to15.size() != 16)
                throw new IllegalArgumentException("Source-only facing map is not runnable");
            for (int i=0;i<16;i++) if (facing0to15.get(i).metadata() != i)
                throw new IllegalArgumentException("Missing source metadata state " + i);
        }
    }
    public record Analysis(Optional<Proof> proof, List<String> diagnostics) {
        public Analysis { proof=Objects.requireNonNull(proof); diagnostics=List.copyOf(diagnostics); }
    }
    private record RotationSite(int codeIndex, int angleLocal) { }
    private record MetadataSite(int nextIndex, int metadataLocal, int mask) { }
    private record SourceMethod(MethodNode method, String descriptor) { }

    public Analysis analyze(Path jar, LegacyBlockTileModelPreflight.Candidate candidate) throws IOException {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(candidate, "candidate");
        return analyze(jar, candidate.tileClass(), candidate.rendererClass());
    }
    /** The production entry point accepts only verified rev284 candidate identities. */
    public Analysis analyze(Path jar, String sourceTileClass, String sourceRendererClass) throws IOException {
        Objects.requireNonNull(jar, "jar");
        if (!internalName(sourceTileClass) || !internalName(sourceRendererClass))
            return fail("Missing or unsafe source tile/renderer class identity");
        Map<String,ClassNode> classes = readClasses(jar, Set.of(sourceTileClass,sourceRendererClass));
        ClassNode tile=classes.get(sourceTileClass), renderer=classes.get(sourceRendererClass);
        if (tile == null || renderer == null || !sourceAncestry(jar, tile, TILE)
                || !sourceAncestry(jar, renderer, TESR))
            return fail("Source-owned TileEntity/render renderer identity not proven");
        MethodNode entry = findUnique(renderer, Set.of("renderTileEntityAt", "func_147500_a"), RENDER_DESC);
        if (entry == null || (entry.access & Opcodes.ACC_STATIC)!=0 || !entry.tryCatchBlocks.isEmpty())
            return fail("Unique nonstatic TESR entry is absent");

        List<SourceMethod> methods = new ArrayList<>();
        methods.add(new SourceMethod(entry, entry.desc));
        List<MethodInsnNode> delegated = new ArrayList<>();
        for (AbstractInsnNode insn : entry.instructions)
            if (insn instanceof MethodInsnNode call && call.owner.equals(sourceRendererClass)
                    && !call.name.equals("<init>")) delegated.add(call);
        if (delegated.size() == 1) {
            MethodInsnNode call=delegated.getFirst();
            if (!call.desc.equals("(L" + sourceTileClass + ";DDDF)V"))
                return fail("TESR delegated helper descriptor must match the proven source tile");
            if (!canonicalHelperCall(entry, call, sourceTileClass))
                return fail("TESR entry must delegate exactly one unchanged source tile and render position");
            MethodNode helper=findUnique(renderer, Set.of(call.name), call.desc);
            if (helper == null || helper == entry || !helper.tryCatchBlocks.isEmpty())
                return fail("TESR delegated draw helper is unresolved or ambiguous");
            methods.add(new SourceMethod(helper, helper.desc));
        } else if (delegated.size()>1) return fail("Multiple source renderer helper calls are ambiguous");

        List<Proof> matches=new ArrayList<>();
        for (SourceMethod source:methods) {
            Proof candidateProof=inspect(renderer.name, source.method(), sourceTileClass);
            if (candidateProof != null) matches.add(candidateProof);
        }
        if (matches.size()!=1) return fail("Exactly one reachable metadata-to-GL axis draw proof is required");
        return new Analysis(Optional.of(matches.getFirst()), List.of());
    }

    private static boolean canonicalHelperCall(MethodNode entry, MethodInsnNode call, String tile) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for(AbstractInsnNode insn:entry.instructions) if(insn.getOpcode()>=0)code.add(insn);
        if(code.size()!=9 || code.get(7)!=call || code.get(8).getOpcode()!=Opcodes.RETURN)
            return false;
        if(!(code.get(2) instanceof TypeInsnNode cast) || cast.getOpcode()!=Opcodes.CHECKCAST
                || !cast.desc.equals(tile)) return false;
        if(!(call.getOpcode()==Opcodes.INVOKESPECIAL || call.getOpcode()==Opcodes.INVOKEVIRTUAL))
            return false;
        return load(code.get(0),Opcodes.ALOAD,0) && load(code.get(1),Opcodes.ALOAD,1)
                && load(code.get(3),Opcodes.DLOAD,2) && load(code.get(4),Opcodes.DLOAD,4)
                && load(code.get(5),Opcodes.DLOAD,6) && load(code.get(6),Opcodes.FLOAD,8);
    }
    private static boolean load(AbstractInsnNode insn, int opcode, int local) {
        return insn instanceof VarInsnNode v && v.getOpcode()==opcode && v.var==local;
    }

    private static Proof inspect(String renderer, MethodNode method, String tile) {
        AbstractInsnNode[] insns = method.instructions.toArray();
        if (insns.length > MAX_METHOD_INSNS || method.tryCatchBlocks.size()>0) return null;
        Map<LabelNode,Integer> labels = new IdentityHashMap<>();
        for (int i=0;i<insns.length;i++) if (insns[i] instanceof LabelNode l) labels.put(l,i);
        List<Integer> rotations = new ArrayList<>();
        for (int i=0;i<insns.length;i++) if (insns[i] instanceof MethodInsnNode call
                && call.owner.equals(GL) && call.name.equals("glRotatef")
                && call.getOpcode()==Opcodes.INVOKESTATIC && ROTATE_DESC.equals(call.desc)) rotations.add(i);
        if (rotations.size()<2 || rotations.size()>4) return null;
        RotationSite xRotation=rotate(insns,rotations.get(0),1,0,0);
        RotationSite zRotation=rotate(insns,rotations.get(1),0,0,1);
        if (xRotation==null || zRotation==null || xRotation.angleLocal()==zRotation.angleLocal()) return null;
        // Between the two constant-axis calls there must be no hidden conditional, state
        // rewrite, transform or call. Only the four direct source operand pushes are allowed.
        List<AbstractInsnNode> between=new ArrayList<>();
        for (int i=rotations.get(0)+1;i<rotations.get(1);i++) if(insns[i].getOpcode()>=0)between.add(insns[i]);
        if (between.size()!=4 || !(between.getFirst() instanceof VarInsnNode var)
                || var.getOpcode()!=Opcodes.FLOAD || var.var!=zRotation.angleLocal()) return null;

        MetadataSite metadata=null;
        int metadataCalls=0;
        for (int i=0;i<rotations.get(0);i++) if (insns[i] instanceof MethodInsnNode call
                && (call.name.equals("getBlockMetadata") || call.name.equals("func_145832_p"))
                && call.desc.equals("()I")) {
            metadataCalls++;
            if (!call.owner.equals(tile) && !call.owner.equals(TILE)) return null;
            AbstractInsnNode receiver=previous(insns,i);
            if (!(receiver instanceof VarInsnNode aload) || aload.getOpcode()!=Opcodes.ALOAD
                    || aload.var!=1) return null;
            int next=nextIndex(insns,i);
            int mask=-1;
            if (next < insns.length && intValue(insns[next])!=null) {
                int bits=intValue(insns[next]);
                int andIndex=nextIndex(insns,next);
                if (bits==7 && andIndex<insns.length && insns[andIndex].getOpcode()==Opcodes.IAND) {
                    mask=7;next=nextIndex(insns,andIndex);
                }
            }
            if (next>=insns.length || !(insns[next] instanceof VarInsnNode store)
                    || store.getOpcode()!=Opcodes.ISTORE) return null;
            metadata=new MetadataSite(nextIndex(insns,next),store.var,mask);
        }
        if (metadataCalls != 1 || metadata==null || metadata.nextIndex()>=rotations.get(0))return null;
        if (xRotation.angleLocal()==metadata.metadataLocal()
                || zRotation.angleLocal()==metadata.metadataLocal()) return null;
        List<Facing> faces=new ArrayList<>(16);
        for(int facing=0;facing<16;facing++) {
            float[] angles=eval(insns, labels, metadata, rotations.get(0), xRotation.angleLocal(),
                    zRotation.angleLocal(), facing);
            if (angles==null) return null;
            faces.add(new Facing(facing,angles[0],angles[1]));
        }
        // A metadata lookup with no observable pose variation is not a six-facing map.
        boolean differing=false;
        for(int i=2;i<=6;i++) if(Float.compare(faces.get(i).rotationXDegrees(),faces.get(1).rotationXDegrees())!=0
                || Float.compare(faces.get(i).rotationZDegrees(),faces.get(1).rotationZDegrees())!=0) {
                differing=true; break;
            }
        if (!differing) return null;
        return new Proof(tile,renderer,method.name,method.desc,metadata.mask(),faces,
                rotations.size()>2,false);
    }

    private static float[] eval(AbstractInsnNode[] code, Map<LabelNode,Integer> labels,
                                 MetadataSite meta, int firstRotate, int localX, int localZ, int facing) {
        Float x=null,z=null;
        int steps=0, i=meta.nextIndex();
        while(i<firstRotate && steps++<MAX_STEPS) {
            AbstractInsnNode node=code[i];int op=node.getOpcode();
            if (node instanceof JumpInsnNode jump) {
                boolean taken;
                if(op==Opcodes.GOTO) taken=true;
                else if(op==Opcodes.IF_ICMPEQ || op==Opcodes.IF_ICMPNE) {
                    AbstractInsnNode right=previous(code,i), left=previous(code,index(code,right));
                    if (!(left instanceof VarInsnNode load) || load.getOpcode()!=Opcodes.ILOAD
                            || load.var!=meta.metadataLocal() || intValue(right)==null) return null;
                    taken=(facing & (meta.mask()<0?15:meta.mask()))==intValue(right);
                    if(op==Opcodes.IF_ICMPNE)taken=!taken;
                } else if(op==Opcodes.IFEQ || op==Opcodes.IFNE) {
                    AbstractInsnNode prior=previous(code,i);
                    if (!(prior instanceof VarInsnNode load) || load.getOpcode()!=Opcodes.ILOAD
                            || load.var!=meta.metadataLocal())return null;
                    taken=(facing & (meta.mask()<0?15:meta.mask()))==0;
                    if(op==Opcodes.IFNE)taken=!taken;
                } else return null;
                if(taken) {
                    Integer dest=labels.get(jump.label);
                    if(dest==null || dest<=i || dest>=firstRotate) return null;
                    i=dest;continue;
                }
            } else if(node instanceof TableSwitchInsnNode || node instanceof LookupSwitchInsnNode) {
                // Ambiguous source switch families require a distinct analyzer; don't guess.
                return null;
            } else if(node instanceof VarInsnNode var && op==Opcodes.FSTORE) {
                AbstractInsnNode immediate=previous(code,i);
                Float value=floatValue(immediate);
                if(value==null || !Float.isFinite(value)||Math.abs(value)>360) return null;
                if(var.var==localX)x=value;
                else if(var.var==localZ)z=value;
                else return null;
            } else if(op==Opcodes.ISTORE || op==Opcodes.ASTORE || op==Opcodes.DSTORE
                    || op==Opcodes.IINC || op==Opcodes.PUTFIELD || op==Opcodes.PUTSTATIC
                    || op==Opcodes.GETFIELD || op==Opcodes.GETSTATIC
                    || op==Opcodes.MONITORENTER || op==Opcodes.MONITOREXIT || op==Opcodes.ATHROW
                    || op==Opcodes.RETURN || op==Opcodes.ARETURN || op==Opcodes.IRETURN
                    || node instanceof InvokeDynamicInsnNode) return null;
            else if(node instanceof MethodInsnNode call) {
                if (!call.owner.equals(GL) || !call.name.equals("glTranslatef")
                        || !call.desc.equals("(FFF)V") || op!=Opcodes.INVOKESTATIC) return null;
            }
            i++;
        }
        return i==firstRotate && x!=null && z!=null ? new float[]{x,z} : null;
    }

    private static RotationSite rotate(AbstractInsnNode[] code, int callIndex,
                                       float axisX,float axisY,float axisZ) {
        AbstractInsnNode d=previous(code,callIndex), c=previous(code,index(code,d));
        AbstractInsnNode b=previous(code,index(code,c)), a=previous(code,index(code,b));
        if (d==null || c==null || b==null || a==null || !Objects.equals(floatValue(b),axisX)
                || !Objects.equals(floatValue(c),axisY) || !Objects.equals(floatValue(d),axisZ)
                || !(a instanceof VarInsnNode load) || load.getOpcode()!=Opcodes.FLOAD) return null;
        return new RotationSite(callIndex,load.var);
    }
    private static int index(AbstractInsnNode[] code, AbstractInsnNode node) {
        if (node==null)return 0;
        for(int i=0;i<code.length;i++) if(code[i]==node)return i;
        return 0;
    }
    private static AbstractInsnNode previous(AbstractInsnNode[] code, int i) {
        for(int j=i-1;j>=0;j--)if(code[j].getOpcode()>=0)return code[j];
        return null;
    }
    private static int nextIndex(AbstractInsnNode[] code, int i) {
        for(int j=i+1;j<code.length;j++)if(code[j].getOpcode()>=0)return j;
        return code.length;
    }
    private static Integer intValue(AbstractInsnNode n) {
        if(n==null)return null;
        return switch(n.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1;
            case Opcodes.ICONST_0 -> 0;
            case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2;
            case Opcodes.ICONST_3 -> 3;
            case Opcodes.ICONST_4 -> 4;
            case Opcodes.ICONST_5 -> 5;
            case Opcodes.BIPUSH,Opcodes.SIPUSH -> ((IntInsnNode)n).operand;
            case Opcodes.LDC -> n instanceof LdcInsnNode ldc && ldc.cst instanceof Integer v ? v : null;
            default -> null;
        };
    }
    private static Float floatValue(AbstractInsnNode n) {
        if(n==null)return null;
        return switch(n.getOpcode()) {
            case Opcodes.FCONST_0 -> 0F;
            case Opcodes.FCONST_1 -> 1F;
            case Opcodes.FCONST_2 -> 2F;
            case Opcodes.LDC -> n instanceof LdcInsnNode ldc && ldc.cst instanceof Float f ? f : null;
            default -> null;
        };
    }
    private static MethodNode findUnique(ClassNode owner, Set<String> names, String desc) {
        MethodNode found=null;
        for(MethodNode m:owner.methods) if(names.contains(m.name)&&m.desc.equals(desc)) {
            if (found!=null) return null;
            found=m;
        }
        return found;
    }
    private static boolean sourceAncestry(Path jar, ClassNode node, String expectedBase)
            throws IOException {
        // Follow the complete source hierarchy; do not treat an arbitrary class extending
        // "something other than Object" as a proved legacy TileEntity.
        Set<String> seen=new HashSet<>();
        String parent=node.superName;
        try(JarFile input=new JarFile(jar.toFile(),false)) {
            for(int depth=0;depth<24 && parent!=null && seen.add(parent);depth++) {
                if(parent.equals(expectedBase))return true;
                if(!internalName(parent))return false;
                var entry=input.getJarEntry(parent+".class");
                if(entry==null)return false;
                try(InputStream bytes=input.getInputStream(entry)) {
                    ClassNode base=new ClassNode(Opcodes.ASM9);
                    new ClassReader(bytes).accept(base,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    if(!base.name.equals(parent))return false;
                    parent=base.superName;
                } catch(RuntimeException malformed) { return false; }
            }
            return false;
        }
    }
    private static boolean internalName(String value) {
        return value!=null && value.matches("[A-Za-z0-9_$]+(/[A-Za-z0-9_$]+)+")
                && !value.contains("..") && !value.contains("//");
    }
    private static Map<String,ClassNode> readClasses(Path jar, Set<String> names) throws IOException {
        Map<String,ClassNode> result=new HashMap<>();
        try(JarFile input=new JarFile(jar.toFile(),false)) {
            if(input.size()>MAX_CLASSES) throw new IOException("Unbounded legacy source archive");
            for(String name:names) {
                var entry=input.getJarEntry(name+".class");
                if(entry==null) continue;
                try(InputStream bytes=input.getInputStream(entry)) {
                    ClassNode node=new ClassNode(Opcodes.ASM9);
                    new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    if(!node.name.equals(name))return Map.of();
                    result.put(name,node);
                } catch(RuntimeException damaged) {return Map.of();}
            }
        }
        return result;
    }
    private static Analysis fail(String reason) {return new Analysis(Optional.empty(),List.of(reason));}
}
