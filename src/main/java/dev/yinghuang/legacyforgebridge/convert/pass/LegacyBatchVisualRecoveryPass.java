package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVanillaRegistry1710;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Batch visual recovery for registered Forge 1.7.10 blocks and item icons.
 *
 * <p>Source icon literals, vanilla registry field references and exact source PNGs are inspected
 * without loading legacy code. Only the old converter's *exact* magenta/barrier fallbacks and
 * source-backed flat tool sprites may change. It never admits behavior, sound, damage, tile sync,
 * multiplayer packet handling, or faithful per-face/per-meta custom renderer equivalence.</p>
 *
 * <p>No mod, item, weapon, block, or numeric legacy ID participates in dispatch. Unsupported
 * icon expressions and special renderers retain a provisional status in the sidecar.</p>
 */
public final class LegacyBatchVisualRecoveryPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/batch-visual-recovery-preview.json";
    private static final String ITEMS = "net/minecraft/init/Items";
    private static final String BLOCKS = "net/minecraft/init/Blocks";
    private static final String ICON_REGISTER = "net/minecraft/client/renderer/texture/IIconRegister";
    private static final String BLOCK_ICON_METHOD = "func_149651_a";
    private static final String ICON_METHOD = "func_94245_a";
    private static final Set<String> HANDHELD = Set.of("sword", "pickaxe", "axe", "shovel", "hoe", "tool");
    private static final int MAX_SOURCE_ICONS = 64;
    private record Sprite(String namespace, String sourcePath, String basename) { }
    private record Processed(String id, String kind, String proof, int sourceSprites, int metaVariants) { }

    @Override public String id() { return "legacy-source-batch-visual-recovery"; }

    @Override public void apply(ConversionContext context) throws Exception {
        var registry = new LegacyRegistryAnalyzer();
        var registrations = registry.analyze(context.sourceJar());
        String ns = context.metadata().fabricId().toLowerCase(Locale.ROOT);
        if (!ns.matches("[a-z0-9_.-]+")) return;
        List<Processed> accepted = new ArrayList<>();
        Map<String,String> denied = new LinkedHashMap<>();
        try (JarFile source = new JarFile(context.sourceJar().toFile(), false)) {
            Map<String,ClassNode> classCache = new HashMap<>();
            for (var block : registrations.blocks()) {
                String id = ns + ":" + modernPath(block.registryName());
                try {
                    Processed result = recoverBlock(context.stagingDir(), ns, source, classCache,
                            block.implementationClass(), id);
                    if (result != null) accepted.add(result);
                } catch (Exception skip) {
                    denied.put(id, "block source visuals not safely restorable: " + skip.getClass().getSimpleName());
                }
            }
            for (var item : registrations.items()) {
                String id = ns + ":" + modernPath(item.registryName());
                try {
                    Processed result = recoverItem(context.stagingDir(), ns, source, classCache,
                            item.implementationClass(), id, registry.classifyItem(item.implementationClass()));
                    if (result != null) accepted.add(result);
                } catch (Exception skip) {
                    denied.put(id, "item source visuals not safely restorable: " + skip.getClass().getSimpleName());
                }
            }
        }
        if (accepted.isEmpty() && denied.isEmpty()) return;
        int blocks = 0,handheld = 0,icons = 0;
        StringBuilder report = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"sourceOnly\": false,\n  \"visualPreviewOnly\": true,\n  \"originalGameplayLogicExecuted\": false,\n  \"specialRendererEquivalenceProven\": false,\n  \"tileStateSyncProven\": false,\n  \"runtimeCombatSemanticsProven\": false,\n  \"sourceSha256\": ");
        quoted(report,context.sourceHash()).append(",\n  \"results\": [");
        for (var row : accepted) {
            if (!report.toString().endsWith("[")) report.append(',');
            report.append("\n    {\"id\":"); quoted(report,row.id());
            report.append(",\"change\":"); quoted(report,row.kind());
            report.append(",\"sourceEvidence\":"); quoted(report,row.proof());
            report.append(",\"sourceTextureCount\":").append(row.sourceSprites());
            report.append(",\"generatedMetadataModels\":").append(row.metaVariants());
            report.append(",\"gameplayComplete\":false}");
            switch(row.kind()) {
                case "BLOCK_STATIC_TEXTURE_PREVIEW" -> blocks++;
                case "SOURCE_TEXTURE_HANDHELD" -> handheld++;
                default -> icons++;
            }
        }
        report.append("\n  ],\n  \"skipped\": [");
        for (var entry : denied.entrySet()) {
            if (!report.toString().endsWith("[")) report.append(',');
            report.append("\n    {\"id\":"); quoted(report,entry.getKey());
            report.append(",\"reason\":"); quoted(report,entry.getValue()); report.append('}');
        }
        report.append("\n  ],\n  \"recoveredBlockVisualCount\": ").append(blocks);
        report.append(",\n  \"recoveredHandheldVisualCount\": ").append(handheld);
        report.append(",\n  \"recoveredFallbackIconCount\": ").append(icons).append("\n}\n");
        Path file = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(file.getParent());
        Files.writeString(file, report.toString(), StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-REV292-VISUAL-0001", SupportLevel.AUTO,
                "Source-owned batch visual recovery: placeholder blocks="+blocks
                +", handheld sprites="+handheld+", fallback items="+icons
                +"; state/face animation and server gameplay still unconverted.");
    }

    private static Processed recoverBlock(Path stage, String namespace, JarFile jar,
                                          Map<String,ClassNode> cache, String sourceClass, String id) throws Exception {
        String path = id.substring(id.indexOf(':')+1);
        Path model = stage.resolve("assets/"+namespace+"/models/block/"+path+".json");
        Path state = stage.resolve("assets/"+namespace+"/blockstates/"+path+".json");
        if (!Files.isRegularFile(model) || !Files.isRegularFile(state)) return null;
        String old = stripped(Files.readString(model));
        if (!old.equals("{\"parent\":\"minecraft:block/magenta_glazed_terracotta\"}")) return null;
        if (!simpleBlockState(Files.readString(state),namespace,path)) return null;
        ClassNode source = loadClass(jar,cache,sourceClass);
        if (source==null) return null;
        List<Sprite> iconSprites = registeredIcons(jar,source,namespace,"blocks");
        String evidence="EXACT_SOURCE_ICON_REGISTRATION";
        String vanilla = null;
        if (iconSprites.isEmpty()) {
            iconSprites = sourcePrefixIcons(jar,source,namespace,"blocks");
            evidence="BOUNDED_SOURCE_TEXTURE_NAME_COMPOSITION";
        }
        if (iconSprites.isEmpty()) {
            // Forge 1.7.10 also permits a Block constructor to install an exact
            // texture name directly rather than registering it through IIconRegister.
            // Require a single LDC -> own Block.textureName PUTFIELD pair; arbitrary
            // class string constants are NOT accepted as identity evidence.
            Sprite constructorTexture=sourceBlockTextureField(jar,source,namespace);
            if(constructorTexture!=null) {
                iconSprites=List.of(constructorTexture);
                evidence="EXACT_BLOCK_TEXTURE_FIELD_ASSIGNMENT";
            }
        }
        if (iconSprites.isEmpty()) {
            vanilla = sourceVanillaBlock(source);
            evidence="EXACT_SOURCE_VANILLA_BLOCK_CONSTRUCTOR";
        }
        if (iconSprites.isEmpty() && vanilla==null) return null;
        if (iconSprites.size()>MAX_SOURCE_ICONS) return null;
        List<String> material = new ArrayList<>();
        if (vanilla!=null) material.add(vanilla);
        else for (Sprite sprite : iconSprites) material.add(copyTexture(jar,stage,sprite,"block"));
        if (material.isEmpty()) return null;
        List<String> names = iconSprites.stream().map(Sprite::basename).toList();
        String top=null,side=null,bottom=null;
        for(int i=0;i<names.size();i++) {
            String name=names.get(i).toLowerCase(Locale.ROOT);
            if (name.endsWith("_top") && top==null) top=material.get(i);
            if (name.endsWith("_side") && side==null) side=material.get(i);
            if (name.endsWith("_bottom") && bottom==null) bottom=material.get(i);
        }
        if (top!=null && side==null) top=null;
        // Source icon order alone cannot prove every metadata/side selector. These are
        // explicitly labeled preview approximations and must not replace real model JSON.
        Map<String,String> models = new LinkedHashMap<>();
        StringBuilder variants = new StringBuilder("{\n  \"variants\": {\n");
        for (int meta=0;meta<16;meta++) {
            String sprite = material.get(meta % material.size());
            String fileName = path + "_lfb_batch_" + meta;
            String faceModel;
            if(top!=null) {
                String upper=top,lower=bottom==null?top:bottom,other=side;
                faceModel="{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":"+q(other)
                        +",\"up\":"+q(upper)+",\"down\":"+q(lower)
                        +",\"north\":"+q(other)+",\"south\":"+q(other)
                        +",\"east\":"+q(other)+",\"west\":"+q(other)+"}}\n";
            } else faceModel="{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":"+q(sprite)+"}}\n";
            models.put(fileName,faceModel);
            if(meta>0) variants.append(",\n");
            variants.append("    \"legacy_meta=").append(meta).append("\": {\"model\":")
                    .append(q(namespace+":block/"+fileName)).append("}");
        }
        variants.append("\n  }\n}\n");
        String previewModel = models.get(path+"_lfb_batch_0");
        // Validate every new path before writing. No mod-specific resource guesses.
        Path dir = stage.resolve("assets/"+namespace+"/models/block");
        for(var entry:models.entrySet()) if(Files.exists(dir.resolve(entry.getKey()+".json"))) return null;
        for(var entry:models.entrySet()) write(dir.resolve(entry.getKey()+".json"),entry.getValue());
        write(model,previewModel); write(state,variants.toString());
        Path held = stage.resolve("assets/"+namespace+"/models/item/"+path+".json");
        // Existing block item parent already points at <namespace>:block/<path>; no rewrite.
        return new Processed(id,"BLOCK_STATIC_TEXTURE_PREVIEW",evidence,material.size(),models.size());
    }

    private static Processed recoverItem(Path stage,String namespace,JarFile jar,
                                         Map<String,ClassNode> cache,String sourceClass,String id,String category)throws Exception {
        if (sourceClass==null || category==null) return null;
        String path=id.substring(id.indexOf(':')+1);
        Path model=stage.resolve("assets/"+namespace+"/models/item/"+path+".json");
        if(!Files.isRegularFile(model))return null;
        String original=Files.readString(model,StandardCharsets.UTF_8);
        String flat=stripped(original);
        if (HANDHELD.contains(category) && flat.contains("\"parent\":\"minecraft:item/generated\"")
                && flat.contains("\"layer0\"") && !flat.contains("\"overrides\"")) {
            write(model,original.replace("minecraft:item/generated","minecraft:item/handheld"));
            return new Processed(id,"SOURCE_TEXTURE_HANDHELD","SOURCE_REGISTERED_TOOL_FAMILY",1,0);
        }
        if (!flat.equals("{\"parent\":\"minecraft:item/barrier\"}")) return null;
        ClassNode owner=loadClass(jar,cache,sourceClass);
        if(owner==null)return null;
        if("bow".equals(category) && inherits(jar,cache,sourceClass,"net/minecraft/item/ItemBow")) {
            List<Sprite> options=sourceItemStem(jar,owner,namespace,"_standby");
            if(options.size()==1){
                String ref=copyTexture(jar,stage,options.getFirst(),"item");
                write(model,itemModel(ref,"minecraft:item/handheld"));
                return new Processed(id,"SOURCE_ICON_FALLBACK","REGISTERED_BOW_BASE_STANDBY",1,0);
            }
        }
        // Icon delegates to an actual pre-1.8 vanilla Item (e.g. a scaled icon wrapper).
        String vanilla=sourceVanillaItem(owner);
        if(vanilla!=null && HANDHELD.contains(category)) {
            write(model,itemModel(vanilla,"minecraft:item/handheld"));
            return new Processed(id,"SOURCE_ICON_FALLBACK","SOURCE_VANILLA_ITEM_ICON",1,0);
        }
        // Source-local ordered icon names (e.g. multiple trophy variant icons). We can show
        // the *first* source icon as a preview; metadata/dynamic selection is NOT proven.
        List<Sprite> local=localIconNames(jar,owner,namespace,"items");
        if(!local.isEmpty()) {
            String ref=copyTexture(jar,stage,local.getFirst(),"item");
            write(model,itemModel(ref,"minecraft:item/generated"));
            return new Processed(id,"SOURCE_ICON_FALLBACK","SOURCE_LOCAL_VARIANT_ICON_STATIC_ONLY",local.size(),0);
        }
        return null;
    }

    private static String itemModel(String texture, String parent){
        return "{\n  \"parent\": "+q(parent)+",\n  \"textures\": {\"layer0\": "+q(texture)+"}\n}\n";
    }

    private static List<Sprite> registeredIcons(JarFile jar,ClassNode source,String ns,String group) {
        LinkedHashMap<String,Sprite> selected=new LinkedHashMap<>();
        for (MethodNode m:source.methods) {
            if(!m.name.equals(BLOCK_ICON_METHOD) && !m.name.equals("registerBlockIcons"))continue;
            for(AbstractInsnNode n:m.instructions) if(n instanceof LdcInsnNode ldc && ldc.cst instanceof String str){
                Sprite sprite=exactSprite(jar,str,ns,group);
                if(sprite!=null && nearbyRegisterCall(n))selected.putIfAbsent(sprite.sourcePath(),sprite);
            }
        }
        return List.copyOf(selected.values());
    }
    private static boolean nearbyRegisterCall(AbstractInsnNode from) {
        int seen=0;
        for(AbstractInsnNode n=from.getNext();n!=null&&seen++<5;n=n.getNext()) {
            if(n instanceof MethodInsnNode call && call.owner.equals(ICON_REGISTER)
                    && (call.name.equals(ICON_METHOD)||call.name.equals("registerIcon")))return true;
            if(n instanceof LdcInsnNode)return false;
        }
        return false;
    }
    private static List<Sprite> sourcePrefixIcons(JarFile jar,ClassNode source,String ns,String group) {
        LinkedHashMap<String,Sprite> selected=new LinkedHashMap<>();
        for(MethodNode method:source.methods) for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof LdcInsnNode ldc && ldc.cst instanceof String raw) {
                int colon=raw.indexOf(':');
                if(colon<=0 || !raw.substring(0,colon).equalsIgnoreCase(ns))continue;
                String prefix=raw.substring(colon+1);
                if(prefix.length()<4 || !prefix.matches("[A-Za-z0-9_./-]+"))continue;
                if(!prefix.endsWith("_") && !hasSuffixInRegister(source))continue;
                List<Sprite> matches=prefixFamily(jar,ns,group,prefix);
                if(matches.size()>=2 && matches.size()<=MAX_SOURCE_ICONS)
                    for(Sprite sprite:matches)selected.putIfAbsent(sprite.sourcePath(),sprite);
            }
        return List.copyOf(selected.values());
    }
    private static boolean hasSuffixInRegister(ClassNode source){
        for(MethodNode method:source.methods) if(method.name.equals(BLOCK_ICON_METHOD)||method.name.equals("registerBlockIcons"))
            for(AbstractInsnNode n:method.instructions) if(n instanceof LdcInsnNode ldc && ldc.cst instanceof String value
                    && value.startsWith("_") && value.length()>1)return true;
        return false;
    }
    private static List<Sprite> prefixFamily(JarFile jar,String ns,String group,String prefix){
        List<Sprite> matches=new ArrayList<>();
        var entries=jar.entries();
        String head="assets/"+ns+"/textures/"+group+"/";
        while(entries.hasMoreElements()) {
            var entry=entries.nextElement();String path=entry.getName();
            if(!path.startsWith(head)||!path.endsWith(".png"))continue;
            String base=path.substring(head.length(),path.length()-4);
            if(base.matches("[a-zA-Z0-9_.-]+")&&base.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                matches.add(new Sprite(ns,path,base));
        }
        matches.sort(Comparator.comparing(Sprite::basename));
        return matches;
    }
    private static Sprite exactSprite(JarFile jar,String text,String expectedNs,String group){
        int colon=text.indexOf(':');if(colon<=0)return null;
        String ns=text.substring(0,colon).toLowerCase(Locale.ROOT);
        String path=text.substring(colon+1);
        if(!ns.equals(expectedNs)||!path.matches("[A-Za-z0-9_./-]+")||path.contains(".."))return null;
        String asset="assets/"+ns+"/textures/"+group+"/"+path+".png";
        if(jar.getJarEntry(asset)==null)return null;
        return new Sprite(ns,asset,path);
    }
    private static Sprite sourceBlockTextureField(JarFile jar, ClassNode owner, String namespace){
        Sprite proven=null;
        for(MethodNode ctor:owner.methods) {
            if(!ctor.name.equals("<init>"))continue;
            for(AbstractInsnNode instruction=ctor.instructions.getFirst();instruction!=null;instruction=instruction.getNext()) {
                if(!(instruction instanceof LdcInsnNode literal) || !(literal.cst instanceof String texture))continue;
                AbstractInsnNode next=instruction.getNext();
                while(next!=null && next.getOpcode()<0)next=next.getNext();
                AbstractInsnNode before=instruction.getPrevious();
                while(before!=null && before.getOpcode()<0)before=before.getPrevious();
                if(!(before instanceof VarInsnNode receiver) || receiver.getOpcode()!=Opcodes.ALOAD || receiver.var!=0
                        || !(next instanceof FieldInsnNode field) || field.getOpcode()!=Opcodes.PUTFIELD
                        || !(field.owner.equals(owner.name) || field.owner.equals("net/minecraft/block/Block"))
                        || !field.name.equals("field_149768_d")
                        || !field.desc.equals("Ljava/lang/String;"))continue;
                Sprite resolved=exactSprite(jar,texture,namespace,"blocks");
                if(resolved==null)return null;
                if(proven!=null && !proven.sourcePath().equals(resolved.sourcePath()))return null;
                proven=resolved;
            }
        }
        return proven;
    }

    private static String sourceVanillaBlock(ClassNode node){
        if(node==null||node.superName==null)return null;
        String icon=null;
        for(MethodNode method:node.methods)if(method.name.equals("<init>")){
            FieldInsnNode found=null;
            for(AbstractInsnNode insn:method.instructions)if(insn instanceof FieldInsnNode field
                    && insn.getOpcode()==Opcodes.GETSTATIC && field.owner.equals(BLOCKS)) {
                if(found!=null)return null;
                found=field;
            }
            if(found==null)continue;
            boolean inputToParent=false;
            for(AbstractInsnNode next=found.getNext();next!=null;next=next.getNext())
                if(next instanceof MethodInsnNode call && call.owner.equals(node.superName)
                    && call.name.equals("<init>")
                    && call.desc.equals("(Lnet/minecraft/block/Block;)V")){inputToParent=true;break;}
            if(!inputToParent)continue;
            var registry=LegacyVanillaRegistry1710.resolve(found.owner,found.name);
            if(registry.isEmpty())continue;
            String id=registry.get().registryName();
            String mapped=switch(id){case "log"->"oak_log";case "leaves"->"oak_leaves";default->id;};
            if(!mapped.matches("[a-z0-9_]+"))return null;
            String candidate="minecraft:block/"+mapped;
            if(icon!=null && !icon.equals(candidate))return null;
            icon=candidate;
        }
        return icon;
    }
    private static String sourceVanillaItem(ClassNode node){
        String result=null;
        for(MethodNode method:node.methods)if(method.name.equals("registerIcons")||method.name.equals("func_94581_a")) {
            boolean iconWrapper=false;
            for(AbstractInsnNode n:method.instructions)if(n instanceof TypeInsnNode t && t.getOpcode()==Opcodes.NEW
                    && t.desc.endsWith("/GiantItemIcon"))iconWrapper=true;
            if(!iconWrapper)continue;
            for(AbstractInsnNode n:method.instructions)if(n instanceof FieldInsnNode f && f.getOpcode()==Opcodes.GETSTATIC
                    && f.owner.equals(ITEMS)){
                var registry=LegacyVanillaRegistry1710.resolve(f.owner,f.name);
                if(registry.isEmpty())continue;
                String resolved="minecraft:item/"+registry.get().registryName();
                if(result!=null && !result.equals(resolved))return null;
                result=resolved;
            }
        }
        return result;
    }
    private static List<Sprite> sourceItemStem(JarFile jar,ClassNode owner,String namespace,String suffix){
        LinkedHashMap<String,Sprite> match=new LinkedHashMap<>();
        for(MethodNode method:owner.methods)for(AbstractInsnNode n:method.instructions)
            if(n instanceof LdcInsnNode ldc && ldc.cst instanceof String value){
                int colon=value.indexOf(':');if(colon<=0||!value.substring(0,colon).equalsIgnoreCase(namespace))continue;
                String path=value.substring(colon+1);
                Sprite sprite=exactSprite(jar,namespace+":"+path+suffix,namespace,"items");
                if(sprite!=null)match.putIfAbsent(sprite.sourcePath(),sprite);
            }
        return List.copyOf(match.values());
    }
    private static List<Sprite> localIconNames(JarFile jar,ClassNode owner,String namespace,String group){
        LinkedHashMap<String,Sprite> list=new LinkedHashMap<>();
        for(MethodNode method:owner.methods)for(AbstractInsnNode n:method.instructions)
            if(n instanceof LdcInsnNode ldc && ldc.cst instanceof String name){
                Sprite sprite=exactSprite(jar,namespace+":"+name,namespace,group);
                if(sprite!=null)list.putIfAbsent(sprite.sourcePath(),sprite);
            }
        return List.copyOf(list.values());
    }
    private static boolean inherits(JarFile jar,Map<String,ClassNode> cache,String child,String base)throws IOException{
        Set<String> seen=new HashSet<>();
        for(String cls=child;cls!=null&&seen.add(cls);){
            if(cls.equals(base))return true;
            ClassNode node=loadClass(jar,cache,cls);cls=node==null?null:node.superName;
        }
        return false;
    }
    private static ClassNode loadClass(JarFile jar,Map<String,ClassNode> cache,String name)throws IOException {
        if(name==null||!name.matches("[a-zA-Z0-9_$]+(?:/[a-zA-Z0-9_$]+)*"))return null;
        if(cache.containsKey(name))return cache.get(name);
        JarEntry entry=jar.getJarEntry(name+".class");
        if(entry==null){cache.put(name,null);return null;}
        try(InputStream in=jar.getInputStream(entry)){
            ClassNode node=new ClassNode(Opcodes.ASM9);
            new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            if(!node.name.equals(name))return null;
            cache.put(name,node);return node;
        }catch(RuntimeException invalid){cache.put(name,null);return null;}
    }
    private static String copyTexture(JarFile source,Path stage,Sprite sprite,String kind)throws Exception{
        JarEntry entry=source.getJarEntry(sprite.sourcePath());if(entry==null)throw new IOException("Original source PNG absent");
        byte[] bytes;
        try(InputStream in=source.getInputStream(entry)){bytes=in.readNBytes(1048577);}
        if(bytes.length<24||bytes.length>1048576 || bytes[0]!=(byte)0x89||bytes[1]!=0x50||bytes[2]!=0x4e||bytes[3]!=0x47)
            throw new IOException("Invalid original PNG");
        byte[] sha=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex=new StringBuilder();for(byte x:sha)hex.append(String.format("%02x",x & 255));
        String asset="assets/"+sprite.namespace()+"/textures/"+kind+"/lfb_batch/"+hex+".png";
        Path path=stage.resolve(asset);Files.createDirectories(path.getParent());
        if(Files.exists(path)){
            if(!Arrays.equals(Files.readAllBytes(path),bytes))throw new IOException("Colliding staged visual texture");
        }else Files.write(path,bytes);
        return sprite.namespace()+":"+kind+"/lfb_batch/"+hex;
    }
    private static boolean simpleBlockState(String json,String ns,String id){
        String flat=stripped(json).replace("\\u003d", "=").replace("\\u003D", "=");
        if(flat.equals("{\"variants\":{\"\":{\"model\":\""+ns+":block/"+id+"\"}}}"))return true;
        if(!flat.startsWith("{\"variants\":{")||!flat.endsWith("}}"))return false;
        if(flat.contains("\"multipart\""))return false;
        int refs=0;for(int m=0;m<16;m++)if(flat.contains("\"legacy_meta="+m+"\":{\"model\":\""+ns+":block/"+id+"\"}"))refs++;
        if(refs!=16)return false;
        return true;
    }
    private static String modernPath(String name){
        String path=name==null?"":name.trim().toLowerCase(Locale.ROOT).replace('\\','/')
                .replaceAll("[^a-z0-9/._-]","_").replaceAll("_+","_");
        while(path.startsWith("/"))path=path.substring(1);
        if(path.isBlank()||path.contains(".."))throw new IllegalArgumentException("Unsafe legacy registry path");
        return path;
    }
    private static String stripped(String data){return data.replaceAll("\\s+","");}
    private static StringBuilder quoted(StringBuilder out,String text){return out.append(q(text));}
    private static String q(String text){return "\""+text.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";}
    private static void write(Path file,String text)throws IOException {
        Files.createDirectories(file.getParent());Files.writeString(file,text,StandardCharsets.UTF_8);
    }
}
