import dev.yinghuang.legacyforgebridge.convert.LegacyFluentTextureAnalyzer;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
public class CorpusProbe {
    public static void main(String[] args)throws Exception{
        var result=new LegacyFluentTextureAnalyzer().analyze(Path.of(args[0]));
        System.out.println("EVIDENCE\t"+result.evidence().size());
        for(var e:result.evidence())System.out.println("FIELD\t"+e.field().owner()+"\t"+e.field().name()+"\t"+e.implementationClass()+"\t"+e.texture()+"\t"+e.resourcePresent());
        Set<LegacyFluentTextureAnalyzer.FieldKey> registered=new HashSet<>();
        try(JarFile jar=new JarFile(args[0])){
            var entries=jar.entries();
            while(entries.hasMoreElements()){
                var e=entries.nextElement();if(!e.getName().endsWith(".class"))continue;
                ClassNode c=new ClassNode(Opcodes.ASM9);
                try(var in=jar.getInputStream(e)){new ClassReader(in.readAllBytes()).accept(c,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}
                for(MethodNode m:c.methods){
                    boolean relevant=false;for(var n:m.instructions)if(n instanceof MethodInsnNode call&&call.owner.equals("cpw/mods/fml/common/registry/GameRegistry")&&call.name.equals("registerItem"))relevant=true;
                    if(!relevant)continue;
                    var frames=new Analyzer<>(new SourceInterpreter()).analyze(c.name,m);
                    for(int i=0;i<m.instructions.size();i++)if(m.instructions.get(i) instanceof MethodInsnNode call
                            &&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("cpw/mods/fml/common/registry/GameRegistry")
                            &&call.name.equals("registerItem")&&call.desc.equals("(Lnet/minecraft/item/Item;Ljava/lang/String;)V")&&frames[i]!=null){
                        var v=frames[i].getStack(frames[i].getStackSize()-2);
                        if(v.insns.size()==1&&v.insns.iterator().next() instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC)
                            registered.add(new LegacyFluentTextureAnalyzer.FieldKey(f.owner,f.name,f.desc));
                    }
                }
            }
        }
        long matched=result.evidence().stream().filter(e->registered.contains(e.field())).count();
        System.out.println("DIRECT_REGISTERED_FIELDS\t"+registered.size());
        System.out.println("DIRECT_REGISTERED_MATCHES\t"+matched);
        for(var e:result.evidence())if(!registered.contains(e.field()))System.out.println("NOT_DIRECTLY_REGISTERED\t"+e.field().owner()+"\t"+e.field().name());
        System.out.println("EXCLUSIONS\t"+result.exclusions().size());
        for(var e:result.exclusions())System.out.println("EXCLUDE\t"+e.field().owner()+"\t"+e.field().name()+"\t"+e.reason());
    }
}
