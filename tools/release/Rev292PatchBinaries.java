import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Audited rev291 -> rev292 profile/version deltas, exact pass injection. */
public final class Rev292PatchBinaries {
    static final String ROOT="dev/yinghuang/legacyforgebridge/convert/";
    static final String PASS=ROOT+"pass/";
    static final String PROFILE=ROOT+"profile/GenericLegacyModProfile.class";
    static final String BUILDER=ROOT+"api/ConversionPlan$Builder";
    static final String ADD_DESC="(L"+ROOT+"api/ConversionPass;)L"+BUILDER+";";
    static final String BUILD="dev/yinghuang/legacyforgebridge/BuildInfo.class";
    static byte[] patchProfile(byte[] bytes){
        var node=new ClassNode(Opcodes.ASM9);new ClassReader(bytes).accept(node,0);
        var method=node.methods.stream().filter(m->m.name.equals("configure")&&m.desc.equals("(L"+BUILDER+";)V"))
                .findFirst().orElseThrow();
        AbstractInsnNode anchor=null;int count=0;boolean target=false;
        for(AbstractInsnNode n:method.instructions) {
            if(n instanceof TypeInsnNode type && n.getOpcode()==Opcodes.NEW) {
                if(type.desc.equals(PASS+"LegacyStaticTileModelBakerPass")) {target=true;count++;}
                if(type.desc.equals(PASS+"LegacyBatchVisualRecoveryPass")) throw new IllegalStateException("Already patched");
            }
            if(target && n instanceof MethodInsnNode call && call.owner.equals(BUILDER)
                    && call.name.equals("add") && call.desc.equals(ADD_DESC)) {
                if(n.getNext().getOpcode()!=Opcodes.POP)throw new IllegalStateException("Invalid profile anchor");
                anchor=n.getNext();target=false;
            }
        }
        if(count!=1||anchor==null) throw new IllegalStateException("Expected one old source model baker");
        InsnList insert=new InsnList();
        insert.add(new VarInsnNode(Opcodes.ALOAD,1));
        insert.add(new TypeInsnNode(Opcodes.NEW,PASS+"LegacyBatchVisualRecoveryPass"));
        insert.add(new InsnNode(Opcodes.DUP));
        insert.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,PASS+"LegacyBatchVisualRecoveryPass","<init>","()V",false));
        insert.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,BUILDER,"add",ADD_DESC,false));
        insert.add(new InsnNode(Opcodes.POP));
        method.instructions.insert(anchor,insert);
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    static byte[] patchBuild(byte[] bytes){
        var node=new ClassNode(Opcodes.ASM9);new ClassReader(bytes).accept(node,0);
        var init=node.methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElseThrow();
        int updated=0;
        for(AbstractInsnNode n:init.instructions) if(n instanceof LdcInsnNode ldc && ldc.cst instanceof String value) {
            if(value.equals("0.2.0-alpha.27-corpus4-local.44-rev291-static-tesr-preview.1")) {
                ldc.cst="0.2.0-alpha.27-corpus4-local.45-rev292-batch-visual-prism-watchdog-preview.1";updated++;
            } else if(value.equals("2026-10-08.291-static-tesr-source-model-preview")) {
                ldc.cst="2026-10-08.292-batch-source-visual-prism-watchdog";updated++;
            }
        }
        if(updated!=2)throw new IllegalStateException("Unexpected BuildInfo version/revision literals count="+updated);
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        Path old=Path.of(args[0]),root=Path.of(args[1]);
        try(JarFile jar=new JarFile(old.toFile())){
            for(var entry:Map.of(PROFILE,0,BUILD,1).entrySet()){
                byte[] original=jar.getInputStream(jar.getJarEntry(entry.getKey())).readAllBytes();
                byte[] patched=entry.getValue()==0?patchProfile(original):patchBuild(original);
                Path target=root.resolve(entry.getKey());Files.createDirectories(target.getParent());Files.write(target,patched);
                System.out.println(entry.getKey()+" before="+original.length+" after="+patched.length);
            }
        }
    }
}
