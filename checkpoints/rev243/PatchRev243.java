import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Guarded incremental patch. New mixins only observe and use require=0. */
public final class PatchRev243 {
    static final String ROOT="dev/yinghuang/legacyforgebridge/", B=ROOT+"behavior/";
    static final String VERSION="0.2.0-alpha.27-corpus4-local.27-rev243-diagnostic.1";
    static final String REVISION="2026-10-02.243-observation-only-motion-diagnostics";
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("base.jar outputClasses");
        Path output=Path.of(args[1]);
        try(ZipFile z=new ZipFile(args[0])) {
            String path=ROOT+"BuildInfo.class";ClassNode n=read(z,path);int versions=0,revisions=0;
            for(MethodNode m:n.methods)for(AbstractInsnNode ins:m.instructions)if(ins instanceof LdcInsnNode ldc) {
                if("0.2.0-alpha.27-corpus4-local.26-rev242-local-test.1".equals(ldc.cst)){ldc.cst=VERSION;versions++;}
                if("2026-10-02.242-stationary-descent-and-source-landing".equals(ldc.cst)){ldc.cst=REVISION;revisions++;}
            }
            if(versions!=1||revisions!=1)throw new IllegalStateException("wrong BuildInfo baseline");
            write(output.resolve(path),n);
            path=B+"LegacyClientJumpMotion.class";n=read(z,path);int messages=0,labels=0;
            for(MethodNode m:n.methods)for(AbstractInsnNode ins:m.instructions) {
                if(ins instanceof LdcInsnNode l&&l.cst instanceof String s&&s.startsWith("LFB source motion compatibility READY rev242")) {
                    l.cst="LFB source motion diagnostics READY rev243; observation only; all historical Y reconciliation disabled; source jump and rev242 landing effects retained; old jumpEcho={} ignored";messages++;
                }
                if(m.name.equals("velocityTail")&&ins instanceof InvokeDynamicInsnNode d)for(int i=0;i<d.bsmArgs.length;i++)
                    if(d.bsmArgs[i] instanceof String s&&s.contains("action=POST_RECONCILE_OBSERVATION")) {
                        d.bsmArgs[i]=s.replace("action=POST_RECONCILE_OBSERVATION","action=OBSERVE_ONLY_REV243");labels++;
                    }
            }
            if(messages!=1||labels!=1)throw new IllegalStateException("wrong trace-label baseline");
            write(output.resolve(path),n);
            // Source particle queue only; do not alter the renderer or spawn a particle twice.
            path=B+"LegacyBehaviorApi$World.class";n=read(z,path);int particles=0;
            for(MethodNode m:n.methods)if(m.name.equals("func_72869_a")&&m.desc.equals("(Ljava/lang/String;DDDDDD)V"))
                for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==Opcodes.RETURN) {
                    InsnList list=new InsnList();list.add(new VarInsnNode(Opcodes.ALOAD,1));
                    for(int i=2;i<=12;i+=2)list.add(new VarInsnNode(Opcodes.DLOAD,i));
                    list.add(new MethodInsnNode(Opcodes.INVOKESTATIC,B+"LegacyMotionTraceLog","sourceParticle","(Ljava/lang/String;DDDDDD)V",false));
                    m.instructions.insertBefore(ins,list);particles++;
                }
            if(particles!=1)throw new IllegalStateException("wrong source particle queue baseline");
            write(output.resolve(path),n);
            // Repair stale header literals only; keep the ordinary logger implementation.
            for(String p:new String[]{ROOT+"LegacyFileLogger.class",ROOT+"network/FmlConnectionTrace.class"}) {
                n=read(z,p);int replaced=0;
                for(MethodNode m:n.methods)for(AbstractInsnNode ins:m.instructions) {
                    if(ins instanceof LdcInsnNode l&&l.cst instanceof String s){String t=header(s);if(!s.equals(t)){l.cst=t;replaced++;}}
                    if(ins instanceof InvokeDynamicInsnNode d)for(int i=0;i<d.bsmArgs.length;i++)if(d.bsmArgs[i] instanceof String s){String t=header(s);if(!s.equals(t)){d.bsmArgs[i]=t;replaced++;}}
                }
                if(replaced==0)throw new IllegalStateException("expected stale header in "+p);
                write(output.resolve(p),n);System.out.println("HEADER_PATCH "+p+" literals="+replaced);
            }
            cameraMixin(output);moveMixin(output);
            System.out.println("PATCH_PASS version=1 revision=1 readiness=1 observationLabel=1 particleQueueHook=1 newObserverMixins=2");
        }
    }
    static String header(String s){return s.replace("0.2.0-alpha.27-corpus4-local.6-rev220-local-test.1",VERSION)
            .replace("2026-09-29.222-corpus4-presentation-projectile-blockstate",REVISION);}
    static ClassNode read(ZipFile z,String p)throws Exception {ClassNode n=new ClassNode();try(var in=z.getInputStream(z.getEntry(p))){new ClassReader(in.readAllBytes()).accept(n,0);}return n;}
    static void write(Path p,ClassNode n)throws Exception {ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
    static ClassWriter mixin(String name,String target) {
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(65,Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,name,null,"java/lang/Object",null);
        w.visitSource(name.substring(name.lastIndexOf('/')+1)+".java",null);
        AnnotationVisitor a=w.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;",false);
        AnnotationVisitor v=a.visitArray("value");v.visit(null,Type.getObjectType(target));v.visitEnd();a.visit("remap",false);a.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        return w;
    }
    static MethodVisitor inject(ClassWriter w,String method,String desc,String target,String point) {
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PRIVATE,method,desc,null,null);
        AnnotationVisitor a=m.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;",true);
        AnnotationVisitor arr=a.visitArray("method");arr.visit(null,target);arr.visitEnd();
        arr=a.visitArray("at");AnnotationVisitor at=arr.visitAnnotation(null,"Lorg/spongepowered/asm/mixin/injection/At;");at.visit("value",point);at.visitEnd();arr.visitEnd();
        a.visit("remap",false);a.visit("require",0);a.visit("cancellable",false);a.visitEnd();m.visitCode();return m;
    }
    static final String CI="Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    static void cameraMixin(Path out)throws Exception {
        String name=ROOT+"mixin/diagnostic/Rev243CameraTraceMixin";ClassWriter w=mixin(name,"net/minecraft/class_4184");
        MethodVisitor m=inject(w,"lfb$rev243Camera","("+CI+")V","method_19321(Lnet/minecraft/class_1937;Lnet/minecraft/class_1297;ZZF)V","RETURN");
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESTATIC,B+"Rev243Diagnostics","camera","(Ljava/lang/Object;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();save(out,name,w);
    }
    static void moveMixin(Path out)throws Exception {
        String name=ROOT+"mixin/diagnostic/Rev243MoveTraceMixin";ClassWriter w=mixin(name,"net/minecraft/class_1297");
        for(String phase:new String[]{"Head","Tail"}) {
            MethodVisitor m=inject(w,"lfb$rev243Move"+phase,"(Lnet/minecraft/class_1313;Lnet/minecraft/class_243;"+CI+")V",
                    "method_5784(Lnet/minecraft/class_1313;Lnet/minecraft/class_243;)V",phase.equals("Head")?"HEAD":"RETURN");
            for(int i=0;i<3;i++)m.visitVarInsn(Opcodes.ALOAD,i);
            m.visitMethodInsn(Opcodes.INVOKESTATIC,B+"Rev243Diagnostics","move"+phase,"(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false);
            m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        }
        w.visitEnd();save(out,name,w);
    }
    static void save(Path out,String name,ClassWriter w)throws Exception{Path p=out.resolve(name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
}
