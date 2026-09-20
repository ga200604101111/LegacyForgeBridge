package dev.yinghuang.legacyforgebridge.convert;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Selects explicit geometric adapters; a texture alone is never evidence for a cube. */
public final class LegacyBlockGeometryAnalyzer {
    public record Rule(LegacyIconTableAnalyzer.GeometryInput input,String family,String proof,boolean dynamic) { }
    public record Analysis(List<Rule> rules,Map<String,String> excluded) {
        public Analysis {rules=List.copyOf(rules);excluded=Map.copyOf(excluded);}
    }
    private static final Set<String> GEOMETRY_OVERRIDE_NAMES=Set.of("getRenderType","func_149645_b");

    public Analysis analyze(Path source) throws IOException {
        var registry=new LegacyRegistryAnalyzer().analyze(source);
        var inputs=new LegacyIconTableAnalyzer().analyzeGeometryInputs(source,registry);
        Map<String,LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity> renderIds=new HashMap<>();
        for(var r:new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(source).rules())renderIds.put(r.registryName(),r.renderIdentity());
        Map<String,ClassNode> classes=new HashMap<>();
        try(var jar=new JarFile(source.toFile())){
            var entries=jar.entries();while(entries.hasMoreElements()){
                var entry=entries.nextElement();if(!entry.getName().endsWith(".class"))continue;
                ClassNode c=new ClassNode();try(var in=jar.getInputStream(entry)){new ClassReader(in.readAllBytes()).accept(c,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}classes.put(c.name,c);
            }
        }
        List<Rule> out=new ArrayList<>();Map<String,String> excluded=new TreeMap<>();
        for(var input:inputs) {
            String family=null,proof="";boolean dynamic=false;
            var render=renderIds.get(input.registryName());
            // The general gameplay render table intentionally has a smaller admission surface.
            // This presentation adapter recognizes these inherited platform families independently.
            if (!sourceOverrides(classes,input.sourceClass(),GEOMETRY_OVERRIDE_NAMES)) {
                Integer inherited=switch(input.platform()) {
                    case "net/minecraft/block/BlockStairs" -> 10;
                    case "net/minecraft/block/BlockPane" -> 18;
                    case "net/minecraft/block/BlockLeavesBase", "net/minecraft/block/BlockLog", "net/minecraft/block/BlockCarpet", "net/minecraft/block/BlockPressurePlate" -> 0;
                    default -> null;
                };
                if(inherited!=null)render=new LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity(inherited,null,null);
            }
            if(input.variants().isEmpty()){excluded.put(input.registryName(),input.limitation());continue;}
            boolean stairs=input.platform().equals("net/minecraft/block/BlockStairs");
            boolean mimic=render!=null && render.constant()==null && !input.neighbourFaces().isEmpty()
                    && Objects.equals(input.originalRenderType(),stairs?10:0)
                    && boundRendererCalls(classes,render,"getOriginalRenderType");
            if(mimic && (stairs||Set.of("net/minecraft/block/Block","net/minecraft/block/BlockPressurePlate").contains(input.platform()))) {
                family=stairs?"mimic_stairs":"mimic_box";dynamic=true;
                proof="Source original render type, bound delegate renderer and tainted-coordinate per-face neighbour projection";
            }else if(stairs && render!=null && render.isConstant(10)) {
                family="stairs";dynamic=true;proof="Inherited 1.7.10 BlockStairs render type 10 and source-owned icon selection";
            }else if(input.platform().equals("net/minecraft/block/BlockPane") && render!=null
                    && !sourceOverrides(classes,input.sourceClass(),Set.of("canPaneConnectToBlock","func_150098_a"))
                    && (render.isConstant(18)||boundRendererCalls(classes,render,"func_150098_a")||boundRendererCalls(classes,render,"canPaneConnectToBlock"))) {
                family="pane";dynamic=true;
                proof="Inherited BlockPane connection predicate plus uniquely bound pane renderer; thin-panel native adaptation";
            }else if(render!=null && render.isConstant(0) && Set.of("net/minecraft/block/Block","net/minecraft/block/BlockLeavesBase","net/minecraft/block/BlockLog","net/minecraft/block/BlockCarpet","net/minecraft/block/BlockPressurePlate").contains(input.platform())
                    && input.neighbourFaces().isEmpty() && input.materialFallbacks().isEmpty()) {
                family="box";proof="Source metadata bounds, inventory bounds and six face icons under vanilla render type 0";
            }
            if(family==null){excluded.put(input.registryName(),"No admitted geometric renderer family; "+input.limitation());continue;}
            if(stairs && sourceOverrides(classes,input.sourceClass(),Set.of("func_150145_f","func_150144_g","func_150147_e","func_150146_f"))) {
                excluded.put(input.registryName(),"Source changes the inherited stair geometric algorithm");continue;
            }
            out.add(new Rule(input,family,proof,dynamic));
        }
        return new Analysis(out,excluded);
    }
    private static boolean sourceOverrides(Map<String,ClassNode> classes,String type,Set<String> names){
        Set<String> visited=new HashSet<>();while(classes.containsKey(type)&&visited.add(type)){ClassNode c=classes.get(type);for(MethodNode m:c.methods)if(names.contains(m.name))return true;type=c.superName;}return false;
    }
    /** Follows only the unique source renderer value paired with the exact symbolic render ID. */
    private static boolean boundRendererCalls(Map<String,ClassNode> classes,LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity id,String callName){
        if(id==null||id.constant()!=null)return false;Set<String> renderers=new HashSet<>();
        for(ClassNode c:classes.values())for(MethodNode m:c.methods){
            for(AbstractInsnNode n:m.instructions)if(n instanceof FieldInsnNode field&&n.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals(id.fieldOwner())&&field.name.equals(id.fieldName())){
                String candidate=null;boolean put=false;int budget=18;
                for(AbstractInsnNode next=n.getNext();next!=null&&budget-->0;next=next.getNext()){
                    if(next instanceof TypeInsnNode t&&next.getOpcode()==Opcodes.NEW)candidate=t.desc;
                    if(next instanceof FieldInsnNode f&&next.getOpcode()==Opcodes.GETSTATIC&&f.desc.startsWith("L"))candidate=f.desc.substring(1,f.desc.length()-1);
                    if(next instanceof MethodInsnNode call&&call.name.equals("put")&&call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")){put=true;break;}
                    if(next instanceof JumpInsnNode||next.getOpcode()==Opcodes.RETURN)break;
                }
                if(put&&candidate!=null&&classes.containsKey(candidate))renderers.add(candidate);
            }
        }
        // Some render IDs deliberately share a world/inventory renderer. Distinct renderer
        // classes would need an explicit context proof, so they are not merged here.
        if(renderers.size()!=1)return false;
        return reaches(classes,renderers.iterator().next(),callName,new HashSet<>());
    }
    private static boolean reaches(Map<String,ClassNode> classes,String type,String wanted,Set<String> visited){
        if(!visited.add(type)||visited.size()>64)return false;ClassNode c=classes.get(type);if(c==null)return false;
        for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode call){
            if(call.name.equals(wanted))return true;
            if(classes.containsKey(call.owner)&&reaches(classes,call.owner,wanted,visited))return true;
        }
        return false;
    }
}
