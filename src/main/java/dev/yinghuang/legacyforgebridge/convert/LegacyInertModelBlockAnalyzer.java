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
 * Fail-closed analyzer for inert legacy BlockContainer instances whose TileEntity exists only to
 * host a client renderer. Gameplay/stateful BlockEntities are deliberately excluded.
 */
public final class LegacyInertModelBlockAnalyzer {
    private static final String BLOCK_CONTAINER = "net/minecraft/block/BlockContainer";
    private static final String TILE_ENTITY = "net/minecraft/tileentity/TileEntity";
    public static final String ORIENTATION_PLAYER_YAW_QUADRANT = "player_yaw_quadrant_0_3";

    public record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ) {
        public Bounds {
            for (float value : new float[]{minX,minY,minZ,maxX,maxY,maxZ})
                if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite block bounds");
            if (minX<0F||minY<0F||minZ<0F||maxX>1F||maxY>1F||maxZ>1F
                    ||minX>=maxX||minY>=maxY||minZ>=maxZ)
                throw new IllegalArgumentException("Invalid legacy block bounds");
        }
    }
    public record Rule(String registryName,String legacyNamespace,String sourceBlockClass,
                       String sourceTileClass,String legacyTileId,Bounds bounds,float sourceLightLevel,
                       int modernLightEmission,String orientation,boolean nonOpaque,
                       boolean nonNormalRender,int renderPass) { }
    public record Skipped(String registryName,String sourceBlockClass,String reason) { }
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules); skipped=List.copyOf(skipped); diagnostics=List.copyOf(diagnostics); }
    }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String,ClassNode> classes=loadClasses(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        var lifecycle=new LegacyLifecycleAnalyzer().analyze(jarPath);
        Map<String,String> tileIds=registeredTiles(lifecycle);
        List<Rule> rules=new ArrayList<>();
        List<Skipped> skipped=new ArrayList<>();
        List<String> diagnostics=new ArrayList<>();
        diagnostics.addAll(registry.diagnostics()); diagnostics.addAll(lifecycle.diagnostics());

        for (var registration:registry.blocks()) {
            String blockClass=registration.implementationClass();
            if(blockClass==null||!inherits(classes,blockClass,BLOCK_CONTAINER)) continue;
            MethodNode create=method(classes,blockClass,Set.of("createNewTileEntity","func_149915_a"),
                    "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;");
            String tileClass=uniqueCreatedType(create);
            if(tileClass==null||!tileIds.containsKey(tileClass)) continue;
            ClassNode tile=classes.get(tileClass);
            if(tile==null||!inherits(classes,tileClass,TILE_ENTITY)) continue;
            if(!inertTile(tile)) continue;

            ClassNode block=classes.get(blockClass);
            MethodNode ctor=method(classes,blockClass,Set.of("<init>"),"()V");
            Bounds bounds=sourceBounds(ctor);
            Float light=sourceLightLevel(ctor);
            if(bounds==null||light==null||light<0F||light>1F){
                skipped.add(skip(registration,blockClass,"inert renderer block has no unique bounded shape/light source contract"));
                continue;
            }
            if(!Boolean.FALSE.equals(returnedBoolean(ownMethod(block,Set.of("isOpaqueCube","func_149662_c"),"()Z")))
                    ||!Boolean.FALSE.equals(returnedBoolean(ownMethod(block,Set.of("renderAsNormalBlock","func_149686_d"),"()Z")))){
                skipped.add(skip(registration,blockClass,"inert renderer block is not proven non-opaque and non-normal"));
                continue;
            }
            Integer pass=returnedInt(ownMethod(block,Set.of("getRenderBlockPass","func_149656_h"),"()I"));
            if(pass==null||pass<0||pass>1){
                skipped.add(skip(registration,blockClass,"legacy render pass is not the admitted 0/1 domain"));
                continue;
            }
            MethodNode placed=method(classes,blockClass,Set.of("onBlockPlacedBy","func_149689_a"),
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V");
            if(!directYawPlacement(placed)){
                skipped.add(skip(registration,blockClass,"placement metadata is not the direct source yaw-quadrant 0..3 mapping"));
                continue;
            }
            int emission=Math.max(0,Math.min(15,(int)(15F*light)));
            rules.add(new Rule(registration.registryName(),registration.legacyNamespace(),blockClass,tileClass,
                    tileIds.get(tileClass),bounds,light,emission,ORIENTATION_PLAYER_YAW_QUADRANT,true,true,pass));
        }
        return new Analysis(rules,skipped,List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static boolean inertTile(ClassNode tile){
        if(!tile.fields.isEmpty()) return false;
        for(MethodNode method:tile.methods){
            if(method.name.equals("<init>")&&method.desc.equals("()V")) continue;
            if((method.name.equals("canUpdate")||method.name.equals("func_145842_c"))&&method.desc.equals("()Z")
                    &&Boolean.FALSE.equals(returnedBoolean(method))) continue;
            return false;
        }
        return true;
    }

    private static Bounds sourceBounds(MethodNode ctor){
        if(ctor==null)return null;
        List<AbstractInsnNode> code=real(ctor); Bounds result=null;
        for(int i=6;i<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEVIRTUAL
                    ||!(call.name.equals("setBlockBounds")||call.name.equals("func_149676_a"))
                    ||!call.desc.equals("(FFFFFF)V"))continue;
            float[] v=new float[6]; boolean ok=true;
            for(int a=0;a<6;a++){Float value=floatConstant(code.get(i-6+a));if(value==null){ok=false;break;}v[a]=value;}
            if(!ok)continue;
            Bounds found;
            try{found=new Bounds(v[0],v[1],v[2],v[3],v[4],v[5]);}catch(IllegalArgumentException invalid){continue;}
            if(result!=null&&!result.equals(found))return null; result=found;
        }
        return result;
    }
    private static Float sourceLightLevel(MethodNode ctor){
        if(ctor==null)return null; List<AbstractInsnNode> code=real(ctor); Float result=null;
        for(int i=1;i<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEVIRTUAL
                    ||!(call.name.equals("setLightLevel")||call.name.equals("func_149715_a"))
                    ||!call.desc.equals("(F)Lnet/minecraft/block/Block;"))continue;
            Float value=floatConstant(code.get(i-1)); if(value==null)return null;
            if(result!=null&&Float.compare(result,value)!=0)return null; result=value;
        }
        return result;
    }

    private static boolean directYawPlacement(MethodNode method){
        if(method==null)return false; boolean yaw=false,four=false,threeSixty=false,half=false,floor=false,mask=false,set=false,notify=false;
        List<AbstractInsnNode> code=real(method);
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD
                    &&field.owner.equals("net/minecraft/entity/EntityLivingBase")
                    &&field.desc.equals("F")&&(field.name.equals("rotationYaw")||field.name.equals("field_70177_z")))yaw=true;
            Float f=floatConstant(insn); if(f!=null&&Float.compare(f,4F)==0)four=true; if(f!=null&&Float.compare(f,360F)==0)threeSixty=true;
            if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d&&Double.compare(d,0.5D)==0)half=true;
            if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC
                    &&call.owner.equals("net/minecraft/util/MathHelper")
                    &&(call.name.equals("floor_double")||call.name.equals("func_76128_c"))&&call.desc.equals("(D)I"))floor=true;
            if(insn.getOpcode()==Opcodes.IAND&&i>0&&Integer.valueOf(3).equals(intConstant(code.get(i-1))))mask=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/world/World")
                    &&(call.name.equals("setBlockMetadataWithNotify")||call.name.equals("func_72921_c"))
                    &&call.desc.equals("(IIIII)Z")){set=true;if(i>0&&Integer.valueOf(3).equals(intConstant(code.get(i-1))))notify=true;}
        }
        return yaw&&four&&threeSixty&&half&&floor&&mask&&set&&notify;
    }

    private static Skipped skip(LegacyRegistryAnalyzer.Registration r,String owner,String reason){return new Skipped(r.registryName(),owner,reason);}
    private static Map<String,String> registeredTiles(LegacyLifecycleAnalyzer.Analysis lifecycle){
        Map<String,String> result=new LinkedHashMap<>();
        for(var registration:lifecycle.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY)){
            if(registration.arguments().size()<2)continue;
            if(registration.arguments().get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type
                    &&registration.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue text)
                result.put(type.internalName(),text.value());
        }
        return result;
    }
    private static String uniqueCreatedType(MethodNode method){
        if(method==null)return null; Set<String> created=new LinkedHashSet<>();
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW)created.add(type.desc);
        return created.size()==1?created.iterator().next():null;
    }
    private static MethodNode method(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){
        Set<String> seen=new HashSet<>(); while(owner!=null&&seen.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;MethodNode own=ownMethod(node,names,desc);if(own!=null)return own;owner=node.superName;}return null;
    }
    private static MethodNode ownMethod(ClassNode node,Set<String> names,String desc){if(node==null)return null;for(MethodNode method:node.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){if(owner.equals(target))return true;ClassNode node=classes.get(owner);owner=node==null?null:node.superName;}return false;}
    private static Boolean returnedBoolean(MethodNode method){Integer value=returnedInt(method);return value==null||value<0||value>1?null:value==1;}
    private static Integer returnedInt(MethodNode method){
        if(method==null)return null; Integer result=null; List<AbstractInsnNode> code=real(method);
        for(int i=1;i<code.size();i++)if(code.get(i).getOpcode()==Opcodes.IRETURN){Integer value=intConstant(code.get(i-1));if(value==null||result!=null&&!result.equals(value))return null;result=value;}return result;
    }
    private static Integer intConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){
        case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;
        case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;
        case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};}
    private static Float floatConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){
        case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;
        case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value?value:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> result=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)result.add(insn);return result;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;
    }
}
