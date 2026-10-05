package dev.yinghuang.legacyforgebridge.rev254;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.*;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Generic, fail-closed Forge 1.7.x BlockSapling presentation/shape compatibility.
 *
 * <p>The converted candidate still contains source-owned 1.7 class files copied by LFB.  This
 * adapter reads only class-file structure; it never defines or executes those classes.  A rule is
 * admitted only when the source-owned inheritance chain provably terminates at the vanilla 1.7
 * BlockSapling class.  Source overrides narrow what can be adapted rather than being guessed.</p>
 */
public final class LegacySaplingSupport {
    private static final String SAPLING = "net/minecraft/block/BlockSapling";
    private static final Set<String> COLLISION_OVERRIDES = Set.of(
            "getCollisionBoundingBoxFromPool", "func_149668_a");
    private static final Set<String> RENDER_OVERRIDES = Set.of("getRenderType", "func_149645_b");
    private static final Set<String> DYNAMIC_BOUNDS = Set.of("setBlockBoundsBasedOnState", "func_149719_a");
    private static final Set<String> BOUNDS_SETTERS = Set.of("setBlockBounds", "func_149676_a");
    private static final Box VANILLA_SAPLING = new Box(.1D,0D,.1D,.9D,.8D,.9D);

    private record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        Box {
            if (!finite(minX,minY,minZ,maxX,maxY,maxZ)
                    || minX<0||minY<0||minZ<0||maxX>1||maxY>1||maxZ>1
                    || minX>maxX||minY>maxY||minZ>maxZ) throw new IllegalArgumentException("Invalid legacy sapling bounds");
        }
    }
    private record Rule(String id,String sourceClass,Box selection,boolean emptyCollision,boolean cutout,
                        boolean explicitBounds,boolean modelLooksCross) { }
    private static volatile Map<String,Rule> RULES;

    public static synchronized void discover() {
        if (RULES != null) return;
        Map<String,Rule> out = new LinkedHashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            Optional<Path> manifest = mod.findPath("legacyforgebridge/converted-content.json");
            if (manifest.isEmpty()) continue;
            try (Reader reader=Files.newBufferedReader(manifest.get(),StandardCharsets.UTF_8)) {
                JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray blocks=root.getAsJsonArray("blocks"); if(blocks==null)continue;
                for(JsonElement element:blocks){
                    if(!element.isJsonObject())continue; JsonObject block=element.getAsJsonObject();
                    if(!block.has("id")||!block.has("sourceClass"))continue;
                    String id=block.get("id").getAsString(); String source=normalize(block.get("sourceClass").getAsString());
                    Candidate c=analyze(mod::findPath,source);
                    if(c==null)continue;
                    boolean cross=modelLooksCross(mod,id);
                    Rule rule=new Rule(id,source,c.selection,c.emptyCollision,c.cutout,c.explicitBounds,cross);
                    Rule old=out.putIfAbsent(id,rule);
                    if(old!=null&&!old.equals(rule)) throw new IllegalStateException("Conflicting legacy sapling proof for "+id);
                }
            } catch(Exception e){
                LoggerFactory.getLogger("LFB-rev254").warn("Could not inspect converted candidate for generic sapling compatibility; leaving it unchanged",e);
            }
        }
        RULES=Map.copyOf(out);
        long collision=out.values().stream().filter(Rule::emptyCollision).count();
        long cutout=out.values().stream().filter(Rule::cutout).count();
        long shaped=out.values().stream().filter(v->v.selection()!=null).count();
        long badModels=out.values().stream().filter(v->!v.modelLooksCross()).count();
        LoggerFactory.getLogger("LFB-rev254").info("Generic Legacy Sapling Adapter: admitted="+out.size()+", emptyCollision="+collision+", cutout="+cutout+", selectionShape="+shaped+", preexistingNonCrossModels="+badModels);
        if(badModels>0)LoggerFactory.getLogger("LFB-rev254").warn("Some previously converted saplings do not already have a cross model. Runtime shape/collision is repaired, but old candidate resources may require reconversion; Bamboo 2.6.8.5 has an embedded backward-compatibility resource override.");
    }

    public static boolean isSapling(class_2960 id){return rule(id)!=null;}
    public static boolean emptyCollision(class_2960 id){Rule r=rule(id);return r!=null&&r.emptyCollision();}
    public static class_265 selectionShape(class_2960 id){
        Rule r=rule(id); if(r==null||r.selection()==null)return null; Box b=r.selection();
        return class_259.method_1081(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());
    }
    public static class_4970.class_2251 properties(class_2960 id,class_4970.class_2251 p){
        Rule r=rule(id); if(r==null)return p;
        return p.method_9624().method_22488().method_9634();
    }
    public static void installClientPresentation(){
        discover(); int mapped=0;
        for(Rule rule:RULES.values()) if(rule.cutout()){
            try{
                class_2960 id=class_2960.method_60654(rule.id()); Object value=class_7923.field_41175.method_63535(id);
                if(value instanceof class_2248 block){BlockRenderLayerMap.putBlock(block,class_11515.field_60925);mapped++;}
            }catch(RuntimeException e){LoggerFactory.getLogger("LFB-rev254").warn("Could not install generic sapling CUTOUT for "+rule.id(),e);}
        }
        if(mapped>0)LoggerFactory.getLogger("LFB-rev254").info("Installed generic legacy sapling CUTOUT presentation: blocks="+mapped);
    }

    public static String debugAnalyzeClassRoot(Path root,String sourceClass) throws Exception {
        Candidate c=analyze(name->{Path p=root.resolve(name);return Files.isRegularFile(p)?Optional.of(p):Optional.empty();},normalize(sourceClass));
        return c==null?"NONE":c.toString();
    }

    private static Rule rule(class_2960 id){if(RULES==null)discover();return id==null?null:RULES.get(id.toString());}

    private record Candidate(Box selection,boolean emptyCollision,boolean cutout,boolean explicitBounds){ }
    @FunctionalInterface private interface Lookup { Optional<Path> find(String name); }

    private static Candidate analyze(Lookup lookup,String source) throws Exception {
        if(source==null||source.isBlank())return null;
        List<ClassInfo> lineage=new ArrayList<>(); String current=source; Set<String> seen=new HashSet<>(); boolean found=false;
        while(current!=null&&seen.add(current)){
            Optional<Path> file=lookup.find(current+".class");
            if(file.isEmpty()) { found=SAPLING.equals(current); break; }
            ClassInfo info=ClassInfo.read(Files.readAllBytes(file.get())); lineage.add(info);
            if(SAPLING.equals(info.superName)){found=true;break;}
            current=info.superName;
        }
        if(!found)return null;
        boolean collisionOverride=hasMethod(lineage,COLLISION_OVERRIDES);
        boolean renderOverride=hasMethod(lineage,RENDER_OVERRIDES);
        boolean dynamicBounds=hasMethod(lineage,DYNAMIC_BOUNDS);
        boolean explicitBounds=false, ambiguousBounds=false; Box selection=VANILLA_SAPLING;
        List<ClassInfo> rootFirst=new ArrayList<>(lineage);Collections.reverse(rootFirst);
        for(ClassInfo info:rootFirst){
            List<Box> boxes=new ArrayList<>(); boolean sawSetter=false;
            for(MethodInfo method:info.methods)if("<init>".equals(method.name)){
                BoundsResult r=method.bounds(info.pool);sawSetter|=r.sawSetter;
                if(r.sawSetter&&r.box==null)ambiguousBounds=true;
                if(r.box!=null)boxes.add(r.box);
            }
            if(sawSetter){explicitBounds=true;
                Box first=boxes.isEmpty()?null:boxes.getFirst();
                if(first==null||boxes.stream().anyMatch(v->!v.equals(first)))ambiguousBounds=true;
                else selection=first;
            }
        }
        if(dynamicBounds||ambiguousBounds)selection=null;
        return new Candidate(selection,!collisionOverride,!renderOverride,explicitBounds);
    }

    private static boolean hasMethod(List<ClassInfo> lineage,Set<String> names){
        for(ClassInfo info:lineage)for(MethodInfo method:info.methods)if(names.contains(method.name))return true;return false;
    }

    private static boolean modelLooksCross(ModContainer mod,String rawId){
        try{
            int at=rawId.indexOf(':');if(at<=0)return false;String ns=rawId.substring(0,at),path=rawId.substring(at+1);
            Optional<Path> file=mod.findPath("assets/"+ns+"/models/block/"+path+".json");if(file.isEmpty())return false;
            JsonObject m=JsonParser.parseString(Files.readString(file.get())).getAsJsonObject();
            if(!m.has("parent"))return false;String parent=m.get("parent").getAsString();
            return Set.of("minecraft:block/cross","minecraft:block/crop").contains(parent);
        }catch(Exception ignored){return false;}
    }

    private static String normalize(String value){return value==null?null:value.replace('.','/');}
    private static boolean finite(double... v){for(double d:v)if(!Double.isFinite(d))return false;return true;}

    private record BoundsResult(boolean sawSetter,Box box){ }
    private record MethodRef(String owner,String name,String desc){ }
    private static final Object UNKNOWN=new Object(),REF=new Object();

    private static final class MethodInfo {
        final String name,desc; final byte[] code;
        MethodInfo(String name,String desc,byte[] code){this.name=name;this.desc=desc;this.code=code;}
        BoundsResult bounds(ConstantPool cp){
            if(code==null)return new BoundsResult(false,null);
            Object[] locals=new Object[32];locals[0]=REF;ArrayList<Object> stack=new ArrayList<>();
            Box last=null;boolean saw=false,unsafe=false;
            for(int pc=0;pc<code.length;){int op=code[pc]&255;int next;
                try{
                    switch(op){
                        case 0x0b->stack.add(0f);case 0x0c->stack.add(1f);case 0x0d->stack.add(2f);
                        case 0x2a->stack.add(REF);case 0x2b,0x2c,0x2d->stack.add(UNKNOWN);
                        case 0x22,0x23,0x24,0x25->{int i=op-0x22;stack.add(locals[i]==null?UNKNOWN:locals[i]);}
                        case 0x17->{int i=u1(pc+1);stack.add(locals[i]==null?UNKNOWN:locals[i]);}
                        case 0x43,0x44,0x45,0x46->{int i=op-0x43;locals[i]=pop(stack);}
                        case 0x38->{locals[u1(pc+1)]=pop(stack);}
                        case 0x12->{stack.add(cp.constant(u1(pc+1)));}
                        case 0x13,0x14->{stack.add(cp.constant(u2(pc+1)));}
                        case 0x62,0x66,0x6a,0x6e->{Object b=pop(stack),a=pop(stack);stack.add(floatOp(op,a,b));}
                        case 0x86->{Object v=pop(stack);stack.add(v instanceof Number n?n.floatValue():UNKNOWN);}
                        case 0x59->{Object v=peek(stack);stack.add(v);}
                        case 0x57->pop(stack);
                        case 0xb2->stack.add(UNKNOWN);
                        case 0xb4->{pop(stack);stack.add(UNKNOWN);} case 0xb5->{pop(stack);pop(stack);}
                        case 0xbb->stack.add(REF);
                        case 0xb6,0xb7,0xb8,0xb9,0xba->{
                            int idx=u2(pc+1);MethodRef ref=cp.method(idx);String desc=ref==null?null:ref.desc;
                            int argc=desc==null?0:argumentCount(desc);Object[] args=new Object[argc];for(int i=argc-1;i>=0;i--)args[i]=pop(stack);
                            if(op!=0xb8&&op!=0xba)pop(stack);
                            if(ref!=null&&BOUNDS_SETTERS.contains(ref.name)&&"(FFFFFF)V".equals(ref.desc)){
                                saw=true;if(!unsafe&&args.length==6&&allNumbers(args)){
                                    try{last=new Box(num(args[0]),num(args[1]),num(args[2]),num(args[3]),num(args[4]),num(args[5]));}
                                    catch(RuntimeException bad){last=null;}
                                }else last=null;
                            }
                            if(desc!=null&&!returnsVoid(desc))stack.add(UNKNOWN);
                        }
                        case 0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,0xa0,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xc6,0xc7,0xc8,0xc9,0xaa,0xab->unsafe=true;
                        default->{ }
                    }
                    next=nextPc(code,pc,op);
                }catch(RuntimeException malformed){return new BoundsResult(saw,null);}
                if(next<=pc||next>code.length)return new BoundsResult(saw,null);pc=next;
            }
            return new BoundsResult(saw,unsafe&&saw?null:last);
        }
        private int u1(int p){return code[p]&255;} private int u2(int p){return ((code[p]&255)<<8)|(code[p+1]&255);}
    }

    private static final class ClassInfo {
        final String superName;final List<MethodInfo> methods;final ConstantPool pool;
        ClassInfo(String superName,List<MethodInfo> methods,ConstantPool pool){this.superName=superName;this.methods=methods;this.pool=pool;}
        static ClassInfo read(byte[] bytes)throws IOException{
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));if(in.readInt()!=0xCAFEBABE)throw new IOException("Not a class file");
            in.readUnsignedShort();in.readUnsignedShort();ConstantPool cp=ConstantPool.read(in);in.readUnsignedShort();in.readUnsignedShort();int superIndex=in.readUnsignedShort();
            String superName=cp.className(superIndex);int interfaces=in.readUnsignedShort();for(int i=0;i<interfaces;i++)in.readUnsignedShort();
            skipMembers(in);int methods=in.readUnsignedShort();List<MethodInfo> list=new ArrayList<>();
            for(int i=0;i<methods;i++){
                in.readUnsignedShort();String name=cp.utf(in.readUnsignedShort()),desc=cp.utf(in.readUnsignedShort());int attrs=in.readUnsignedShort();byte[] code=null;
                for(int a=0;a<attrs;a++){String attr=cp.utf(in.readUnsignedShort());int len=in.readInt();
                    if("Code".equals(attr)){
                        byte[] raw=in.readNBytes(len);DataInputStream c=new DataInputStream(new ByteArrayInputStream(raw));c.readUnsignedShort();c.readUnsignedShort();int n=c.readInt();code=c.readNBytes(n);
                    }else in.skipNBytes(len);
                }
                list.add(new MethodInfo(name,desc,code));
            }
            return new ClassInfo(superName,List.copyOf(list),cp);
        }
        static void skipMembers(DataInputStream in)throws IOException{int count=in.readUnsignedShort();for(int i=0;i<count;i++){in.readUnsignedShort();in.readUnsignedShort();in.readUnsignedShort();int attrs=in.readUnsignedShort();for(int a=0;a<attrs;a++){in.readUnsignedShort();long len=Integer.toUnsignedLong(in.readInt());in.skipNBytes(len);}}}
    }

    private static final class ConstantPool {
        final int[] tag;final Object[] a,b;
        ConstantPool(int[] tag,Object[] a,Object[] b){this.tag=tag;this.a=a;this.b=b;}
        static ConstantPool read(DataInputStream in)throws IOException{
            int n=in.readUnsignedShort();int[] tag=new int[n];Object[] a=new Object[n],b=new Object[n];
            for(int i=1;i<n;i++){int t=in.readUnsignedByte();tag[i]=t;switch(t){
                case 1->a[i]=in.readUTF();case 3->a[i]=in.readInt();case 4->a[i]=in.readFloat();case 5->{a[i]=in.readLong();i++;}case 6->{a[i]=in.readDouble();i++;}
                case 7,8,16,19,20->a[i]=in.readUnsignedShort();case 9,10,11,12,17,18->{a[i]=in.readUnsignedShort();b[i]=in.readUnsignedShort();}
                case 15->{a[i]=in.readUnsignedByte();b[i]=in.readUnsignedShort();}default->throw new IOException("Unsupported constant-pool tag "+t);
            }}return new ConstantPool(tag,a,b);
        }
        String utf(int i){return i>0&&i<tag.length&&tag[i]==1?(String)a[i]:null;}
        String className(int i){if(i<=0||tag[i]!=7)return null;return utf((Integer)a[i]);}
        Object constant(int i){if(i<=0||i>=tag.length)return UNKNOWN;return switch(tag[i]){case 3,4,5,6->a[i];case 8->utf((Integer)a[i]);default->UNKNOWN;};}
        MethodRef method(int i){if(i<=0||i>=tag.length||!(tag[i]==10||tag[i]==11))return null;String owner=className((Integer)a[i]);int nt=(Integer)b[i];if(nt<=0||tag[nt]!=12)return null;return new MethodRef(owner,utf((Integer)a[nt]),utf((Integer)b[nt]));}
    }

    private static Object pop(ArrayList<Object> s){return s.isEmpty()?UNKNOWN:s.remove(s.size()-1);}private static Object peek(ArrayList<Object>s){return s.isEmpty()?UNKNOWN:s.get(s.size()-1);}
    private static Object floatOp(int op,Object a,Object b){if(!(a instanceof Number x)||!(b instanceof Number y))return UNKNOWN;float A=x.floatValue(),B=y.floatValue();return switch(op){case 0x62->A+B;case 0x66->A-B;case 0x6a->A*B;case 0x6e->B==0F?Float.NaN:A/B;default->Float.NaN;};}
    private static boolean allNumbers(Object[] a){for(Object v:a)if(!(v instanceof Number))return false;return true;}private static double num(Object v){return ((Number)v).doubleValue();}
    private static int argumentCount(String d){int n=0;for(int i=1;i<d.length()&&d.charAt(i)!=')';n++){char c=d.charAt(i++);if(c=='L'){i=d.indexOf(';',i)+1;}else if(c=='['){while(d.charAt(i)=='[')i++;if(d.charAt(i)=='L')i=d.indexOf(';',i)+1;else i++;}}return n;}
    private static boolean returnsVoid(String d){int i=d.indexOf(')');return i>=0&&i+1<d.length()&&d.charAt(i+1)=='V';}
    private static int nextPc(byte[] c,int pc,int op){
        return switch(op){
            case 0x10,0x12,0x15,0x16,0x17,0x18,0x19,0x36,0x37,0x38,0x39,0x3a,0xa9,0xbc->pc+2;
            case 0x11,0x13,0x14,0x84,0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,0xa0,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xbb,0xbd,0xc0,0xc1,0xc6,0xc7->pc+3;
            case 0xb9,0xba,0xc8,0xc9->pc+5;case 0xc5->pc+4;
            case 0xaa->{int p=(pc+4)&~3;int low=i4(c,p+4),high=i4(c,p+8);yield p+12+(high-low+1)*4;}
            case 0xab->{int p=(pc+4)&~3;int pairs=i4(c,p+4);yield p+8+pairs*8;}
            case 0xc4->{int nested=c[pc+1]&255;yield pc+(nested==0x84?6:4);}default->pc+1;
        };
    }
    private static int i4(byte[] c,int p){return ((c[p]&255)<<24)|((c[p+1]&255)<<16)|((c[p+2]&255)<<8)|(c[p+3]&255);}
    private LegacySaplingSupport(){}
}