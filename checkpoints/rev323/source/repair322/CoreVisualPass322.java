
package dev.yinghuang.legacyforgebridge.repair322;
import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.*;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.rev320.BlockFacts320;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import java.util.*;import java.util.jar.*;import java.lang.reflect.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.commons.*;
import static org.objectweb.asm.Opcodes.*;
import dev.yinghuang.legacyforgebridge.repair322.VisualSupport322.*;
/** Supplemental SOURCE-driven visual pass. Invoked by the LFB converter, not a resource pack. */
public final class CoreVisualPass322 {
 static final String ABI="dev/yinghuang/legacyforgebridge/repair322/VisualSupport322";
 static final Gson G=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
 final Path source,stage; final Map<String,ClassNode> classes=new LinkedHashMap<>();
 final Map<String,String> entries=new HashMap<>();final Map<String,String> spriteCache=new HashMap<>();
 final LegacyIconTableAnalyzer analyzer=new LegacyIconTableAnalyzer();
 final LegacyRegistryAnalyzer.Analysis analysis; final JsonObject content,iconBook,nameBook,manifest;
 final JarFile jar;
 CoreVisualPass322(Path source,Path stage)throws Exception {
  this.source=source;this.stage=stage;jar=new JarFile(source.toFile());
  for(var e:Collections.list(jar.entries())) {String low=e.getName().toLowerCase(Locale.ROOT);if(entries.putIfAbsent(low,e.getName())!=null)throw new IllegalArgumentException("ambiguous source path "+low);if(e.getName().endsWith(".class")){ClassNode c=new ClassNode();new ClassReader(jar.getInputStream(e)).accept(c,0);classes.put(c.name,c);}}
  content=read("legacyforgebridge/converted-content.json"); iconBook=read("legacyforgebridge/icon-presentation.json");nameBook=read("legacyforgebridge/item-names.json");
  manifest=new JsonObject();manifest.addProperty("schemaVersion",1);manifest.addProperty("sourceSha256",sha(Files.readAllBytes(source)));manifest.add("plants",new JsonObject());manifest.add("grids",new JsonObject());manifest.add("exclusions",new JsonObject());
  analysis=new LegacyRegistryAnalyzer().analyze(source);
 }
 public static void apply(ConversionContext c)throws Exception {apply(c.sourceJar(),c.stagingDir());}
 public static void apply(Path source,Path stage)throws Exception {
  if(!Files.isRegularFile(stage.resolve("legacyforgebridge/converted-content.json")))return;
  try(var p=new PassResource(new CoreVisualPass322(source,stage))){p.pass.run();}
 }
 private record PassResource(CoreVisualPass322 pass) implements AutoCloseable {public void close()throws Exception {pass.jar.close();}}
 JsonObject read(String p)throws Exception {Path f=stage.resolve(p);return Files.exists(f)?JsonParser.parseString(Files.readString(f)).getAsJsonObject():new JsonObject();}
 static JsonObject obj(JsonObject o,String k) {if(!o.has(k))o.add(k,new JsonObject());return o.getAsJsonObject(k);}
 void write(String p,Object value)throws Exception {Path f=stage.resolve(p).normalize();if(!f.startsWith(stage))throw new IllegalArgumentException(p);Files.createDirectories(f.getParent());Files.writeString(f,G.toJson(value),StandardCharsets.UTF_8);}
 void bytes(String p,byte[] b)throws Exception {Path f=stage.resolve(p).normalize();if(!f.startsWith(stage))throw new IllegalArgumentException(p);Files.createDirectories(f.getParent());Files.write(f,b);}
 static String sha(byte[] b)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 static Object invoke(Object a,String name,Object...args)throws Exception {for(var m:a.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);try{return m.invoke(a,args);}catch(InvocationTargetException e){Throwable t=e.getCause();if(t instanceof Exception ex)throw ex;throw e;}}throw new NoSuchMethodException(name);}
 static Object field(Object a,String name)throws Exception {var f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
 static void set(Object a,String name,Object v)throws Exception {var f=a.getClass().getDeclaredField(name);f.setAccessible(true);f.set(a,v);}
 static Object makeInner(String name,Object...args)throws Exception {Class<?> c=Class.forName(LegacyIconTableAnalyzer.class.getName()+"$"+name);for(var k:c.getDeclaredConstructors())if(k.getParameterCount()==args.length){k.setAccessible(true);return k.newInstance(args);}throw new NoSuchMethodException(name);}
 void reset()throws Exception {set(analyzer,"budget",1000000);set(analyzer,"readOnly",false);set(analyzer,"geometryMode",true);}
 String sprite(String raw,String ns)throws Exception {
  if(spriteCache.containsKey(raw))return spriteCache.get(raw);
  if(raw.startsWith("minecraft:block/"))return raw;
  String[] s=raw.split(":",2);if(s.length!=2)throw new IllegalArgumentException("unqualified sprite "+raw);
  String key=("assets/"+s[0]+"/textures/blocks/"+s[1]+".png").toLowerCase(Locale.ROOT);
  String exact=entries.get(key);if(exact==null)throw new IllegalArgumentException("missing source sprite "+raw);
  byte[] data=jar.getInputStream(jar.getJarEntry(exact)).readAllBytes();
  String out="block/lfb_visual322/"+sha((raw+":"+sha(data)).getBytes(StandardCharsets.UTF_8)).substring(0,24);
  bytes("assets/"+ns+"/textures/"+out+".png",data);
  if(entries.containsKey(key+".mcmeta"))bytes("assets/"+ns+"/textures/"+out+".png.mcmeta",jar.getInputStream(jar.getJarEntry(entries.get(key+".mcmeta"))).readAllBytes());
  String result=ns+":"+out;spriteCache.put(raw,result);return result;
 }
 void run()throws Exception {
  JsonObject surfaces=read("legacyforgebridge/source-surfaces319.json");Map<String,String> renderers=new HashMap<>();
  for(var e:obj(surfaces,"rules").entrySet()){JsonObject r=e.getValue().getAsJsonObject();if(r.has("sourceRenderer")&&!r.get("sourceRenderer").getAsString().startsWith("platform:"))renderers.put(r.get("sourceClass").getAsString(),r.get("sourceRenderer").getAsString());}
  analyzer.lfb$contracts320(source,analysis,renderers);
  for(var e:content.getAsJsonArray("blocks")){JsonObject b=e.getAsJsonObject();String owner=b.get("sourceClass").getAsString();if(renderers.containsKey(owner))try {plant(b,renderers.get(owner));}catch(Exception ex){obj(manifest,"exclusions").addProperty(b.get("id").getAsString(),ex.toString());System.out.println("[LFB visual322] EXCLUDED "+b.get("id")+" "+ex);}}
  gridPass();
  write("legacyforgebridge/icon-presentation.json",iconBook);write("legacyforgebridge/item-names.json",nameBook);write("legacyforgebridge/visual-programs322.json",manifest);
  System.out.println("[LFB visual322] plants="+obj(manifest,"plants").size()+" grids="+obj(manifest,"grids").size()+" exclusions="+obj(manifest,"exclusions").size());
 }
 static MethodNode method(ClassNode c,String name) {return c.methods.stream().filter(m->m.name.equals(name)).findFirst().orElse(null);}
 final class Loader extends ClassLoader {Loader(){super(CoreVisualPass322.class.getClassLoader());}Class<?> define(String n,byte[] b){return defineClass(n.replace('/','.'),b,0,b.length);}}
 record Programs(String blockName,String rendererName,byte[] blockBytes,byte[] rendererBytes,Class<?> blockClass,Class<?> rendererClass){}
 Programs compile(String owner,String renderer,Object sourceObject,String ns,String token)throws Exception {
  String bp="dev/yinghuang/legacyforgebridge/generated/visual322/B"+token, rp="dev/yinghuang/legacyforgebridge/generated/visual322/R"+token;
  ClassNode block=classes.get(owner), render=classes.get(renderer);if(block==null||render==null)throw new IllegalArgumentException("missing source class");
  Map<String,String> types=new HashMap<>(Map.of(owner,bp,renderer,rp,"net/minecraft/block/Block",ABI+"$Block","net/minecraft/block/BlockBush",ABI+"$Block","net/minecraft/client/renderer/RenderBlocks",ABI+"$Renderer","net/minecraft/world/IBlockAccess",ABI+"$World","net/minecraft/util/IIcon","java/lang/String"));
  Remapper remap=new Remapper(){public String map(String n){return types.getOrDefault(n,n);}};
  Map<String,List<MethodNode>> selected=new LinkedHashMap<>(); selected.put(owner,new ArrayList<>());selected.put(renderer,new ArrayList<>());
  MethodNode bounds=method(block,"func_149719_a");if(bounds==null)throw new IllegalArgumentException("no bounded source method");selected.get(owner).add(bounds);
  MethodNode world=method(render,"renderWorldBlock");if(world==null)throw new IllegalArgumentException("no source world renderer");selected.get(renderer).add(world);
  Map<String,FieldInsnNode> fields=new LinkedHashMap<>();
  Set<String> platformMethods=Set.of("func_149676_a","func_72805_g","func_147439_a","func_147782_a","func_147771_a","func_147757_a","func_147784_q","func_147746_l","func_147796_n","func_147775_a");
  boolean changed;do{changed=false;for(var entry:selected.entrySet())for(MethodNode m:List.copyOf(entry.getValue()))for(AbstractInsnNode i:m.instructions){
    if(i instanceof InvokeDynamicInsnNode||i.getOpcode()==NEW||i.getOpcode()==ATHROW||i.getOpcode()==MONITORENTER)throw new IllegalArgumentException("unsafe visual opcode");
    if(i instanceof JumpInsnNode j&&m.instructions.indexOf(j.label)<m.instructions.indexOf(i))throw new IllegalArgumentException("source loop requires bounded compiler");
    if(i instanceof MethodInsnNode call){
      if(selected.containsKey(call.owner)&&method(classes.get(call.owner),call.name)!=null){MethodNode target=method(classes.get(call.owner),call.name);if(!selected.get(call.owner).contains(target)){selected.get(call.owner).add(target);changed=true;}}
      else if(!types.containsKey(call.owner)||!platformMethods.contains(call.name))throw new IllegalArgumentException("unproved visual call "+call.owner+"."+call.name);
    }
    if(i instanceof FieldInsnNode f){if(selected.containsKey(f.owner))fields.putIfAbsent(f.owner+"."+f.name,f);else if(!f.owner.equals("net/minecraft/client/renderer/RenderBlocks"))throw new IllegalArgumentException("unproved visual field "+f.owner+"."+f.name);}
  }}while(changed);
  Map<String,byte[]> output=new LinkedHashMap<>();
  for(var entry:selected.entrySet()){
    String old=entry.getKey(),name=types.get(old),parent=old.equals(owner)?ABI+"$Block":"java/lang/Object";
    ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){protected String getCommonSuperClass(String a,String b){return a.equals(b)?a:"java/lang/Object";}};
    cw.visit(V17,ACC_PUBLIC|ACC_FINAL,name,null,parent,old.equals(renderer)?new String[]{ABI+"$Drawer"}:null);
    MethodVisitor ctor=cw.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();ctor.visitVarInsn(ALOAD,0);ctor.visitMethodInsn(INVOKESPECIAL,parent,"<init>","()V",false);
    MethodVisitor clinit=cw.visitMethod(ACC_STATIC,"<clinit>","()V",null,null);clinit.visitCode();
    for(var f:fields.values())if(f.owner.equals(old)){
      String desc=remap.mapDesc(f.desc);boolean stat=f.getOpcode()==GETSTATIC||f.getOpcode()==PUTSTATIC;
      cw.visitField(ACC_PUBLIC|(stat?ACC_STATIC:0),f.name,desc,null,null).visitEnd();
      Object value=stat?invoke(analyzer,"readStatic",old,f.name,0):((Map<?,?>)field(sourceObject,"fields")).get(f.name);
      if(value==null)throw new IllegalArgumentException("missing proven source field "+f.name);
      if(value.getClass().getSimpleName().equals("Icon"))value=sprite((String)invoke(value,"id"),ns);
      if(!(value instanceof Number||value instanceof String))throw new IllegalArgumentException("unsupported field type "+f.name);
      MethodVisitor mv=stat?clinit:ctor;if(!stat)mv.visitVarInsn(ALOAD,0);mv.visitLdcInsn(value);mv.visitFieldInsn(stat?PUTSTATIC:PUTFIELD,name,f.name,desc);
    }
    ctor.visitInsn(RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();clinit.visitInsn(RETURN);clinit.visitMaxs(0,0);clinit.visitEnd();
    for(var m:entry.getValue()){MethodVisitor mv=cw.visitMethod((m.access&~(ACC_PRIVATE|ACC_PROTECTED))|ACC_PUBLIC,m.name,remap.mapMethodDesc(m.desc),null,null);m.accept(new MethodRemapper(mv,remap));}
    cw.visitEnd();output.put(name,cw.toByteArray());
  }
  Loader l=new Loader();Class<?> bc=l.define(bp,output.get(bp)),rc=l.define(rp,output.get(rp));return new Programs(bp,rp,output.get(bp),output.get(rp),bc,rc);
 }
 void plant(JsonObject b,String renderer)throws Exception {
  String id=b.get("id").getAsString(),owner=b.get("sourceClass").getAsString(),ns=id.split(":",2)[0],path=id.split(":",2)[1];
  var registration=analysis.blocks().stream().filter(r->r.registryName().equals(b.get("legacyRegistryName").getAsString())).findFirst().orElseThrow();
  reset();Object original=invoke(analyzer,"geometryObject",registration);invoke(analyzer,"ensureGeometryIcons",original,0);
  Object getter=invoke(analyzer,"find",owner,List.of("getIcon","func_149691_a"),"(II)Lnet/minecraft/util/IIcon;");
  Map<Integer,String[]> icons=new TreeMap<>();
  for(int m=0;m<16;m++)try{reset();String[] faces=new String[6];for(int f=0;f<6;f++){Object icon=invoke(analyzer,"run",getter,original,List.of(f,m),0);faces[f]=sprite((String)invoke(icon,"id"),ns);}icons.put(m,faces);}catch(Exception missing){}
  if(icons.isEmpty())throw new IllegalArgumentException("no valid metadata icons");
  String token=sha((id+":"+manifest.get("sourceSha256").getAsString()).getBytes(StandardCharsets.UTF_8)).substring(0,20);Programs p=compile(owner,renderer,original,ns,token);
  Block block=(Block)p.blockClass().getConstructor().newInstance();Drawer draw=(Drawer)p.rendererClass().getConstructor().newInstance();icons.forEach((m,a)->block.icons[m]=a);
  JsonObject rule=new JsonObject();rule.addProperty("blockProgram",p.blockName().replace('/','.'));rule.addProperty("rendererProgram",p.rendererName().replace('/','.'));rule.addProperty("sourceClass",owner);rule.addProperty("sourceRenderer",renderer);rule.add("icons",G.toJsonTree(icons));rule.add("textures",G.toJsonTree(new TreeSet<>(spriteCache.values())));rule.addProperty("blockSha256",sha(p.blockBytes()));rule.addProperty("rendererSha256",sha(p.rendererBytes()));
  JsonObject bs=read("assets/"+ns+"/blockstates/"+path+".json"), variants=obj(bs,"variants");
  JsonObject contracts=read("legacyforgebridge/source-contracts320.json"), contractStates=obj(obj(contracts,"surfaces"),id);
  JsonObject catalog=obj(obj(iconBook,"items"),id);
  Map<Integer,String> keys=itemNames(original,registration,icons.keySet());
  for(var e:icons.entrySet()){
    int m=e.getKey();World world=VisualSupport322.probe(block,m,0,0,0,0);block.func_149719_a(world,0,0,0);Renderer r=new Renderer(block,m);draw.renderWorldBlock(world,0,0,0,block,0,r);
    if(r.draws.isEmpty())throw new IllegalArgumentException("no source draws meta="+m);
    for(int conn=0;conn<16;conn++)for(int[] xyz:new int[][]{{0,0,0},{-1017,36,1242},{31,64,-17}}){World test=VisualSupport322.probe(block,m,xyz[0],xyz[1],xyz[2],conn);block.func_149719_a(test,xyz[0],xyz[1],xyz[2]);Renderer rr=new Renderer(block,m);draw.renderWorldBlock(test,xyz[0],xyz[1],xyz[2],block,0,rr);for(Draw d:rr.draws)for(double v:d.bounds())if(!Double.isFinite(v)||v<-.001||v>1.001)throw new IllegalArgumentException("unbounded generated geometry");}
    String modelPath="lfb_visual322/"+token+"/"+m;
    write("assets/"+ns+"/models/block/"+modelPath+".json",model(r.draws));JsonObject var=new JsonObject();var.addProperty("model",ns+":block/"+modelPath);variants.add("legacy_meta="+m,var);
    JsonObject im=new JsonObject();im.addProperty("parent","minecraft:item/generated");JsonObject tx=new JsonObject();tx.addProperty("layer0",e.getValue()[0]);im.add("textures",tx);write("assets/"+ns+"/models/item/"+modelPath+".json",im);
    JsonObject idef=new JsonObject(), leaf=new JsonObject();leaf.addProperty("type","minecraft:model");leaf.addProperty("model",ns+":item/"+modelPath);
    String oldPath="assets/"+ns+"/items/lfb_meta/"+path+"/"+m+".json";JsonObject old=read(oldPath);if(old.has("model")&&old.getAsJsonObject("model").has("tints"))leaf.add("tints",old.getAsJsonObject("model").get("tints"));
    int rgb=sourceColor(id,m);{JsonArray ts=new JsonArray();JsonObject t=new JsonObject();t.addProperty("type","minecraft:constant");t.addProperty("value",rgb);ts.add(t);leaf.add("tints",ts);}
    idef.add("model",leaf);write(oldPath,idef);catalog.addProperty(""+m,ns+":lfb_meta/"+path+"/"+m);
    block.func_149719_a(world,0,0,0);JsonObject shape=new JsonObject();shape.add("outline",G.toJsonTree(block.bounds()));shape.addProperty("emptyCollision",true);shape.addProperty("draws",r.draws.size());contractStates.add(""+m,shape);obj(contracts,"exclusions").remove(id+":"+m);
    if(keys.containsKey(m)){String key="lfb.visual322."+ns+"."+path+"."+m+".name";obj(obj(nameBook,"items"),id).addProperty(""+m,key);copyName(keys.get(m),key);}
  }
  write("assets/"+ns+"/blockstates/"+path+".json",bs);write("legacyforgebridge/source-contracts320.json",contracts);bytes(p.blockName()+".class",p.blockBytes());bytes(p.rendererName()+".class",p.rendererBytes());obj(manifest,"plants").add(id,rule);
  System.out.println("[LFB visual322] SOURCE_GEOMETRY "+id+" states="+icons.keySet()+" sampleTests="+(icons.size()*48));
 }
 Map<Integer,String> itemNames(Object block,LegacyRegistryAnalyzer.Registration registration,Set<Integer> metas)throws Exception {
  Map<Integer,String> result=new TreeMap<>();String owner=registration.itemBlockClass();if(owner==null)return result;
  try {reset();Object item=makeInner("Obj",owner);invoke(analyzer,"invoke",owner,"<init>","(Lnet/minecraft/block/Block;)V",item,List.of(block),INVOKESPECIAL,0);
   Object method=invoke(analyzer,"find",owner,List.of("getUnlocalizedName","func_77667_c"),"(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;");
   if(method==null)return result;for(int meta:metas){reset();Object stack=makeInner("Obj","net/minecraft/item/ItemStack");Map<String,Object> f=(Map<String,Object>)field(stack,"fields");f.put("$metadata",meta);f.put("$item",item);Object key=invoke(analyzer,"run",method,item,List.of(stack),0);if(key instanceof String s)result.put(meta,s.endsWith(".name")?s:s+".name");}
  }catch(Exception ex){System.out.println("[LFB visual322] NAME_PROBE "+registration.registryName()+" "+ex);}
  return result;
 }
 void copyName(String key,String target)throws Exception {
  for(String exact:entries.values())if(exact.startsWith("assets/")&&exact.contains("/lang/")&&exact.endsWith(".lang")){
   String lang=exact.substring(exact.lastIndexOf('/')+1,exact.length()-5).toLowerCase(Locale.ROOT);String out=exact.substring(0,exact.indexOf("/lang/"))+"/lang/"+lang+".json";
   String text=new String(jar.getInputStream(jar.getJarEntry(exact)).readAllBytes(),StandardCharsets.UTF_8);
   for(String line:text.split("\\R")){int eq=line.indexOf('=');if(eq>0&&line.substring(0,eq).trim().equals(key)){JsonObject translations=read(out);translations.addProperty(target,line.substring(eq+1).trim());write(out,translations);break;}}
  }
 }
 static final String[] FACES={"down","up","north","south","west","east"};
 static JsonObject model(List<Draw> draws){JsonObject out=new JsonObject(),textures=new JsonObject();JsonArray elements=new JsonArray();out.addProperty("ambientocclusion",false);textures.addProperty("particle",draws.getFirst().icons()[0]);int index=0;
  for(Draw d:draws){String prefix="d"+(index++);for(int f=0;f<6;f++)textures.addProperty(prefix+f,d.icons()[f]);
   if(d.kind().equals("BOX")){double[] a=d.bounds();JsonObject el=new JsonObject(),faces=new JsonObject();el.add("from",G.toJsonTree(new double[]{16*a[0],16*a[1],16*a[2]}));el.add("to",G.toJsonTree(new double[]{16*a[3],16*a[4],16*a[5]}));for(int f=0;f<6;f++){int normal=f<2?1:f<4?2:0;boolean valid=true;for(int axis=0;axis<3;axis++)if(axis!=normal&&a[axis]==a[axis+3])valid=false;if(valid){JsonObject face=new JsonObject();face.addProperty("texture","#"+prefix+f);face.addProperty("tintindex",0);faces.add(FACES[f],face);}}el.add("faces",faces);elements.add(el);
   }else if(d.kind().equals("CROPS")){for(String axis:List.of("x","z"))for(double coord:new double[]{4,12}){JsonObject el=new JsonObject(),faces=new JsonObject();el.add("from",G.toJsonTree(axis.equals("x")?new double[]{coord,0,0}:new double[]{0,0,coord}));el.add("to",G.toJsonTree(axis.equals("x")?new double[]{coord,16,16}:new double[]{16,16,coord}));for(String face:axis.equals("x")?List.of("west","east"):List.of("north","south")){JsonObject fj=new JsonObject();fj.addProperty("texture","#"+prefix+0);fj.addProperty("tintindex",0);fj.add("uv",G.toJsonTree(new int[]{0,0,16,16}));faces.add(face,fj);}el.add("faces",faces);el.addProperty("shade",false);elements.add(el);}
   }else{for(int angle:new int[]{45,-45}){JsonObject el=new JsonObject(),rot=new JsonObject(),faces=new JsonObject();el.add("from",G.toJsonTree(new double[]{.8,0,8}));el.add("to",G.toJsonTree(new double[]{15.2,16,8}));rot.add("origin",G.toJsonTree(new int[]{8,8,8}));rot.addProperty("axis","y");rot.addProperty("angle",angle);rot.addProperty("rescale",false);el.add("rotation",rot);for(String face:List.of("north","south")){JsonObject fj=new JsonObject();fj.addProperty("texture","#"+prefix+0);fj.addProperty("tintindex",0);fj.add("uv",G.toJsonTree(new int[]{0,0,16,16}));faces.add(face,fj);}el.add("faces",faces);el.addProperty("shade",false);elements.add(el);}}
  }out.add("textures",textures);out.add("elements",elements);return out;
 }
 void gridPass()throws Exception {
  for(JsonElement e:content.getAsJsonArray("blocks")){JsonObject b=e.getAsJsonObject();String owner=b.get("sourceClass").getAsString();ClassNode base=null;
   for(int depth=0;depth<32&&classes.containsKey(owner);depth++){ClassNode c=classes.get(owner);if(c.fields.stream().anyMatch(f->f.desc.equals("[[[Lnet/minecraft/util/IIcon;"))&&method(c,"func_149673_e")!=null){base=c;break;}owner=c.superName;}
   if(base==null)continue;try {grid(b,base);}catch(Exception ex){obj(manifest,"exclusions").addProperty(b.get("id").getAsString(),ex.toString());System.out.println("[LFB visual322] GRID_EXCLUDED "+b.get("id")+" "+ex);}
  }
 }
 void grid(JsonObject b,ClassNode base)throws Exception {
  String id=b.get("id").getAsString();MethodNode getter=method(base,"func_149673_e"),reg=method(base,"func_149651_a");if(reg==null)throw new IllegalArgumentException("no source grid registration");
  String iconClass=null;int size=0;for(AbstractInsnNode i:reg.instructions)if(i instanceof MultiANewArrayInsnNode a&&a.dims==3){AbstractInsnNode t=i.getPrevious();while(t!=null&&t.getOpcode()<0)t=t.getPrevious();if(t==null||intValue(t)!=6)throw new IllegalArgumentException("grid face count");t=t.getPrevious();while(t!=null&&t.getOpcode()<0)t=t.getPrevious();size=intValue(t);AbstractInsnNode u=t.getPrevious();while(u!=null&&u.getOpcode()<0)u=u.getPrevious();if(intValue(u)!=size)throw new IllegalArgumentException("non-square grid");iconClass=a.desc.substring(4,a.desc.length()-1);}
  if(size!=4||iconClass==null)throw new IllegalArgumentException("unproved grid size");
  ClassNode icon=classes.get(iconClass);if(icon==null||method(icon,"func_94209_e")==null||method(icon,"func_94206_g")==null)throw new IllegalArgumentException("unproved source sub-icon");
  String name="dev/yinghuang/legacyforgebridge/generated/visual322/G"+sha(base.name.getBytes(StandardCharsets.UTF_8)).substring(0,20);String fieldName=null;
  for(AbstractInsnNode i:getter.instructions){if(i instanceof MethodInsnNode||i instanceof InvokeDynamicInsnNode||i.getOpcode()==NEW||i.getOpcode()==PUTFIELD||i.getOpcode()==PUTSTATIC)throw new IllegalArgumentException("non-pure grid selector");if(i instanceof FieldInsnNode f){if(!f.owner.equals(base.name)||!f.desc.equals("[[[Lnet/minecraft/util/IIcon;"))throw new IllegalArgumentException("unproved grid field");fieldName=f.name;}if(i instanceof JumpInsnNode j&&getter.instructions.indexOf(j.label)<getter.instructions.indexOf(i))throw new IllegalArgumentException("loop in grid selector");}
  if(fieldName==null)throw new IllegalArgumentException("no grid field");
  String parent=ABI+"$GridProgram";Map<String,String> mapping=Map.of(base.name,name,"net/minecraft/util/IIcon",ABI+"$GridIcon","net/minecraft/world/IBlockAccess",ABI+"$World");Remapper remap=new Remapper(){public String map(String n){return mapping.getOrDefault(n,n);}};
  ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){protected String getCommonSuperClass(String a,String c){return a.equals(c)?a:"java/lang/Object";}};cw.visit(V17,ACC_PUBLIC|ACC_FINAL,name,null,parent,null);cw.visitField(ACC_PUBLIC,fieldName,"[[[L"+ABI+"$GridIcon;",null,null).visitEnd();MethodVisitor ctor=cw.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();ctor.visitVarInsn(ALOAD,0);ctor.visitMethodInsn(INVOKESPECIAL,parent,"<init>","()V",false);ctor.visitVarInsn(ALOAD,0);ctor.visitInsn(ICONST_4);ctor.visitMethodInsn(INVOKESTATIC,ABI,"gridIcons","(I)[[[L"+ABI+"$GridIcon;",false);ctor.visitFieldInsn(PUTFIELD,name,fieldName,"[[[L"+ABI+"$GridIcon;");ctor.visitInsn(RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();getter.accept(new MethodRemapper(cw.visitMethod(ACC_PUBLIC,getter.name,remap.mapMethodDesc(getter.desc),null,null),remap));cw.visitEnd();byte[] binary=cw.toByteArray();
  GridProgram program=(GridProgram)new Loader().define(name,binary).getConstructor().newInstance();JsonArray table=new JsonArray();for(int x=0;x<size;x++)for(int y=0;y<size;y++)for(int z=0;z<size;z++){JsonArray cell=new JsonArray();for(int side=0;side<6;side++){GridIcon a=program.func_149673_e(null,x,y,z,side),negative=program.func_149673_e(null,x-size,y-size,z-size,side);if(a.u()<0||a.v()<0||a.u()>=size||a.v()>=size||a.side()!=side||!a.equals(negative))throw new IllegalArgumentException("invalid grid sample");cell.add(G.toJsonTree(new int[]{a.u(),a.v()}));}table.add(cell);}
  gridAssets(b,base);bytes(name+".class",binary);JsonObject r=new JsonObject();r.addProperty("size",size);r.addProperty("program",name.replace('/','.'));r.addProperty("programSha256",sha(binary));r.addProperty("sourceClass",base.name);r.addProperty("sourceIconClass",iconClass);r.add("selectorTable",table);obj(manifest,"grids").add(id,r);System.out.println("[LFB visual322] SOURCE_GRID "+id+" size="+size+" facesTested="+(size*size*size*6));
 }
 static int intValue(AbstractInsnNode n){if(n==null)return -1;if(n.getOpcode()>=ICONST_M1&&n.getOpcode()<=ICONST_5)return n.getOpcode()-ICONST_0;if(n instanceof IntInsnNode i)return i.operand;if(n instanceof LdcInsnNode l&&l.cst instanceof Integer i)return i;return -1;}
 public static void main(String[] args)throws Exception {if(args.length!=2)throw new IllegalArgumentException("source.jar staging-directory");apply(Path.of(args[0]).toAbsolutePath(),Path.of(args[1]).toAbsolutePath());}
 final Map<String,dev.yinghuang.legacyforgebridge.surface319.SurfaceProgram319> colorPrograms=new HashMap<>();
 int sourceColor(String id,int metadata)throws Exception {
  var p=colorPrograms.get(id);if(p==null){JsonObject r=read("legacyforgebridge/source-surfaces319.json").getAsJsonObject("rules").getAsJsonObject(id);String name=r.get("program").getAsString();Path f=stage.resolve(name.replace('.','/')+".class");if(!sha(Files.readAllBytes(f)).equals(r.get("programSha256").getAsString()))throw new IllegalArgumentException("source color program hash mismatch");try(var loader=new java.net.URLClassLoader(new java.net.URL[]{stage.toUri().toURL()},getClass().getClassLoader())){p=Class.forName(name,true,loader).asSubclass(dev.yinghuang.legacyforgebridge.surface319.SurfaceProgram319.class).getConstructor().newInstance();}colorPrograms.put(id,p);}
  return p.itemColor(metadata)&0xffffff;
 }
 void gridAssets(JsonObject b,ClassNode base)throws Exception {
  String id=b.get("id").getAsString(),owner=b.get("sourceClass").getAsString(),ns=id.split(":",2)[0],path=id.split(":",2)[1];
  var registration=analysis.blocks().stream().filter(r->r.registryName().equals(b.get("legacyRegistryName").getAsString())).findFirst().orElseThrow();FieldInsnNode sourceBase=null;ClassNode sourceClass=classes.get(owner);for(MethodNode ctor:sourceClass.methods)if(ctor.name.equals("<init>"))for(AbstractInsnNode i:ctor.instructions)if(i instanceof MethodInsnNode call&&call.getOpcode()==INVOKESPECIAL&&call.owner.equals(base.name)&&call.desc.equals("(Lnet/minecraft/block/Block;)V")){AbstractInsnNode prev=i.getPrevious();while(prev!=null&&prev.getOpcode()<0)prev=prev.getPrevious();if(!(prev instanceof FieldInsnNode f)||f.getOpcode()!=GETSTATIC||!f.owner.equals("net/minecraft/init/Blocks"))throw new IllegalArgumentException("base block is not a proven vanilla field");if(sourceBase!=null&&!sourceBase.name.equals(f.name))throw new IllegalArgumentException("ambiguous base block");sourceBase=f;}
  if(sourceBase==null)throw new IllegalArgumentException("no proven base block constructor argument");Object block=makeInner("Symbol",sourceBase.owner,sourceBase.name);
  List<String> faceIcons=new ArrayList<>();for(int f=0;f<6;f++)faceIcons.add((String)invoke(analyzer,"vanillaBlockIcon",block,f,0));
  Map<String,Number> constants=Map.of();
  var facts=new dev.yinghuang.legacyforgebridge.convert.rev319.SurfaceFacts319(registration.registryName(),owner,"net/minecraft/block/Block",null,constants,List.of(),List.of(0d,0d,0d,1d,1d,1d),Map.of(),"");
  String program="dev/yinghuang/legacyforgebridge/generated/surface319/P"+sha((id+"-gridcolors322").getBytes(StandardCharsets.UTF_8)).substring(0,20);byte[] binary=new dev.yinghuang.legacyforgebridge.convert.rev319.SurfaceCompiler319(classes,facts,program).compile(null);bytes(program+".class",binary);
  var colors=(dev.yinghuang.legacyforgebridge.surface319.SurfaceProgram319)new Loader().define(program,binary).getConstructor().newInstance();int rgb=colors.itemColor(0);
  boolean nonOpaque=false;MethodNode opacity=method(sourceClass,"func_149662_c");if(opacity!=null){List<AbstractInsnNode> ins=new ArrayList<>();for(AbstractInsnNode i:opacity.instructions)if(i.getOpcode()>=0)ins.add(i);if(ins.size()!=2||ins.get(1).getOpcode()!=IRETURN)throw new IllegalArgumentException("unproved source opacity");nonOpaque=intValue(ins.get(0))==0;}
  JsonObject surfaces=read("legacyforgebridge/source-surfaces319.json"),rule=new JsonObject();rule.addProperty("id",id);rule.addProperty("program",program.replace('/','.'));rule.addProperty("programSha256",sha(binary));rule.addProperty("sourceClass",owner);rule.addProperty("sourceRenderer","platform:CUBE");rule.addProperty("worldStates",16);rule.addProperty("cutout",nonOpaque);rule.addProperty("nonOpaque",nonOpaque);rule.addProperty("uv",false);rule.add("textures",G.toJsonTree(faceIcons));obj(surfaces,"rules").add(id,rule);write("legacyforgebridge/source-surfaces319.json",surfaces);
  JsonObject model=model(List.of(new Draw("BOX",new double[]{0,0,0,1,1,1},faceIcons.toArray(String[]::new))));model.addProperty("parent","minecraft:block/block");model.addProperty("ambientocclusion",true);for(var el:model.getAsJsonArray("elements"))for(var f:el.getAsJsonObject().getAsJsonObject("faces").entrySet())f.getValue().getAsJsonObject().addProperty("cullface",f.getKey());
  write("assets/"+ns+"/models/block/"+path+".json",model);for(int m=0;m<16;m++)write("assets/"+ns+"/models/block/"+path+"_lfb_batch_"+m+".json",model);
  JsonObject im=read("assets/"+ns+"/items/"+path+".json"),leaf=im.getAsJsonObject("model");JsonArray tints=new JsonArray();JsonObject t=new JsonObject();t.addProperty("type","minecraft:constant");t.addProperty("value",rgb);tints.add(t);leaf.add("tints",tints);write("assets/"+ns+"/items/"+path+".json",im);
  System.out.println("[LFB visual322] GRID_SOURCE_MATERIALS "+id+" faces="+faceIcons+" sourceItemColor="+rgb+" nonOpaque="+nonOpaque);
 }
}
