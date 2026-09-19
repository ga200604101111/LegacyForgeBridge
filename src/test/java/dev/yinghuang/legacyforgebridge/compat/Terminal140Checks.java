package dev.yinghuang.legacyforgebridge.compat;

import java.io.InputStream;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Uses real dependency bytecode for edge coverage; does not claim a live Mixin/client launch. */
public final class Terminal140Checks {
    private Terminal140Checks() { }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void identityShortcutPolicy() {
        check(!LegacyTerminalBlockRewritePolicy.maySkip(LegacyTerminalBlockRewritePolicy.PROTOCOL,true,true),"Terminal state rewrite skipped");
        check(LegacyTerminalBlockRewritePolicy.maySkip("earlier-protocol",true,true),"Unrelated identity shortcut disabled");
        check(LegacyTerminalBlockRewritePolicy.maySkip(LegacyTerminalBlockRewritePolicy.PROTOCOL,false,true),"Item/block-id identity shortcut disabled");
        check(!LegacyTerminalBlockRewritePolicy.maySkip("earlier-protocol",true,false),"Non-identity mapping skipped");
    }
    public static void nativeMetadataAndDamageSelection() {
        for(int value=0;value<=65535;value++) {
            check(LegacyStackMetadataPolicy.select(value,0,1,false)==value,"Native subtype lost at "+value);
            check(LegacyStackMetadataPolicy.select(null,null,value,false)==value,"Carrier subtype lost at "+value);
            check(LegacyStackMetadataPolicy.select(0,value,0,true)==value,"Durability update lost at "+value);
        }
        check(LegacyStackMetadataPolicy.select(null,null,null,false)==0,"No-data default changed");
        check(LegacyStackMetadataPolicy.select(0,null,7,false)==0,"Explicit native meta zero ignored");
    }
    public static void invalidMetadataRejected() {
        for(int value:List.of(-1,65536,Integer.MAX_VALUE,Integer.MIN_VALUE)) {
            boolean rejected=false;try{LegacyStackMetadataPolicy.select(value,null,null,false);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"Invalid metadata admitted: "+value);
        }
    }
    static ClassNode read(String name)throws Exception {
        try(InputStream in=Terminal140Checks.class.getClassLoader().getResourceAsStream(name+".class")) {
            if(in==null)throw new AssertionError("Missing class "+name);ClassNode node=new ClassNode();new ClassReader(in.readAllBytes()).accept(node,0);return node;
        }
    }
    public static void runtimeHasAllSevenShortcutSites()throws Exception {
        var node=read("com/viaversion/viaversion/rewriter/BlockRewriter");
        Map<String,Integer> expected=Map.of("registerBlockUpdate",1,"registerChunkBlocksUpdate",1,"registerSectionBlocksUpdate",1,"registerSectionBlocksUpdate1_20",1,"registerLevelEvent",2,"handleChunk1_18",1);
        for(var e:expected.entrySet()) {
            int count=0;for(var m:node.methods)if(m.name.equals(e.getKey()))for(var n:m.instructions)
                if(n instanceof MethodInsnNode call && call.owner.equals("com/viaversion/viaversion/api/data/Mappings")&&call.name.equals("isIntIdIdentity"))count++;
            check(count==e.getValue(),"Changed upstream shortcut sites for "+e.getKey()+": "+count);
        }
    }
    public static void compiledMixinsAreWiredToExactBoundaries()throws Exception {
        var mixin=read("dev/yinghuang/legacyforgebridge/mixin/client/ViaTerminalBlockRewriteMixin");
        AnnotationNode redirect=null;
        for(var m:mixin.methods)if(m.visibleAnnotations!=null)for(var a:m.visibleAnnotations)if(a.desc.endsWith("/Redirect;"))redirect=a;
        if(redirect==null)for(var m:mixin.methods)if(m.invisibleAnnotations!=null)for(var a:m.invisibleAnnotations)if(a.desc.endsWith("/Redirect;"))redirect=a;
        check(redirect!=null,"Terminal redirect missing");
        Map<String,Object> values=new HashMap<>();for(int i=0;i<redirect.values.size();i+=2)values.put((String)redirect.values.get(i),redirect.values.get(i+1));
        check(Integer.valueOf(7).equals(values.get("require")),"Not all seven terminal shortcut sites are required");
        check(new HashSet<>((List<?>)values.get("method")).equals(Set.of("registerBlockUpdate","registerChunkBlocksUpdate","registerSectionBlocksUpdate","registerSectionBlocksUpdate1_20","registerLevelEvent","handleChunk1_18")),"Terminal hook family incomplete");
        var nativeCodec=read("com/viaversion/viaversion/api/type/types/item/StructuredDataType");
        check(nativeCodec.methods.stream().anyMatch(m->m.name.equals("key")&&m.desc.equals("(I)Lcom/viaversion/viaversion/api/minecraft/data/StructuredDataKey;")),"Native codec descriptor changed");
        var bridge=read("dev/yinghuang/legacyforgebridge/compat/LegacyViaStackComponents");boolean nativeVersion=false;
        for(var m:bridge.methods)if(m.name.equals("key"))for(var n:m.instructions)
            if(n instanceof FieldInsnNode field&&field.name.equals("V1_21_11"))nativeVersion=true;
        check(nativeVersion,"Metadata codec not scoped to native protocol");
    }
    public static void main(String[] args)throws Exception {
        identityShortcutPolicy();nativeMetadataAndDamageSelection();invalidMetadataRejected();runtimeHasAllSevenShortcutSites();
        System.out.println("Terminal140Checks: policy, 196608 metadata selections, invalid inputs, 7 runtime shortcut sites passed; live client not run");
    }
}
