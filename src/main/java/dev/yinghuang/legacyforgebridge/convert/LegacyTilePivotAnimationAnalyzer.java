package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/**
 * SOURCE ONLY: a bounded operand-causal proof for a legacy ModelBase animation which resets
 * ModelRenderer.rotationPointY and conditionally adds a clamped sine offset to each proven part.
 * Both the TESR -> source model animator invocation and every pivot write receiver/value are
 * independently traced. It never executes a legacy tick, guesses packet fields, or registers
 * a modern BlockEntityRenderer. No mod or field names are production admission selectors.
 */
public final class LegacyTilePivotAnimationAnalyzer {
    private static final String TILE = "net/minecraft/tileentity/TileEntity";
    private static final String TESR = "net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String MODEL = "net/minecraft/client/model/ModelBase";
    private static final String PART = "net/minecraft/client/model/ModelRenderer";
    private static final String GL = "org/lwjgl/opengl/GL11";
    private static final String DRAW_DESC = "(L" + TILE + ";DDDF)V";
    private static final Set<String> PIVOT_Y = Set.of("rotationPointY", "field_78797_d");
    private static final int MAX_METHOD = 2048, MAX_CLOSURE = 12, MAX_DEPTH = 36, MAX_PARTS = 64;

    public record Part(String modelField, String pivotField, float restPivotY,
                       List<String> sourceTileFields, boolean usesPartialTick,
                       boolean sourceSineClampAndPriorPivot) {
        public Part {
            sourceTileFields = List.copyOf(sourceTileFields);
            if (modelField == null || modelField.isBlank() || !PIVOT_Y.contains(pivotField)
                    || !Float.isFinite(restPivotY) || Math.abs(restPivotY) > 4096
                    || sourceTileFields.isEmpty() || !usesPartialTick || !sourceSineClampAndPriorPivot)
                throw new IllegalArgumentException("Unbounded or unproven legacy animated part");
        }
    }
    public record Proof(String sourceTileClass, String sourceRendererClass, String sourceModelClass,
                        String modelAnimationMethod, String guardTileField,
                        List<Part> parts, boolean packetPayloadProven,
                        boolean clientAnimationRuntimeWired) {
        public Proof {
            parts = List.copyOf(parts);
            if (sourceTileClass == null || sourceRendererClass == null || sourceModelClass == null
                    || modelAnimationMethod == null || guardTileField == null || guardTileField.isBlank()
                    || parts.isEmpty() || parts.size() > MAX_PARTS
                    || packetPayloadProven || clientAnimationRuntimeWired)
                throw new IllegalArgumentException("Source-only pivot animation cannot grant client runtime");
        }
    }
    public record Analysis(Optional<Proof> proof, List<String> diagnostics) {
        public Analysis {
            proof = Objects.requireNonNull(proof);
            diagnostics = List.copyOf(diagnostics);
        }
    }
    private record Context(Frame<SourceValue>[] frames, Map<AbstractInsnNode,Integer> positions) {
        Frame<SourceValue> frame(AbstractInsnNode at) {
            Integer position = positions.get(at);
            return position == null ? null : frames[position];
        }
    }
    private record ValueProof(Set<String> tileFields, boolean partial, boolean sine,
                              boolean clamp, Set<String> priorPivotParts) {
        ValueProof combine(ValueProof rhs) {
            Set<String> fields = new TreeSet<>(tileFields); fields.addAll(rhs.tileFields);
            Set<String> prior = new TreeSet<>(priorPivotParts); prior.addAll(rhs.priorPivotParts);
            return new ValueProof(fields, partial || rhs.partial, sine || rhs.sine,
                    clamp || rhs.clamp, prior);
        }
    }
    private static final ValueProof EMPTY = new ValueProof(Set.of(),false,false,false,Set.of());
    private record Write(int index, String part, String pivot, Float reset,
                         ValueProof dynamic) { }
    private record Invocation(MethodNode method, MethodInsnNode call) { }

    public Analysis analyze(Path jar, LegacyBlockTileModelPreflight.Candidate candidate) throws IOException {
        Objects.requireNonNull(candidate);
        return analyze(jar, candidate.tileClass(), candidate.rendererClass(), candidate.modelClass());
    }
    /** Reusable identity-based fixture seam; names here are arguments, never hardcoded dispatch. */
    public Analysis analyze(Path jar, String tileName, String rendererName, String modelName) throws IOException {
        Objects.requireNonNull(jar);
        if (!validName(tileName) || !validName(rendererName) || !validName(modelName))
            return fail("Unsafe or missing source class identity");
        try (JarFile source = new JarFile(jar.toFile(),false)) {
            if (source.size() > 65_536) return fail("Unbounded legacy source archive");
            ClassNode tile = read(source,tileName), renderer = read(source,rendererName), model = read(source,modelName);
            if (tile == null || renderer == null || model == null || !descends(source,tile,TILE)
                    || !descends(source,renderer,TESR) || !descends(source,model,MODEL))
                return fail("Source TileEntity, TESR and ModelBase ancestry not all proven");
            if (!uniqueConstructedModelField(renderer,modelName))
                return fail("Source TESR model instance/field ownership not proven");
            Invocation invocation = findAnimationInvocation(renderer,tileName,modelName);
            if (invocation == null) return fail("Unique reachable model animation invocation not proven");
            MethodNode animator = unique(model,invocation.call.name,invocation.call.desc);
            if (animator == null || (animator.access & (Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE)) != 0
                    || !animator.tryCatchBlocks.isEmpty() || animator.instructions.size() > MAX_METHOD)
                return fail("Missing/unsafe source model animator body");
            if (!modelReceiverAndArguments(renderer,invocation,tileName,modelName))
                return fail("Actual TESR animation receiver, TileEntity argument or partial time unproven");
            Set<String> partFields = new TreeSet<>();
            for (FieldNode field : model.fields)
                if (field.desc.equals("L"+PART+";") && (field.access & Opcodes.ACC_STATIC)==0)
                    partFields.add(field.name);
            if (partFields.isEmpty() || partFields.size()>MAX_PARTS || !constructedParts(model,partFields))
                return fail("Source ModelRenderer part construction cannot be closed");
            Context cx = context(model.name,animator);
            if (cx == null) return fail("Model animation bytecode source dataflow cannot be analyzed");
            AbstractInsnNode[] code = animator.instructions.toArray();
            List<Write> writes = new ArrayList<>();
            List<JumpInsnNode> jumps = new ArrayList<>();
            for (int i=0;i<code.length;i++) {
                AbstractInsnNode op = code[i];
                if (op instanceof JumpInsnNode jump) jumps.add(jump);
                if (op instanceof TableSwitchInsnNode || op instanceof LookupSwitchInsnNode
                        || op instanceof InvokeDynamicInsnNode || op.getOpcode()==Opcodes.ATHROW
                        || op.getOpcode()==Opcodes.PUTSTATIC || op.getOpcode()==Opcodes.MONITORENTER
                        || op.getOpcode()==Opcodes.MONITOREXIT)
                    return fail("Unsupported animator control flow or side effects");
                if (op instanceof MethodInsnNode call && !allowedAnimationMath(call))
                    return fail("Unknown source method invocation inside model animator");
                if (op instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD
                        && !field.owner.equals(PART))
                    return fail("Source animator writes additional unproved model/tile state");
                if (op instanceof FieldInsnNode write && write.getOpcode()==Opcodes.PUTFIELD
                        && write.owner.equals(PART)) {
                    if (!PIVOT_Y.contains(write.name) || !write.desc.equals("F"))
                        return fail("Animator writes an unsupported ModelRenderer property");
                    Frame<SourceValue> frame = cx.frame(op);
                    if (frame == null || frame.getStackSize()<2) return fail("Unreachable/invalid pivot write");
                    String part = partReceiver(cx,frame.getStack(frame.getStackSize()-2),model,partFields,0,
                            Collections.newSetFromMap(new IdentityHashMap<>()));
                    if (part == null) return fail("Pivot write receiver does not resolve to one source-owned part");
                    Float reset=directFloat(frame.getStack(frame.getStackSize()-1));
                    ValueProof expr = reset == null ? value(cx,frame.getStack(frame.getStackSize()-1),
                            tileName,source,model,partFields,0,
                            Collections.newSetFromMap(new IdentityHashMap<>())) : null;
                    if (reset==null && (!exactPriorPlusZeroClampedSine(cx,
                            frame.getStack(frame.getStackSize()-1),part,model,partFields)
                            || expr==null || expr.tileFields.isEmpty() || !expr.partial
                            || !expr.sine || !expr.clamp || !expr.priorPivotParts.equals(Set.of(part))))
                        return fail("Animator pivot offset is not bounded and causally source-driven");
                    writes.add(new Write(i,part,write.name,reset,expr));
                }
            }
            if (writes.isEmpty() || writes.size()>MAX_PARTS*2 || jumps.size()!=1)
                return fail("Not one finite conditional per-part reset + dynamic offset family");
            long sineCalls=0, clampCalls=0;
            for (AbstractInsnNode op:animator.instructions)
                if (op instanceof MethodInsnNode call) {
                    if (isSine(call)) sineCalls++;
                    if (isMin(call)) clampCalls++;
                }
            if (sineCalls!=partFields.size() || clampCalls!=partFields.size())
                return fail("Exactly one source sine/min clamp pair per constructed part required");
            JumpInsnNode guard = jumps.getFirst();
            if (guard.getOpcode()!=Opcodes.IFNE && guard.getOpcode()!=Opcodes.IFEQ)
                return fail("Animation condition is not a source integer zero comparison");
            Frame<SourceValue> guardFrame = cx.frame(guard);
            if (guardFrame==null || guardFrame.getStackSize()<1)
                return fail("Missing source guarded tile state");
            String guardField = tileGuard(cx,guardFrame.getStack(guardFrame.getStackSize()-1),tileName,source,
                    0,Collections.newSetFromMap(new IdentityHashMap<>()));
            if (guardField == null) return fail("Animator branch is not a source tile integer field");
            int branchIndex = cx.positions.get(guard);
            Integer target = cx.positions.get(guard.label);
            if (target==null || target<=branchIndex || target>=code.length)
                return fail("Animator branch is not a bounded forward guard");
            int nonzeroStart = guard.getOpcode()==Opcodes.IFNE ? target : branchIndex+1;
            int zeroStart = guard.getOpcode()==Opcodes.IFNE ? branchIndex+1 : target;
            Map<String,Float> resets=new TreeMap<>();
            Map<String,Write> moves=new TreeMap<>();
            for (Write w : writes) {
                if (w.reset != null) {
                    if (w.index>=branchIndex || resets.putIfAbsent(w.part,w.reset)!=null)
                        return fail("One unconditional static reset per part required");
                } else {
                    if (w.index<=branchIndex || moves.putIfAbsent(w.part,w)!=null
                            || reachable(code,nonzeroStart,w.index) || !reachable(code,zeroStart,w.index))
                        return fail("Dynamic offset must run only when source tile guard is zero");
                }
            }
            if (!resets.keySet().equals(moves.keySet()) || !resets.keySet().equals(partFields))
                return fail("Every constructed ModelRenderer part needs one reset and one animated write");
            List<Part> parts = new ArrayList<>();
            for (String part : partFields) {
                Write w=moves.get(part);
                parts.add(new Part(part,w.pivot,resets.get(part),List.copyOf(w.dynamic.tileFields),true,true));
            }
            return new Analysis(Optional.of(new Proof(tileName,rendererName,modelName,animator.name,
                    guardField,parts,false,false)),List.of());
        }
    }

    /** One original ModelBox pivot read PLUS min(0,sin(source-expression)); no other terms. */
    private static boolean exactPriorPlusZeroClampedSine(Context ctx,SourceValue result,
            String part,ClassNode model,Set<String> allParts) {
        if(result==null || result.insns==null || result.insns.size()!=1)return false;
        AbstractInsnNode fadd=result.insns.iterator().next();
        if(fadd.getOpcode()!=Opcodes.FADD)return false;
        Frame<SourceValue> frame=ctx.frame(fadd);
        if(frame==null || frame.getStackSize()<2)return false;
        SourceValue a=frame.getStack(frame.getStackSize()-2),b=frame.getStack(frame.getStackSize()-1);
        return (isOldPivot(ctx,a,part,model,allParts) && isZeroClampOfSine(ctx,b))
                || (isOldPivot(ctx,b,part,model,allParts) && isZeroClampOfSine(ctx,a));
    }
    private static boolean isOldPivot(Context ctx,SourceValue value,String part,
                                     ClassNode model,Set<String> allParts) {
        if(value==null || value.insns==null || value.insns.size()!=1)return false;
        AbstractInsnNode op=value.insns.iterator().next();
        if(!(op instanceof FieldInsnNode field) || field.getOpcode()!=Opcodes.GETFIELD
                || !field.owner.equals(PART) || !field.desc.equals("F") || !PIVOT_Y.contains(field.name))
            return false;
        Frame<SourceValue> frame=ctx.frame(op);
        if(frame==null || frame.getStackSize()<1)return false;
        return part.equals(partReceiver(ctx,frame.getStack(frame.getStackSize()-1),
                model,allParts,0,Collections.newSetFromMap(new IdentityHashMap<>())));
    }
    private static boolean isZeroClampOfSine(Context ctx,SourceValue value) {
        if(value==null || value.insns==null || value.insns.size()!=1)return false;
        AbstractInsnNode op=value.insns.iterator().next();
        if(!(op instanceof MethodInsnNode call) || !isMin(call))return false;
        Frame<SourceValue> frame=ctx.frame(op);
        if(frame==null || frame.getStackSize()<2)return false;
        SourceValue a=frame.getStack(frame.getStackSize()-2),b=frame.getStack(frame.getStackSize()-1);
        return (Float.valueOf(0f).equals(directFloat(a)) && isDirectSine(b))
                || (Float.valueOf(0f).equals(directFloat(b)) && isDirectSine(a));
    }
    private static boolean isDirectSine(SourceValue value) {
        if(value==null || value.insns==null || value.insns.size()!=1)return false;
        AbstractInsnNode op=value.insns.iterator().next();
        return op instanceof MethodInsnNode call && isSine(call);
    }
    private static boolean isSine(MethodInsnNode call) {
        return call.getOpcode()==Opcodes.INVOKESTATIC
                && call.owner.equals("net/minecraft/util/MathHelper")
                && (call.name.equals("sin") || call.name.equals("func_76126_a"))
                && call.desc.equals("(F)F");
    }
    private static boolean isMin(MethodInsnNode call) {
        return call.getOpcode()==Opcodes.INVOKESTATIC && call.owner.equals("java/lang/Math")
                && call.name.equals("min") && call.desc.equals("(FF)F");
    }
    private static boolean allowedAnimationMath(MethodInsnNode call) {
        return isSine(call) || isMin(call);
    }
    private static boolean uniqueConstructedModelField(ClassNode renderer, String model) {
        MethodNode ctor=unique(renderer,"<init>","()V");
        if (ctor==null || !ctor.tryCatchBlocks.isEmpty()) return false;
        int fieldCount=0, assign=0, allocations=0;
        for (FieldNode f : renderer.fields) if (f.desc.equals("L"+model+";")) {
            if ((f.access & Opcodes.ACC_STATIC)!=0) return false;
            fieldCount++;
        }
        Context ctx=context(renderer.name,ctor);
        if(ctx==null)return false;
        for (AbstractInsnNode op : ctor.instructions) {
            if (op instanceof TypeInsnNode type && type.getOpcode()==Opcodes.NEW && type.desc.equals(model)) allocations++;
            if (op instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD
                    && field.owner.equals(renderer.name) && field.desc.equals("L"+model+";")) {
                if(!assignedNew(ctx,ctor,field,model))return false;
                assign++;
            }
        }
        return fieldCount==1 && assign==1 && allocations==1;
    }
    private static boolean constructedParts(ClassNode model, Set<String> parts) {
        MethodNode ctor=unique(model,"<init>","()V");
        if (ctor==null || !ctor.tryCatchBlocks.isEmpty()) return false;
        Map<String,Integer> count = new HashMap<>();int allocations=0;
        Context ctx=context(model.name,ctor);
        if(ctx==null)return false;
        for (AbstractInsnNode op : ctor.instructions) {
            if (op instanceof TypeInsnNode type && type.getOpcode()==Opcodes.NEW && type.desc.equals(PART)) allocations++;
            if (op instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD
                    && field.owner.equals(model.name) && field.desc.equals("L"+PART+";")) {
                if(!parts.contains(field.name) || !assignedNew(ctx,ctor,field,PART))return false;
                count.merge(field.name,1,Integer::sum);
            }
        }
        return allocations==parts.size() && count.keySet().equals(parts)
                && count.values().stream().allMatch(n->n==1);
    }
    private static boolean assignedNew(Context ctx,MethodNode ctor,FieldInsnNode field,String type) {
        Frame<SourceValue> frame=ctx.frame(field);
        if(frame==null || frame.getStackSize()<2
                || !isThis(frame.getStack(frame.getStackSize()-2)))return false;
        TypeInsnNode allocation=newAllocation(ctx,frame.getStack(frame.getStackSize()-1),0,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        if(allocation==null || !type.equals(allocation.desc))return false;
        Integer assignmentIndex=ctx.positions.get(field);
        int matchingInitializers=0;
        for(AbstractInsnNode op:ctor.instructions) {
            Integer i=ctx.positions.get(op);
            if(i==null || i>=assignmentIndex)break;
            if(!(op instanceof MethodInsnNode call) || call.getOpcode()!=Opcodes.INVOKESPECIAL
                    || !call.owner.equals(type) || !call.name.equals("<init>"))continue;
            Frame<SourceValue> at=ctx.frame(op);
            if(at==null)continue;
            int position=at.getStackSize()-Type.getArgumentTypes(call.desc).length-1;
            if(position<0)continue;
            if(newAllocation(ctx,at.getStack(position),0,
                    Collections.newSetFromMap(new IdentityHashMap<>()))==allocation)matchingInitializers++;
        }
        return matchingInitializers==1;
    }
    private static TypeInsnNode newAllocation(Context ctx,SourceValue val,int depth,
                                               Set<AbstractInsnNode> path) {
        if(val==null || val.insns==null || val.insns.size()!=1 || depth>MAX_DEPTH)return null;
        AbstractInsnNode op=val.insns.iterator().next();
        if(!path.add(op))return null;
        TypeInsnNode result=null;
        if(op instanceof TypeInsnNode t && t.getOpcode()==Opcodes.NEW)result=t;
        else if(op.getOpcode()==Opcodes.DUP || op.getOpcode()==Opcodes.CHECKCAST){
            Frame<SourceValue> frame=ctx.frame(op);
            if(frame!=null && frame.getStackSize()>0)
                result=newAllocation(ctx,frame.getStack(frame.getStackSize()-1),depth+1,path);
        }else if(op instanceof VarInsnNode v && v.getOpcode()==Opcodes.ALOAD) {
            Frame<SourceValue> frame=ctx.frame(op);
            if(frame!=null && v.var<frame.getLocals())
                result=newAllocation(ctx,frame.getLocal(v.var),depth+1,path);
        }
        path.remove(op);return result;
    }
    private static Invocation findAnimationInvocation(ClassNode renderer,String tile,String model) {
        MethodNode root=unique(renderer,"renderTileEntityAt",DRAW_DESC);
        if (root == null || (root.access & (Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)
            return null;
        Deque<MethodNode> queue=new ArrayDeque<>();queue.add(root);
        Set<MethodNode> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        Invocation found=null;
        while(!queue.isEmpty()) {
            MethodNode at=queue.removeFirst();if(!visited.add(at) || visited.size()>MAX_CLOSURE
                    || !at.tryCatchBlocks.isEmpty() || at.instructions.size()>MAX_METHOD) return null;
            for (AbstractInsnNode op : at.instructions) {
                if (!(op instanceof MethodInsnNode call)) continue;
                if (call.owner.equals(model) && call.desc.equals("(L"+tile+";F)V")
                        && call.getOpcode()==Opcodes.INVOKEVIRTUAL) {
                    if (found != null) return null;
                    found=new Invocation(at,call);
                }
                if (call.owner.equals(renderer.name) && !call.name.equals("<init>")) {
                    if (call.getOpcode()!=Opcodes.INVOKESPECIAL && call.getOpcode()!=Opcodes.INVOKEVIRTUAL)
                        return null;
                    MethodNode helper=unique(renderer,call.name,call.desc);
                    if (helper==null || visited.contains(helper)) return null;
                    queue.addLast(helper);
                }
            }
        }
        return found;
    }
    private static boolean modelReceiverAndArguments(ClassNode renderer,Invocation inv,String tile,String model) {
        Context ctx=context(renderer.name,inv.method);
        if (ctx==null) return false;
        Frame<SourceValue> frame=ctx.frame(inv.call);
        if (frame==null || frame.getStackSize()<3) return false;
        SourceValue receiver=frame.getStack(frame.getStackSize()-3);
        SourceValue arg=frame.getStack(frame.getStackSize()-2);
        SourceValue partial=frame.getStack(frame.getStackSize()-1);
        if (receiver.insns==null || receiver.insns.size()!=1 || arg.insns==null || arg.insns.size()!=1
                || partial.insns==null || partial.insns.size()!=1) return false;
        AbstractInsnNode object=receiver.insns.iterator().next();
        if (!(object instanceof FieldInsnNode field) || field.getOpcode()!=Opcodes.GETFIELD
                || !field.owner.equals(renderer.name) || !field.desc.equals("L"+model+";")) return false;
        if (renderer.fields.stream().filter(f->f.name.equals(field.name)&&f.desc.equals(field.desc)
                && (f.access & Opcodes.ACC_STATIC)==0).count()!=1) return false;
        AbstractInsnNode tileArg=arg.insns.iterator().next();
        if (tileArg instanceof TypeInsnNode cast && cast.getOpcode()==Opcodes.CHECKCAST
                && cast.desc.equals(tile)) {
            Frame<SourceValue> before=ctx.frame(cast);
            tileArg=before==null || before.getStackSize()<1 || before.getStack(before.getStackSize()-1).insns.size()!=1
                    ? null : before.getStack(before.getStackSize()-1).insns.iterator().next();
        }
        if (!(tileArg instanceof VarInsnNode v) || v.getOpcode()!=Opcodes.ALOAD || v.var!=1) return false;
        AbstractInsnNode tick=partial.insns.iterator().next();
        return tick instanceof VarInsnNode f && f.getOpcode()==Opcodes.FLOAD && f.var==8;
    }

    private static String partReceiver(Context ctx,SourceValue source,ClassNode model,Set<String> parts,
                                       int depth,Set<AbstractInsnNode> path) {
        if (source==null || source.insns==null || source.insns.size()!=1 || depth>MAX_DEPTH)return null;
        AbstractInsnNode op=source.insns.iterator().next();
        if (!path.add(op)) return null;
        String result=null;
        if (op instanceof FieldInsnNode f && f.getOpcode()==Opcodes.GETFIELD
                && f.owner.equals(model.name) && f.desc.equals("L"+PART+";") && parts.contains(f.name)) {
            Frame<SourceValue> before=ctx.frame(f);
            if (before!=null && before.getStackSize()>=1
                    && isThis(before.getStack(before.getStackSize()-1))) result=f.name;
        } else if (op.getOpcode()==Opcodes.DUP || op.getOpcode()==Opcodes.CHECKCAST) {
            Frame<SourceValue> before=ctx.frame(op);
            if (before!=null && before.getStackSize()>0)
                result=partReceiver(ctx,before.getStack(before.getStackSize()-1),model,parts,depth+1,path);
        } else if (op instanceof VarInsnNode v && v.getOpcode()==Opcodes.ALOAD) {
            Frame<SourceValue> before=ctx.frame(op);
            if (before!=null && v.var<before.getLocals())
                result=partReceiver(ctx,before.getLocal(v.var),model,parts,depth+1,path);
        }
        path.remove(op);return result;
    }
    private static boolean isThis(SourceValue source) {
        return source!=null && source.insns!=null && source.insns.size()==1
                && source.insns.iterator().next() instanceof VarInsnNode v
                && v.getOpcode()==Opcodes.ALOAD && v.var==0;
    }
    private static Float directFloat(SourceValue val) {
        if(val==null || val.insns==null || val.insns.size()!=1)return null;
        AbstractInsnNode op=val.insns.iterator().next();
        if(op.getOpcode()==Opcodes.FCONST_0)return 0f;
        if(op.getOpcode()==Opcodes.FCONST_1)return 1f;
        if(op.getOpcode()==Opcodes.FCONST_2)return 2f;
        if(op instanceof LdcInsnNode ldc && ldc.cst instanceof Float f && Float.isFinite(f))return f;
        return null;
    }
    private static ValueProof value(Context ctx,SourceValue val,String tile,JarFile jar,ClassNode model,
                                    Set<String> parts,int depth,Set<AbstractInsnNode> path) throws IOException {
        if(val==null || val.insns==null || val.insns.size()!=1 || depth>MAX_DEPTH)return null;
        AbstractInsnNode op=val.insns.iterator().next();
        if(!path.add(op))return null;
        Frame<SourceValue> frame=ctx.frame(op);
        ValueProof result=null;
        int opcode=op.getOpcode();
        if(directFloat(val)!=null || opcode==Opcodes.ICONST_0 || opcode==Opcodes.ICONST_1
                || opcode==Opcodes.ICONST_2 || opcode==Opcodes.ICONST_M1
                || op instanceof LdcInsnNode ldc && ldc.cst instanceof Integer) result=EMPTY;
        else if(op instanceof VarInsnNode v && (opcode==Opcodes.FLOAD || opcode==Opcodes.ILOAD)) {
            if(v.var==2 && opcode==Opcodes.FLOAD) result=new ValueProof(Set.of(),true,false,false,Set.of());
            else if(frame!=null && v.var<frame.getLocals())
                result=value(ctx,frame.getLocal(v.var),tile,jar,model,parts,depth+1,path);
        } else if(op instanceof VarInsnNode store && (opcode==Opcodes.FSTORE || opcode==Opcodes.ISTORE)) {
            if(frame!=null && frame.getStackSize()>0)
                result=value(ctx,frame.getStack(frame.getStackSize()-1),tile,jar,model,parts,depth+1,path);
        } else if(opcode==Opcodes.I2F || opcode==Opcodes.FNEG) {
            if(frame!=null && frame.getStackSize()>0)
                result=value(ctx,frame.getStack(frame.getStackSize()-1),tile,jar,model,parts,depth+1,path);
        } else if (opcode==Opcodes.FADD || opcode==Opcodes.FSUB || opcode==Opcodes.FMUL
                || opcode==Opcodes.FDIV || opcode==Opcodes.IADD || opcode==Opcodes.ISUB) {
            if(frame!=null && frame.getStackSize()>=2) {
                ValueProof a=value(ctx,frame.getStack(frame.getStackSize()-2),tile,jar,model,parts,depth+1,path);
                ValueProof b=value(ctx,frame.getStack(frame.getStackSize()-1),tile,jar,model,parts,depth+1,path);
                if(a!=null&&b!=null) result=a.combine(b);
            }
        } else if(op instanceof FieldInsnNode field && opcode==Opcodes.GETFIELD) {
            if(field.owner.equals(PART) && PIVOT_Y.contains(field.name) && field.desc.equals("F")
                    && frame!=null && frame.getStackSize()>0) {
                String p=partReceiver(ctx,frame.getStack(frame.getStackSize()-1),model,parts,0,
                        Collections.newSetFromMap(new IdentityHashMap<>()));
                if(p!=null)result=new ValueProof(Set.of(),false,false,false,Set.of(p));
            } else if((field.desc.equals("I") || field.desc.equals("F"))
                    && sourceTileField(jar,tile,field) && frame!=null && frame.getStackSize()>0
                    && tileParameter(frame.getStack(frame.getStackSize()-1)))
                result=new ValueProof(Set.of(field.name),false,false,false,Set.of());
        } else if(op instanceof MethodInsnNode call && opcode==Opcodes.INVOKESTATIC && frame!=null) {
            boolean sine=call.owner.equals("net/minecraft/util/MathHelper")
                    && (call.name.equals("sin") || call.name.equals("func_76126_a"))
                    && call.desc.equals("(F)F");
            boolean clamp=call.owner.equals("java/lang/Math") && call.name.equals("min")
                    && call.desc.equals("(FF)F");
            if(sine && frame.getStackSize()>=1) {
                ValueProof p=value(ctx,frame.getStack(frame.getStackSize()-1),tile,jar,model,parts,depth+1,path);
                if(p!=null && !p.tileFields.isEmpty() && p.partial)
                    result=new ValueProof(p.tileFields,p.partial,true,p.clamp,p.priorPivotParts);
            } else if(clamp && frame.getStackSize()>=2) {
                ValueProof a=value(ctx,frame.getStack(frame.getStackSize()-2),tile,jar,model,parts,depth+1,path);
                ValueProof b=value(ctx,frame.getStack(frame.getStackSize()-1),tile,jar,model,parts,depth+1,path);
                boolean leftZero=directFloat(frame.getStack(frame.getStackSize()-2))!=null
                        && directFloat(frame.getStack(frame.getStackSize()-2))==0f;
                boolean rightZero=directFloat(frame.getStack(frame.getStackSize()-1))!=null
                        && directFloat(frame.getStack(frame.getStackSize()-1))==0f;
                if(a!=null && b!=null && ((leftZero && b.sine)||(rightZero && a.sine))) {
                    ValueProof p=a.combine(b);
                    result=new ValueProof(p.tileFields,p.partial,p.sine,true,p.priorPivotParts);
                }
            }
        }
        path.remove(op);return result;
    }
    private static String tileGuard(Context ctx,SourceValue val,String tile,JarFile jar,
                                    int depth,Set<AbstractInsnNode> path)throws IOException {
        if(val==null || val.insns==null || val.insns.size()!=1 || depth>MAX_DEPTH)return null;
        AbstractInsnNode op=val.insns.iterator().next();if(!path.add(op))return null;
        String result=null;
        if(op instanceof FieldInsnNode f && f.getOpcode()==Opcodes.GETFIELD && f.desc.equals("I")
                && sourceTileField(jar,tile,f)) {
            Frame<SourceValue> frame=ctx.frame(f);
            if(frame!=null && frame.getStackSize()>0 && tileParameter(frame.getStack(frame.getStackSize()-1)))
                result=f.name;
        }
        path.remove(op);return result;
    }
    private static boolean tileParameter(SourceValue val) {
        return val!=null&&val.insns!=null&&val.insns.size()==1
                && val.insns.iterator().next() instanceof VarInsnNode v && v.getOpcode()==Opcodes.ALOAD
                && v.var==1;
    }
    private static boolean sourceTileField(JarFile jar,String tile,FieldInsnNode f)throws IOException {
        if(!descends(jar,read(jar,tile),f.owner))return false;
        ClassNode owner=read(jar,f.owner);
        return owner!=null && owner.fields.stream().anyMatch(x->x.name.equals(f.name)
                && x.desc.equals(f.desc) && (x.access&Opcodes.ACC_STATIC)==0);
    }
    private static boolean reachable(AbstractInsnNode[] code,int from,int destination) {
        Deque<Integer> queue=new ArrayDeque<>();boolean[] seen=new boolean[code.length];queue.add(from);
        while(!queue.isEmpty()) {
            int i=queue.removeFirst();if(i<0||i>=code.length||seen[i])continue;
            if(i==destination)return true;seen[i]=true;
            AbstractInsnNode op=code[i];
            if(op.getOpcode()==Opcodes.RETURN || op.getOpcode()==Opcodes.IRETURN
                    || op.getOpcode()==Opcodes.ARETURN || op.getOpcode()==Opcodes.ATHROW)continue;
            if(op instanceof JumpInsnNode || op instanceof TableSwitchInsnNode
                    || op instanceof LookupSwitchInsnNode)return false;
            queue.add(i+1);
        }
        return false;
    }
    private static Context context(String owner,MethodNode method) {
        try {
            Frame<SourceValue>[] frames=new Analyzer<>(new SourceInterpreter()).analyze(owner,method);
            Map<AbstractInsnNode,Integer> positions=new IdentityHashMap<>();
            for(int i=0;i<method.instructions.size();i++)positions.put(method.instructions.get(i),i);
            return new Context(frames,positions);
        } catch(AnalyzerException | RuntimeException failed) {return null;}
    }
    private static MethodNode unique(ClassNode node,String name,String desc) {
        if(node==null)return null;
        MethodNode found=null;
        for(MethodNode m:node.methods)if(m.name.equals(name)&&m.desc.equals(desc)){
            if(found!=null)return null;found=m;
        }
        return found;
    }
    private static boolean validName(String name) {
        return name!=null && !name.isBlank() && !name.contains("..") && !name.startsWith("/")
                && !name.contains("\\") && name.length()<256;
    }
    private static ClassNode read(JarFile jar,String name)throws IOException {
        if(!validName(name))return null;
        var entry=jar.getJarEntry(name+".class");if(entry==null || entry.getSize()>1_000_000)return null;
        try(InputStream input=jar.getInputStream(entry)) {
            ClassNode result=new ClassNode(Opcodes.ASM9);
            try{new ClassReader(input).accept(result,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}
            catch(RuntimeException malformed){return null;}
            return result.name.equals(name)?result:null;
        }
    }
    private static boolean descends(JarFile jar,ClassNode node,String target)throws IOException {
        if(node==null)return false;
        Set<String> seen=new HashSet<>();String at=node.name;
        for(int i=0;i<24 && at!=null && seen.add(at);i++) {
            if(at.equals(target))return true;
            ClassNode owner=read(jar,at);at=owner==null?null:owner.superName;
        }
        return false;
    }
    private static Analysis fail(String reason){return new Analysis(Optional.empty(),List.of(reason));}
}
