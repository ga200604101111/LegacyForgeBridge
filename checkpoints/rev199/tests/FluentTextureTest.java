import dev.yinghuang.legacyforgebridge.convert.LegacyFluentTextureAnalyzer;
import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.function.Consumer;
import static org.objectweb.asm.Opcodes.*;

/** Synthetic bytecode fixtures only. No Minecraft class is defined or executed. */
public final class FluentTextureTest {
    static final String ITEM="net/minecraft/item/Item",BLOCK="net/minecraft/block/Block",H="fixture/Holder",C="fixture/Content";
    static final String ID="L"+ITEM+";",BD="L"+BLOCK+";";
    static int assertions;
    static class Fixture {
        final Map<String,byte[]> entries=new LinkedHashMap<>();
        final ClassWriter holder=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        final MethodVisitor m;
        final boolean block;
        Fixture(boolean block){
            this.block=block;holder.visit(V1_7,ACC_PUBLIC,H,null,"java/lang/Object",null);
            holder.visitField(ACC_PUBLIC|ACC_STATIC,"unrelatedField",block?BD:ID,null,null).visitEnd();
            m=holder.visitMethod(ACC_PUBLIC|ACC_STATIC,"lifecycle","()V",null,null);m.visitCode();
        }
        void begin(String type){m.visitTypeInsn(NEW,type);m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,type,"<init>","()V",false);}
        void texture(String value,String name){m.visitLdcInsn(value);m.visitMethodInsn(INVOKEVIRTUAL,block?BLOCK:ITEM,name,"(Ljava/lang/String;)"+(block?BD:ID),false);}
        void store(){m.visitFieldInsn(PUTSTATIC,H,"unrelatedField",block?BD:ID);}
        Path finish()throws Exception{
            m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();holder.visitEnd();entries.put(H+".class",holder.toByteArray());
            entries.putIfAbsent("assets/another_mod/textures/"+(block?"blocks/":"items/")+"actual_icon.png",new byte[]{1});
            Path p=Files.createTempFile("lfb-texture-test-",".jar");
            try(JarOutputStream jar=new JarOutputStream(Files.newOutputStream(p))){for(var e:entries.entrySet()){jar.putNextEntry(new JarEntry(e.getKey()));jar.write(e.getValue());jar.closeEntry();}}
            return p;
        }
    }
    static byte[] child(String name,String base,String override,String desc){
        ClassWriter c=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.visit(V1_7,ACC_PUBLIC,name,null,base,null);
        // This constructor must never be run; evidence is deliberately independent of behavior.
        MethodVisitor init=c.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();
        init.visitTypeInsn(NEW,"java/lang/AssertionError");init.visitInsn(DUP);
        init.visitMethodInsn(INVOKESPECIAL,"java/lang/AssertionError","<init>","()V",false);init.visitInsn(ATHROW);init.visitMaxs(0,0);init.visitEnd();
        if(override!=null){MethodVisitor v=c.visitMethod(ACC_PUBLIC,override,desc,null,null);v.visitCode();v.visitInsn(ACONST_NULL);v.visitInsn(ARETURN);v.visitMaxs(0,0);v.visitEnd();}
        c.visitEnd();return c.toByteArray();
    }
    static LegacyFluentTextureAnalyzer.Result run(Fixture f)throws Exception{
        Path p=f.finish();try{return new LegacyFluentTextureAnalyzer().analyze(p);}finally{Files.delete(p);}
    }
    static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    static void accept(Fixture f,String texture)throws Exception{
        var r=run(f);check(r.evidence().size()==1,"Expected evidence: "+r.exclusions());check(r.exclusions().isEmpty(),"Unexpected exclusion");
        check(r.evidence().getFirst().texture().equals(texture),"Wrong texture identity");
        check(r.evidence().getFirst().field().name().equals("unrelatedField"),"Wrong field identity");
    }
    static void reject(Fixture f,String contains)throws Exception{
        var r=run(f);check(r.evidence().isEmpty(),"Unsafe evidence admitted");check(!r.exclusions().isEmpty(),"No exclusion reason");
        check(r.exclusions().getFirst().reason().contains(contains),"Unexpected reason: "+r.exclusions());
    }
    static Fixture simple(String texture){Fixture f=new Fixture(false);f.begin(ITEM);f.texture(texture,"setTextureName");f.store();return f;}
    public static void main(String[] args)throws Exception{
        accept(simple("another_mod:actual_icon"),"another_mod:actual_icon");
        Fixture f=new Fixture(false);f.begin(ITEM);f.texture("another_mod:actual_icon","func_111206_d");f.store();accept(f,"another_mod:actual_icon");
        f=new Fixture(true);f.begin(BLOCK);f.texture("another_mod:actual_icon","func_149658_d");f.store();accept(f,"another_mod:actual_icon");
        f=new Fixture(false);f.entries.put(C+".class",child(C,ITEM+"Sword",null,null));f.begin(C);f.texture("another_mod:actual_icon","setTextureName");f.store();accept(f,"another_mod:actual_icon");
        f=new Fixture(false);f.begin(ITEM);f.texture("another_mod:first","setTextureName");f.texture("another_mod:actual_icon","setTextureName");f.store();accept(f,"another_mod:actual_icon");
        f=new Fixture(false);f.begin(ITEM);f.m.visitLdcInsn("name_is_not_texture");f.m.visitMethodInsn(INVOKEVIRTUAL,ITEM,"setUnlocalizedName","(Ljava/lang/String;)"+ID,false);
        f.texture("another_mod:actual_icon","setTextureName");f.m.visitFieldInsn(GETSTATIC,"fixture/UnknownState","tab","Lnet/minecraft/creativetab/CreativeTabs;");f.m.visitMethodInsn(INVOKEVIRTUAL,ITEM,"setCreativeTab","(Lnet/minecraft/creativetab/CreativeTabs;)"+ID,false);f.store();accept(f,"another_mod:actual_icon");
        var missing=run(simple("another_mod:missing"));check(missing.evidence().size()==1&&!missing.evidence().getFirst().resourcePresent(),"Missing PNG must not be advertised present");
        f=new Fixture(false);f.begin(ITEM);f.texture("another_mod:actual_icon","setTextureName");f.m.visitTypeInsn(CHECKCAST,ITEM);f.store();accept(f,"another_mod:actual_icon");
        f=new Fixture(false);f.begin(ITEM);f.m.visitLdcInsn("another_mod:actual_icon");f.m.visitMethodInsn(INVOKEVIRTUAL,ITEM,"setUnlocalizedName","(Ljava/lang/String;)"+ID,false);f.store();reject(f,"no literal texture");
        f=new Fixture(false);f.begin(ITEM);f.m.visitFieldInsn(GETSTATIC,H,"unknownText","Ljava/lang/String;");f.m.visitMethodInsn(INVOKEVIRTUAL,ITEM,"setTextureName","(Ljava/lang/String;)"+ID,false);f.store();reject(f,"literal string");
        f=new Fixture(false);f.begin(ITEM);f.texture("another_mod:actual_icon","unknownFluent");f.store();reject(f,"unknown or overridden");
        f=new Fixture(false);f.entries.put(C+".class",child(C,ITEM,"setTextureName","(Ljava/lang/String;)"+ID));f.begin(C);f.texture("another_mod:actual_icon","setTextureName");f.store();reject(f,"overridden");
        f=new Fixture(false);f.entries.put("fixture/Parent.class",child("fixture/Parent",ITEM,"setTextureName","(Ljava/lang/String;)"+ID));f.entries.put(C+".class",child(C,"fixture/Parent",null,null));f.begin(C);f.texture("another_mod:actual_icon","setTextureName");f.store();reject(f,"overridden");
        f=new Fixture(false);f.entries.put(C+".class",child(C,"fixture/MissingParent",null,null));f.begin(C);f.texture("another_mod:actual_icon","setTextureName");f.store();reject(f,"ancestry");
        f=simple("another_mod:actual_icon");f.begin(ITEM);f.texture("another_mod:actual_icon","setTextureName");f.store();reject(f,"multiple static writers");
        f=new Fixture(false);f.begin(ITEM);f.m.visitVarInsn(ASTORE,0);f.m.visitVarInsn(ALOAD,0);f.texture("another_mod:actual_icon","setTextureName");f.store();reject(f,"alias");
        f=new Fixture(false);f.begin(ITEM);f.texture("another_mod:actual_icon","setTextureName");f.m.visitMethodInsn(INVOKESTATIC,"fixture/Other","sideEffect","()V",false);f.store();reject(f,"extra call");
        f=new Fixture(false);f.begin(ITEM);f.texture("another_mod:actual_icon","setTextureName");Label l=new Label();f.m.visitJumpInsn(GOTO,l);f.m.visitLabel(l);f.store();reject(f,"control flow");
        f=new Fixture(false);f.begin(ITEM);f.m.visitLdcInsn("another_mod:actual_icon");f.m.visitMethodInsn(INVOKEVIRTUAL,"fixture/Unrelated","setTextureName","(Ljava/lang/String;)"+ID,false);f.store();reject(f,"overridden fluent");
        f=new Fixture(false);f.m.visitTypeInsn(NEW,ITEM);f.texture("another_mod:actual_icon","setTextureName");f.store();reject(f,"constructor");
        f=new Fixture(false);f.begin(BLOCK);f.texture("another_mod:actual_icon","setTextureName");f.store();reject(f,"fluent");
        for(String unsafe:List.of("MOD:icon","mod:../icon","mod:/icon","mod:one//two","mod:one/./two","mod:icon/","mod:icon\\other","mod:icon:extra"))reject(simple(unsafe),unsafe.contains("MOD")||unsafe.contains("\\")||unsafe.endsWith(":extra")?"noncanonical":"unsafe");
        f=new Fixture(false);f.m.visitFieldInsn(GETSTATIC,H,"other",ID);f.texture("another_mod:actual_icon","setTextureName");f.store();reject(f,"unsupported allocation");
        // Presentation overrides do not erase setter evidence, but still require a separate gate.
        f=new Fixture(false);f.entries.put(C+".class",child(C,ITEM,"getIconFromDamage","(I)Lnet/minecraft/util/IIcon;"));f.begin(C);f.texture("another_mod:actual_icon","setTextureName");f.store();accept(f,"another_mod:actual_icon");
        var analyzer=new LegacyFluentTextureAnalyzer();Path a=simple("another_mod:actual_icon").finish(),b=simple("another_mod:missing").finish();
        try{check(analyzer.analyze(a).evidence().getFirst().resourcePresent(),"first resource");check(!analyzer.analyze(b).evidence().getFirst().resourcePresent(),"stale resource state");}finally{Files.delete(a);Files.delete(b);}
        System.out.println("PASS assertions="+assertions);
    }
}
