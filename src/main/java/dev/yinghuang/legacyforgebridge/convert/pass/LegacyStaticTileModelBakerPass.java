package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileModelPreflight;
import dev.yinghuang.legacyforgebridge.convert.LegacyTileFacingRotationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Bounded source-owned static ModelRenderer -> modern vanilla block JSON preview.
 * Animations and dynamic yaw are deliberately NOT replayed: the resulting file is a
 * single static source frame, never proof of TileEntity packet synchronization.
 * Emits only if the original block/TESR/model, PNG dimensions, ModelBox constructor,
 * render closure, GL transforms, and all 16 metadata pose angles agree.
 * No mod-name, specific class, or numeric registry ID is used for dispatch.
 */
public final class LegacyStaticTileModelBakerPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/static-tesr-visual-preview.json";
    private static final String PART="net/minecraft/client/model/ModelRenderer";
    private static final String BASE="net/minecraft/client/model/ModelBase";
    private static final String GL="org/lwjgl/opengl/GL11";
    private static final String RESOURCE="net/minecraft/util/ResourceLocation";
    private static final int MAX_SOURCE_PARTS=32;

    private static final class PartData {
        String field;
        int u,v;
        double x,y,z;
        int w,h,d;
        double px,py,pz;
        boolean hasBox;
        PartData(int u,int v){this.u=u;this.v=v;}
    }
    private static final class PartObject { PartData data; }
    private enum ThisObject { INSTANCE }
    private record SourceModel(int w,int h,List<PartData> drawn) { }
    private record Texture(String namespace,String path,byte[] png,int width,int height) { }
    private record Baked(String blockId,int parts,int poses) { }

    @Override public String id(){return "legacy-static-tesr-model-source-baker";}
    @Override public void apply(ConversionContext context) throws Exception {
        var source = new LegacyBlockTileModelPreflight().analyze(context.sourceJar());
        if(source.candidates().isEmpty())return;
        var angles = new LegacyTileFacingRotationAnalyzer();
        List<Baked> completed = new ArrayList<>();
        Map<String,String> rejected = new LinkedHashMap<>();
        try(JarFile jar=new JarFile(context.sourceJar().toFile(),false)){
            for(var candidate:source.candidates()){
                try {
                    var proof=angles.analyze(context.sourceJar(),candidate).proof()
                            .orElseThrow(()->new IllegalArgumentException("16-state static facing source proof absent"));
                    completed.add(materialize(context.stagingDir(),context.metadata().fabricId(),jar,candidate,proof));
                }catch(IllegalArgumentException | IOException invalid){
                    rejected.put(candidate.registryName(), invalid.getMessage()==null ? invalid.getClass().getSimpleName() : invalid.getMessage());
                }
            }
        }
        if(completed.isEmpty()&&rejected.isEmpty())return;
        StringBuilder out=new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"sourceOnly\": true,\n  \"runtimeAnimationWired\": false,\n  \"tileStateSyncProven\": false,\n  \"sourceSha256\": ");
        quote(out,context.sourceHash()).append(",\n  \"partialStaticModelCount\": ").append(completed.size())
                .append(",\n  \"candidates\": [");
        for(int i=0;i<completed.size();i++){
            var candidate=completed.get(i);
            if(i>0)out.append(',');
            out.append("{\"id\":");quote(out,candidate.blockId());
            out.append(",\"sourceDrawnPartCount\":").append(candidate.parts());
            out.append(",\"sourcePoseCount\":").append(candidate.poses());
            out.append(",\"runtimeReady\":false}");
        }
        out.append("],\n  \"skipped\": [");int i=0;
        for(var entry:rejected.entrySet()){
            if(i++>0)out.append(',');out.append("{\"registryName\":");quote(out,entry.getKey());
            out.append(",\"reason\":");quote(out,entry.getValue());out.append('}');
        }
        out.append("]\n}\n");
        Path path=context.stagingDir().resolve(OUTPUT);Files.createDirectories(path.getParent());
        Files.writeString(path,out.toString(),StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-REV291-TESR-0001",SupportLevel.AUTO,
                "Provisional generic static source ModelRenderer frames="+completed.size()
                        +"; no client TileEntity animation, gameplay, or packet synchronization enabled.");
    }

    private static Baked materialize(Path root,String namespace,JarFile jar,
            LegacyBlockTileModelPreflight.Candidate candidate,
            LegacyTileFacingRotationAnalyzer.Proof proof) throws IOException {
        String registry=candidate.registryName();
        String id=modernPath(registry),ns=namespace.toLowerCase(Locale.ROOT);
        if(!ns.matches("[a-z0-9_.-]+")||!id.matches("[a-z0-9/._-]+")||id.contains(".."))
            throw new IllegalArgumentException("unsafe generated model identity");
        if(proof.facing0to15().size()!=16)throw new IllegalArgumentException("metadata pose range incomplete");
        ClassNode model=sourceClass(jar,candidate.modelClass());
        ClassNode renderer=sourceClass(jar,candidate.rendererClass());
        proveRenderer(renderer,model.name);
        SourceModel geometry=proveModel(model);
        Texture texture=sourceTexture(jar,renderer);
        if(!texture.namespace().equals(ns)||geometry.w()!=texture.width()||geometry.h()!=texture.height())
            throw new IllegalArgumentException("source model/render texture atlas does not match");
        String block="assets/"+ns+"/models/block/";
        Path specialized=root.resolve(block+id+".json");
        if(Files.exists(specialized)){
            String source=Files.readString(specialized,StandardCharsets.UTF_8).replaceAll("\\s+", "");
            // Replace ONLY the exact cube_all fallback created by GenericContentPass.
            // A real existing model, extra texture slots, elements or source-owned JSON is never touched.
            String prefix="{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"";
            if(!source.startsWith(prefix)||!source.endsWith("\"}}")
                    ||!source.substring(prefix.length(),source.length()-3).matches("[a-zA-Z0-9_./:-]+"))
                throw new IllegalArgumentException("specialized source block model must not be overwritten");
        }
        // Do not create conflicting assets until the entire candidate is valid.
        Map<String,byte[]> poses=new LinkedHashMap<>();
        Map<Integer,String> variants=new TreeMap<>();
        String texHash=hexSha(texture.png());
        String texturePath="assets/"+ns+"/textures/block/lfb_static_source/"+texHash+".png";
        String textureRef=ns+":block/lfb_static_source/"+texHash;
        for(var state:proof.facing0to15()){
            int meta=state.metadata();double rx=state.rotationXDegrees(),rz=state.rotationZDegrees();
            if(meta<0||meta>15||variants.containsKey(meta)||!isQuarter(rx)||!isQuarter(rz))
                throw new IllegalArgumentException("ambiguous/non-right-angle legacy metadata pose");
            String key=fmtAngle(rx)+"_"+fmtAngle(rz);
            String name=id+"_lfb_source_static_"+key;
            poses.putIfAbsent(name,bake(geometry,textureRef,rx,rz).getBytes(StandardCharsets.UTF_8));
            variants.put(meta,ns+":block/"+name);
        }
        if(variants.size()!=16)throw new IllegalArgumentException("missing legacy metadata pose");
        StringBuilder states=new StringBuilder("{\n  \"variants\": {\n");
        for(int meta=0;meta<16;meta++){
            if(meta>0)states.append(",\n");
            states.append("    \"legacy_meta=").append(meta).append("\": {\"model\":");
            quote(states,variants.get(meta)).append('}');
        }
        states.append("\n  }\n}\n");
        // Atomic with respect to source analysis: all model and UV validation happened first.
        for(var entry:poses.entrySet()){
            Path target=root.resolve(block+entry.getKey()+".json");
            if(Files.exists(target))throw new IllegalArgumentException("source baked model path already exists");
        }
        Path state=root.resolve("assets/"+ns+"/blockstates/"+id+".json");
        if(Files.exists(state)){
            String source=Files.readString(state,StandardCharsets.UTF_8).replaceAll("\\s+", "");
            String fallback="{\"variants\":{\"\":{\"model\":\""+ns+":block/"+id+"\"}}}";
            if(!source.equals(fallback))
                throw new IllegalArgumentException("specialized legacy metadata variants must not be overwritten");
        }
        Path png=root.resolve(texturePath);
        if(Files.exists(png)&&!Arrays.equals(Files.readAllBytes(png),texture.png()))
            throw new IllegalArgumentException("texture hash collision");
        Files.createDirectories(png.getParent());
        if(!Files.exists(png))Files.write(png,texture.png());
        for(var entry:poses.entrySet()){
            Path target=root.resolve(block+entry.getKey()+".json");
            Files.createDirectories(target.getParent());Files.write(target,entry.getValue());
        }
        Files.createDirectories(state.getParent());Files.writeString(state,states.toString(),StandardCharsets.UTF_8);
        // Keep the source block-item parent resolving to a real model, not the old cube_all shell.
        String defaultPose=variants.get(0);
        Files.writeString(specialized,"{\n  \"parent\": \""+defaultPose+"\"\n}\n",StandardCharsets.UTF_8);
        return new Baked(ns+":"+id,geometry.drawn().size(),poses.size());
    }
    private static boolean isQuarter(double v){return Double.isFinite(v)&&Math.abs(v/90-Math.rint(v/90))<1e-6&&Math.abs(v)<=360;}
    private static String fmtAngle(double angle){long x=Math.round(angle);return x<0?"m"+(-x):"p"+x;}
    private static String modernPath(String raw){
        if(raw==null||raw.isBlank())throw new IllegalArgumentException("empty registry name");
        String path=raw.trim().toLowerCase(Locale.ROOT).replace('\\','/').replaceAll("[^a-z0-9/._-]","_").replaceAll("_+","_");
        while(path.startsWith("/"))path=path.substring(1);
        if(path.isBlank())throw new IllegalArgumentException("empty generated block id");return path;
    }
    private static ClassNode sourceClass(JarFile jar,String owner) throws IOException {
        if(owner==null||!owner.matches("[a-zA-Z0-9_$/]+")||owner.contains(".."))
            throw new IllegalArgumentException("unsafe or missing source class");
        JarEntry entry=jar.getJarEntry(owner+".class");
        if(entry==null)throw new IllegalArgumentException("source class unavailable");
        try(InputStream input=jar.getInputStream(entry)){
            ClassNode node=new ClassNode(Opcodes.ASM9);
            new ClassReader(input).accept(node,ClassReader.SKIP_FRAMES|ClassReader.SKIP_DEBUG);
            if(!owner.equals(node.name))throw new IllegalArgumentException("source class identity mismatch");
            return node;
        }catch(RuntimeException malformed){throw new IllegalArgumentException("invalid source class",malformed);}
    }
    private static MethodNode uniqueMethod(ClassNode clazz,String name,String desc){
        MethodNode m=null;for(MethodNode method:clazz.methods){if(method.name.equals(name)&&method.desc.equals(desc)){
            if(m!=null)throw new IllegalArgumentException("duplicate source method "+name);m=method;
        }}if(m==null||m.tryCatchBlocks.size()!=0)throw new IllegalArgumentException("source method missing/has exception handler "+name);return m;
    }
    private static List<AbstractInsnNode> real(MethodNode m){
        List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode node:m.instructions)
            if(node.getOpcode()>=0)out.add(node);return out;
    }
    private static Number number(AbstractInsnNode insn){
        if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Number x)return x;
        if(insn instanceof IntInsnNode n&&(n.getOpcode()==Opcodes.BIPUSH||n.getOpcode()==Opcodes.SIPUSH))return n.operand;
        return switch(insn.getOpcode()){
            case Opcodes.ICONST_M1->-1;
            case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;
            case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;
            case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;
            default->null;
        };
    }
    private static Object pop(List<Object> stack){if(stack.isEmpty())throw new IllegalArgumentException("unbalanced bytecode stack");return stack.remove(stack.size()-1);}
    private static double finite(Object item){if(!(item instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("dynamic/unbounded source number");return n.doubleValue();}
    private static int integer(Object item){double x=finite(item);if(x!=Math.rint(x)||Math.abs(x)>2048)throw new IllegalArgumentException("unbounded source integer");return (int)x;}
    private static SourceModel proveModel(ClassNode model){
        if(!BASE.equals(model.superName))throw new IllegalArgumentException("not direct static ModelBase family");
        MethodNode ctor=uniqueMethod(model,"<init>","()V");
        List<Object> stack=new ArrayList<>();Map<String,PartData> fields=new LinkedHashMap<>();
        int width=64,height=32,returns=0;
        for(AbstractInsnNode insn:real(ctor)){
            int op=insn.getOpcode();Number value=number(insn);
            if(value!=null){stack.add(value);continue;}
            if(op==Opcodes.ALOAD&&insn instanceof VarInsnNode v&&v.var==0){stack.add(ThisObject.INSTANCE);continue;}
            if(op==Opcodes.NEW&&insn instanceof TypeInsnNode n&&n.desc.equals(PART)){stack.add(new PartObject());continue;}
            if(op==Opcodes.DUP){if(stack.isEmpty())throw new IllegalArgumentException("unbalanced DUP");stack.add(stack.get(stack.size()-1));continue;}
            if(op==Opcodes.GETFIELD&&insn instanceof FieldInsnNode f&&f.desc.equals("L"+PART+";")){
                if(pop(stack)!=ThisObject.INSTANCE||!fields.containsKey(f.name))throw new IllegalArgumentException("unproved part receiver");
                PartObject obj=new PartObject();obj.data=fields.get(f.name);stack.add(obj);continue;
            }
            if(op==Opcodes.PUTFIELD&&insn instanceof FieldInsnNode f){
                Object data=pop(stack),owner=pop(stack);
                if(owner!=ThisObject.INSTANCE)throw new IllegalArgumentException("unproved model constructor side effect");
                if(f.desc.equals("L"+PART+";")){
                    if(!(data instanceof PartObject obj)||obj.data==null||fields.containsKey(f.name))
                        throw new IllegalArgumentException("unproved model part allocation");
                    obj.data.field=f.name;fields.put(f.name,obj.data);
                }else if(f.desc.equals("I")&&(f.name.equals("field_78090_t")||f.name.equals("textureWidth"))){width=integer(data);
                }else if(f.desc.equals("I")&&(f.name.equals("field_78089_u")||f.name.equals("textureHeight"))){height=integer(data);
                }else throw new IllegalArgumentException("unsupported model constructor assignment");
                continue;
            }
            if((op==Opcodes.INVOKESPECIAL||op==Opcodes.INVOKEVIRTUAL)&&insn instanceof MethodInsnNode call){
                if(call.owner.equals(BASE)&&call.name.equals("<init>")&&call.desc.equals("()V")){
                    if(pop(stack)!=ThisObject.INSTANCE)throw new IllegalArgumentException("unproved ModelBase constructor");continue;
                }
                if(call.owner.equals(PART)&&call.name.equals("<init>")&&call.desc.equals("(L"+BASE+";II)V")){
                    int v=integer(pop(stack)),u=integer(pop(stack));Object parent=pop(stack),object=pop(stack);
                    if(parent!=ThisObject.INSTANCE||!(object instanceof PartObject p)||p.data!=null)throw new IllegalArgumentException("unproved part initialization");
                    p.data=new PartData(u,v);continue;
                }
                if(call.owner.equals(PART)&&(call.name.equals("func_78789_a")||call.name.equals("addBox"))
                        &&call.desc.equals("(FFFIII)L"+PART+";")){
                    int d=integer(pop(stack)),h=integer(pop(stack)),w=integer(pop(stack));
                    double z=finite(pop(stack)),y=finite(pop(stack)),x=finite(pop(stack));
                    Object obj=pop(stack);if(!(obj instanceof PartObject p)||p.data==null||p.data.hasBox)
                        throw new IllegalArgumentException("unproved source ModelBox binding");
                    p.data.x=x;p.data.y=y;p.data.z=z;p.data.w=w;p.data.h=h;p.data.d=d;p.data.hasBox=true;stack.add(p);continue;
                }
                if(call.owner.equals(PART)&&(call.name.equals("func_78790_a")||call.name.equals("addBox"))&&call.desc.equals("(FFFIIIF)V")){
                    double expand=finite(pop(stack));int d=integer(pop(stack)),h=integer(pop(stack)),w=integer(pop(stack));
                    double z=finite(pop(stack)),y=finite(pop(stack)),x=finite(pop(stack));Object obj=pop(stack);
                    if(expand!=0||!(obj instanceof PartObject p)||p.data==null||p.data.hasBox)
                        throw new IllegalArgumentException("unsupported expanded source ModelBox");
                    p.data.x=x;p.data.y=y;p.data.z=z;p.data.w=w;p.data.h=h;p.data.d=d;p.data.hasBox=true;continue;
                }
                if(call.owner.equals(PART)&&(call.name.equals("func_78793_a")||call.name.equals("setRotationPoint"))&&call.desc.equals("(FFF)V")){
                    double z=finite(pop(stack)),y=finite(pop(stack)),x=finite(pop(stack));Object obj=pop(stack);
                    if(!(obj instanceof PartObject p)||p.data==null)throw new IllegalArgumentException("unproved part pivot");
                    p.data.px=x;p.data.py=y;p.data.pz=z;continue;
                }
                throw new IllegalArgumentException("unclassified ModelRenderer constructor call");
            }
            if(op==Opcodes.POP){pop(stack);continue;}
            if(op==Opcodes.RETURN){returns++;if(!stack.isEmpty())throw new IllegalArgumentException("constructor stack not closed");continue;}
            throw new IllegalArgumentException("unclassified model constructor bytecode "+op);
        }
        if(returns!=1||width<1||height<1||width>2048||height>2048||fields.size()>MAX_SOURCE_PARTS)
            throw new IllegalArgumentException("unbounded source model constructor");
        List<PartData> rendered=new ArrayList<>();List<AbstractInsnNode> draw=real(uniqueMethod(model,"render","(F)V"));
        if(draw.size()<5||draw.size()%4!=1||draw.getLast().getOpcode()!=Opcodes.RETURN)
            throw new IllegalArgumentException("source part render method not fixed");
        Set<String> visited=new HashSet<>();
        for(int i=0;i<draw.size()-1;i+=4){
            if(!(draw.get(i) instanceof VarInsnNode first)||first.getOpcode()!=Opcodes.ALOAD||first.var!=0
                    ||!(draw.get(i+1) instanceof FieldInsnNode f)||f.getOpcode()!=Opcodes.GETFIELD
                    ||!f.desc.equals("L"+PART+";")||!(draw.get(i+2) instanceof VarInsnNode scale)
                    ||scale.getOpcode()!=Opcodes.FLOAD||scale.var!=1
                    ||!(draw.get(i+3) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEVIRTUAL
                    ||!call.owner.equals(PART)||!Set.of("func_78785_a","render").contains(call.name)
                    ||!call.desc.equals("(F)V")||!visited.add(f.name))
                throw new IllegalArgumentException("unproven ModelRenderer render call chain");
            PartData box=fields.get(f.name);
            if(box==null||!box.hasBox||box.w<1||box.h<1||box.d<1||box.w>128||box.h>128||box.d>128
                    ||box.u<0||box.v<0||box.u+2*(box.d+box.w)>width||box.v+box.d+box.h>height)
                throw new IllegalArgumentException("source model cuboid atlas/shape unproved");
            rendered.add(box);
        }
        return new SourceModel(width,height,List.copyOf(rendered));
    }
    private static void proveRenderer(ClassNode renderer,String model){
        int modelDraws=0,rotations=0,scales=0,translates=0,pushes=0,pops=0;
        for(MethodNode m:renderer.methods){
            var code=real(m);
            for(int i=0;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call){
                if(call.owner.equals(model)&&call.name.equals("render")&&call.desc.equals("(F)V")){
                    modelDraws++;Number v=i>0?number(code.get(i-1)):null;
                    if(v==null||Math.abs(v.doubleValue()-0.0625)>1e-7)throw new IllegalArgumentException("model scale not fixed at 1/16");
                }
                if(!call.owner.equals(GL))continue;
                switch(call.name){
                    case "glRotatef" -> rotations++;
                    case "glTranslatef" -> translates++;
                    case "glPushMatrix" -> pushes++;
                    case "glPopMatrix" -> pops++;
                    case "glScalef" -> {
                        scales++;
                        if(i<3)throw new IllegalArgumentException("scale operands missing");
                        double[] source={1,-1,-1};
                        for(int k=0;k<3;k++){
                            Number v=number(code.get(i-3+k));
                            if(v==null||v.doubleValue()!=source[k])throw new IllegalArgumentException("unknown source model scale");
                        }
                    }
                    case "glDisable","glEnable","glBlendFunc","glColorMask","glDepthMask","glAlphaFunc" ->
                        throw new IllegalArgumentException("multi-pass legacy GL renderer requires its own adapter");
                    case "glColor4f","glColor3f" -> { /* reset or set a source color: not treated as emissive */ }
                    default -> throw new IllegalArgumentException("unclassified GL draw state "+call.name);
                }
            }
        }
        if(modelDraws!=1||rotations!=3||scales!=1||translates!=1||pushes<1||pushes!=pops)
            throw new IllegalArgumentException("source TESR draw transform/matrix closure not bounded");
    }
    private static Texture sourceTexture(JarFile jar,ClassNode renderer) throws IOException {
        Set<String> ids=new LinkedHashSet<>();
        for(MethodNode method:renderer.methods){
            if(!method.name.equals("<clinit>"))continue;
            for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc && ldc.cst instanceof String value
                    &&value.contains(":textures/model/")&&value.endsWith(".png"))ids.add(value);
        }
        if(ids.size()!=1)throw new IllegalArgumentException("renderer texture literal not uniquely proven");
        String sourceId=ids.iterator().next();int colon=sourceId.indexOf(':');
        String namespace=sourceId.substring(0,colon),path=sourceId.substring(colon+1);
        if(!namespace.matches("[a-z0-9_.-]+")||!path.matches("textures/model/[a-zA-Z0-9_./-]+\\.png")||path.contains(".."))
            throw new IllegalArgumentException("unknown/unsafe source renderer texture");
        JarEntry entry=jar.getJarEntry("assets/"+namespace+"/"+path);
        if(entry==null)throw new IllegalArgumentException("source-owned model PNG missing");
        byte[] bytes;
        try(InputStream stream=jar.getInputStream(entry)){bytes=stream.readAllBytes();}
        if(bytes.length<24||bytes[0]!=(byte)0x89||bytes[1]!='P'||bytes[2]!='N'||bytes[3]!='G')
            throw new IllegalArgumentException("invalid source texture PNG");
        ByteBuffer header=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int w=header.getInt(16),h=header.getInt(20);
        if(w<1||h<1||w>2048||h>2048)throw new IllegalArgumentException("PNG bounds not proven");
        return new Texture(namespace,path,bytes,w,h);
    }
    private static String hexSha(byte[] input){
        try {byte[] digest=MessageDigest.getInstance("SHA-256").digest(input);
            StringBuilder out=new StringBuilder();for(byte n:digest)out.append(String.format("%02x",n&255));return out.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static StringBuilder quote(StringBuilder out,String raw){
        out.append('"');for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);if(c=='"'||c=='\\')out.append('\\');
            if(c<32)throw new IllegalArgumentException("unsafe JSON source text");out.append(c);
        }return out.append('"');
    }
    private static String bake(SourceModel model,String texture,double rx,double rz){
        StringBuilder out=new StringBuilder("{\n  \"ambientocclusion\": false,\n  \"textures\": {\"particle\":");
        quote(out,texture).append(",\"all\":");quote(out,texture).append("},\n  \"elements\": [\n");
        int index=0;
        for(var p:model.drawn()){
            if(index++>0)out.append(",\n");
            double[] low={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
            double[] high={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(int a=0;a<2;a++)for(int b=0;b<2;b++)for(int c=0;c<2;c++){
                double[] vertex=rotate(p.x+p.px+(a==1?p.w:0),p.y+p.py+(b==1?p.h:0),p.z+p.pz+(c==1?p.d:0),rx,rz);
                for(int k=0;k<3;k++){low[k]=Math.min(low[k],vertex[k]);high[k]=Math.max(high[k],vertex[k]);}
            }
            out.append("    {\"from\":");vector(out,low);out.append(",\"to\":");vector(out,high);
            out.append(",\"shade\":false,\"faces\":{");
            Map<String,double[]> rects=new LinkedHashMap<>();
            double u=p.u,v=p.v,w=p.w,h=p.h,d=p.d;
            rects.put("east",new double[]{u+d+w,v+d,u+d+w+d,v+d+h});
            rects.put("west",new double[]{u,v+d,u+d,v+d+h});
            rects.put("down",new double[]{u+d,v,u+d+w,v+d});
            rects.put("up",new double[]{u+d+w,v+d,u+d+2*w,v});
            rects.put("north",new double[]{u+d,v+d,u+d+w,v+d+h});
            rects.put("south",new double[]{u+d+w+d,v+d,u+d+2*w+d,v+d+h});
            String[] directions={"east","west","up","down","north","south"};
            int[][] normals={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,-1},{0,0,1}};
            Map<String,double[]> uvFaces=new LinkedHashMap<>();
            for(int n=0;n<directions.length;n++){
                double[] origin=rotate(0,0,0,rx,rz),forward=rotate(normals[n][0],normals[n][1],normals[n][2],rx,rz);
                int x=(int)Math.round(forward[0]-origin[0]),y=(int)Math.round(forward[1]-origin[1]),z=(int)Math.round(forward[2]-origin[2]);
                int target=-1;for(int t=0;t<normals.length;t++)if(normals[t][0]==x&&normals[t][1]==y&&normals[t][2]==z)target=t;
                if(target<0||uvFaces.containsKey(directions[target]))throw new IllegalArgumentException("transformed face not axis-aligned");
                double[] uv=rects.get(directions[n]);
                uvFaces.put(directions[target],new double[]{Math.min(uv[0],uv[2])*16/model.w(),
                    Math.min(uv[1],uv[3])*16/model.h(),Math.max(uv[0],uv[2])*16/model.w(),Math.max(uv[1],uv[3])*16/model.h()});
            }
            int count=0;for(String key:directions){
                if(count++>0)out.append(',');quote(out,key).append(":{\"texture\":\"#all\",\"uv\":");vector(out,uvFaces.get(key));out.append('}');
            }
            out.append("}}");
        }
        out.append("\n  ],\n  \"gui_light\":\"front\"\n}\n");return out.toString();
    }
    private static double[] rotate(double x,double y,double z,double rx,double rz){
        y=-y;z=-z;double az=Math.toRadians(rz),ax=Math.toRadians(rx);
        double a=x*Math.cos(az)-y*Math.sin(az),b=x*Math.sin(az)+y*Math.cos(az);
        double ny=b*Math.cos(ax)-z*Math.sin(ax),nz=b*Math.sin(ax)+z*Math.cos(ax);
        return new double[]{snap(a+8),snap(ny+8),snap(nz+8)};
    }
    private static double snap(double n){if(!Double.isFinite(n)||n< -16.001||n>32.001)throw new IllegalArgumentException("unbounded model vertex");
        if(Math.abs(n-Math.rint(n))<1e-8)return Math.rint(n);return n;
    }
    private static void vector(StringBuilder out,double[] vector){out.append('[');for(int i=0;i<vector.length;i++){
        if(i>0)out.append(',');double value=vector[i];if(!Double.isFinite(value))throw new IllegalArgumentException("nonfinite model coordinate");
        out.append(String.format(Locale.ROOT,"%.6f",value));
    }out.append(']');}
}
