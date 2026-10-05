package dev.yinghuang.legacyforgebridge.rev256;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Non-executing source evidence. Run BEFORE source stripping; data, not source JVM classes,
 * travels with the converted candidate. This class has no Minecraft/Fabric dependency. */
public final class SaplingProof {
 public static final String PATH="legacyforgebridge/sapling-client-proof.properties";
 private static final String SAPLING="net/minecraft/block/BlockSapling";
 private static final Set<String> COLLISION_OVERRIDES=Set.of("getCollisionBoundingBoxFromPool","func_149668_a","addCollisionBoxesToList","func_149743_a");
 private static final Set<String> RENDER_OVERRIDES=Set.of("getRenderType","func_149645_b");
 private static final Set<String> DYNAMIC_BOUNDS=Set.of("setBlockBoundsBasedOnState","func_149719_a","getSelectedBoundingBoxFromPool","func_149633_g");
 private static final Set<String> BOUNDS_SETTERS=Set.of("setBlockBounds","func_149676_a");
 private static final Box VANILLA_SAPLING=new Box(.1D,0D,.1D,.9D,.8D,.9D);
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public Box {
            if (!finite(minX,minY,minZ,maxX,maxY,maxZ)
                    || minX<0||minY<0||minZ<0||maxX>1||maxY>1||maxZ>1
                    || minX>maxX||minY>maxY||minZ>maxZ) throw new IllegalArgumentException("Invalid legacy sapling bounds");
        }
    }
    public record Candidate(Box selection,boolean emptyCollision,boolean cutout,boolean explicitBounds){ }
    @FunctionalInterface public interface Lookup { Optional<Path> find(String name); }

    public static Candidate analyze(Lookup lookup,String source) throws Exception {
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
        for(ClassInfo info:lineage) for(MethodInfo m:info.methods)
            if(!"<init>".equals(m.name) && m.code!=null && m.bounds(info.pool).sawSetter()) dynamicBounds=true;
        boolean explicitBounds=false, ambiguousBounds=false; Box selection=VANILLA_SAPLING;
        // Apply constructor bounds from oldest source superclass to the registered implementation.
        List<ClassInfo> rootFirst=new ArrayList<>(lineage);Collections.reverse(rootFirst);
        for(ClassInfo info:rootFirst){
            List<Box> boxes=new ArrayList<>(); boolean sawSetter=false;
            for(MethodInfo method:info.methods)if("<init>".equals(method.name)){
                BoundsResult r=method.bounds(info.pool);if(method.code==null)ambiguousBounds=true;sawSetter|=r.sawSetter;
                if(r.sawSetter&&r.box==null)ambiguousBounds=true;
                if(r.box!=null)boxes.add(r.box);
            }
            if(sawSetter){explicitBounds=true; if(boxes.size()!=info.methods.stream().filter(m->"<init>".equals(m.name)).count())ambiguousBounds=true;
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
                        case 0xbb->stack.add(UNKNOWN);
                        case 0xb6,0xb7,0xb8,0xb9,0xba->{
                            if(op==0xba)unsafe=true; int idx=u2(pc+1);MethodRef ref=cp.method(idx);String desc=ref==null?null:ref.desc;
                            int argc=desc==null?0:argumentCount(desc);Object[] args=new Object[argc];for(int i=argc-1;i>=0;i--)args[i]=pop(stack);
                            Object receiver=(op!=0xb8&&op!=0xba)?pop(stack):UNKNOWN;
                            if(ref!=null&&BOUNDS_SETTERS.contains(ref.name)&&"(FFFFFF)V".equals(ref.desc)){
                                saw=true;if(receiver==REF&&!unsafe&&args.length==6&&allNumbers(args)){
                                    try{last=new Box(num(args[0]),num(args[1]),num(args[2]),num(args[3]),num(args[4]),num(args[5]));}
                                    catch(RuntimeException bad){last=null;}
                                }else last=null;
                            }
                            if(desc!=null&&!returnsVoid(desc))stack.add(UNKNOWN);
                        }
                        case 0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,0xa0,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xc6,0xc7,0xc8,0xc9,0xaa,0xab->unsafe=true;
                        case 0x00,0xb1 -> { }
                        case 0x01 -> stack.add(UNKNOWN);
                        case 0x02,0x03,0x04,0x05,0x06,0x07,0x08 -> stack.add(op-0x03);
                        case 0x10 -> stack.add((int)code[pc+1]);
                        case 0x11 -> stack.add((int)(short)u2(pc+1));
                        case 0x19 -> stack.add(u1(pc+1)==0?REF:UNKNOWN);
                        default -> { unsafe=true; stack.clear(); }
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
                        if(len<0||len>2_000_000)throw new IOException("Code attribute too large"); byte[] raw=in.readNBytes(len);DataInputStream c=new DataInputStream(new ByteArrayInputStream(raw));c.readUnsignedShort();c.readUnsignedShort();int n=c.readInt();if(n<0||n>65535)throw new IOException("Invalid code length");code=c.readNBytes(n); if(c.readUnsignedShort()!=0) code=null;
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

 public static Candidate inspect(Path jar,String source) throws Exception {
  try(FileSystem fs=FileSystems.newFileSystem(jar,Map.of())) {
   return analyze(n->{Path p=fs.getPath("/"+n);return Files.isRegularFile(p)?Optional.of(p):Optional.empty();},normalize(source));
  }
 }
 public static void add(Properties p,int i,String id,String source,Candidate c){
  String k="rule."+i+".";
  p.setProperty(k+"id",id);p.setProperty(k+"source",normalize(source));
  p.setProperty(k+"emptyCollision",Boolean.toString(c.emptyCollision()));
  p.setProperty(k+"cutout",Boolean.toString(c.cutout()));
  p.setProperty(k+"explicitBounds",Boolean.toString(c.explicitBounds()));
  Box b=c.selection();p.setProperty(k+"selection",b==null?"unresolved":b.minX()+","+b.minY()+","+b.minZ()+","+b.maxX()+","+b.maxY()+","+b.maxZ());
 }
 public static Candidate decode(Properties p,int i){
  String k="rule."+i+".";String b=p.getProperty(k+"selection", "");
  Box box=null;if(!b.equals("unresolved")) {
   String[] a=b.split(",",-1);if(a.length!=6)throw new IllegalArgumentException("Missing bounds");
   box=new Box(Double.parseDouble(a[0]),Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3]),Double.parseDouble(a[4]),Double.parseDouble(a[5]));
  }
  return new Candidate(box,flag(p,k+"emptyCollision"),flag(p,k+"cutout"),flag(p,k+"explicitBounds"));
 }
 private static boolean flag(Properties p,String k){String v=p.getProperty(k);if(!"true".equals(v)&&!"false".equals(v))throw new IllegalArgumentException("Missing flag "+k);return Boolean.parseBoolean(v);}
 public static Properties header(String hash){
  if(hash==null||!hash.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Invalid hash");
  Properties p=new Properties();p.setProperty("schema","1");p.setProperty("sourceSha256",hash.toLowerCase(Locale.ROOT));p.setProperty("count","0");return p;
 }
 public static Map<String,Candidate> verified(Properties p,String hash,Map<String,String> identities){
  if(!"1".equals(p.getProperty("schema"))||hash==null||!hash.matches("[0-9a-fA-F]{64}")||!hash.equalsIgnoreCase(p.getProperty("sourceSha256","")))return Map.of();
  int n;try{n=Integer.parseInt(p.getProperty("count","-1"));}catch(NumberFormatException bad){return Map.of();}
  if(n<0||n>4096)return Map.of();Map<String,Candidate> out=new LinkedHashMap<>();Set<String> conflicted=new HashSet<>();
  for(int i=0;i<n;i++)try {
   String k="rule."+i+".",id=p.getProperty(k+"id"),source=p.getProperty(k+"source");
   if(id==null||source==null||!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||!source.equals(normalize(identities.get(id)))||conflicted.contains(id))continue;
   Candidate c=decode(p,i),prior=out.putIfAbsent(id,c);
   if(prior!=null&&!prior.equals(c)){out.remove(id);conflicted.add(id);}
  }catch(RuntimeException malformed){/* A malformed row is not a valid proof. */}
  return Map.copyOf(out);
 }
 public static void write(Path path,Properties p)throws IOException{
  Files.createDirectories(path.getParent());StringBuilder b=new StringBuilder("# LFB source sapling evidence; generated before source-class strip\n");
  for(String k:new TreeSet<>(p.stringPropertyNames()))b.append(escape(k)).append('=').append(escape(p.getProperty(k))).append('\n');
  Files.writeString(path,b,java.nio.charset.StandardCharsets.UTF_8);
 }
 private static String escape(String v){return v.replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r").replace("=","\\=").replace(":","\\:");}
 public static void main(String[] a)throws Exception{
  Candidate c=inspect(Path.of(a[0]),a[1]);if(c==null)throw new IllegalArgumentException("Not a proven sapling");
  Properties p=header(a[3]);add(p,0,a[2],a[1],c);p.setProperty("count","1");write(Path.of(a[4]),p);System.out.println(c);
 }
 private SaplingProof(){}
}
