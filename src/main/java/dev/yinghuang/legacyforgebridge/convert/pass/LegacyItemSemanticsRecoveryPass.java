package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyMaterial1710Analyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** General source-based localization + armor slots/protection + sword baseline data.
 * This stage runs after the registry and language passes and before modern bytecode codegen.
 * It does not implement special legacy gameplay hooks, arrow release, or mob AI.
 */
public final class LegacyItemSemanticsRecoveryPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/item-semantics-recovery.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern STATIC_FIELD=Pattern.compile(
            "StaticFieldReference\\[owner=([^,]+), name=([^,]+), descriptor=([^\\]]+)\\]");
    private static final String ARMOR_CTOR="(Lnet/minecraft/item/ItemArmor$ArmorMaterial;II)V";
    private static final String TOOL_CTOR="(Lnet/minecraft/item/Item$ToolMaterial;)V";
    private static final int[] SLOT_DURABILITY_FACTOR={11,16,15,13};
    @Override public String id(){return "legacy-source-item-localization-equipment";}
    @Override public void apply(ConversionContext ctx) throws Exception {
        JsonObject result=process(ctx.sourceJar(),ctx.stagingDir(),ctx.metadata().fabricId(),ctx.sourceHash());
        if(result!=null) ctx.diagnostics().info("LFB-ITEM-SEMANTICS-0001",SupportLevel.AUTO,
                "Reconciled source-owned item names, equipment slots, armor points and sword baselines; "
                        + "special legacy abilities remain server-authoritative.");
    }
    /** Pure, source-driven staging transform exposed for exact original-JAR integration tests. */
    public static JsonObject process(Path sourceJar,Path root,String fallbackNamespace,String sourceHash) throws IOException {
        Path contentPath=root.resolve("legacyforgebridge/converted-content.json");
        if(!Files.isRegularFile(contentPath))return null;
        JsonObject content=JsonParser.parseString(Files.readString(contentPath,StandardCharsets.UTF_8)).getAsJsonObject();
        String namespace=content.has("namespace")?content.get("namespace").getAsString():fallbackNamespace;
        Path langDir=root.resolve("assets").resolve(namespace).resolve("lang");
        TreeMap<String,JsonObject> languages=new TreeMap<>();
        if(Files.isDirectory(langDir))try(var stream=Files.list(langDir)) {
            for(Path path:stream.filter(x->x.getFileName().toString().matches("[a-zA-Z_]+\\.json")).toList()){
                try {languages.put(path.getFileName().toString(),
                        JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject());}
                catch(RuntimeException ignored) { /* Do not destroy malformed locale files. */ }
            }
        }
        JsonObject english=languages.get("en_us.json");
        JsonArray items=content.has("items")?content.getAsJsonArray("items"):new JsonArray();
        JsonArray blocks=content.has("blocks")?content.getAsJsonArray("blocks"):new JsonArray();
        Set<String> sourceOwners=new LinkedHashSet<>();
        for(JsonElement elem:items){
            if(!elem.isJsonObject())continue;
            var field=field(elem.getAsJsonObject());if(field!=null&&field.owner.matches("[a-zA-Z0-9_/$]+"))
                sourceOwners.add(field.owner);
        }
        var materials=new LegacyMaterial1710Analyzer().analyze(sourceJar,sourceOwners);
        int renamedItems=0,renamedBlocks=0,armorSlots=0,armorStats=0,swords=0,addedLanguage=0;
        JsonArray proofRows=new JsonArray();
        for(JsonArray array:List.of(blocks,items))for(JsonElement element:array){
            if(!element.isJsonObject())continue;
            JsonObject item=element.getAsJsonObject();
            boolean isItem=array==items;
            if(!item.has("descriptionKey")||!item.has("id"))continue;
            String original=item.get("descriptionKey").getAsString();
            String prefix="lfb.converted."+namespace+".";
            if(!original.startsWith(prefix))continue;
            String normalized=normalize(original,prefix);
            if(!original.equals(normalized)) {
                item.addProperty("descriptionKey",normalized);
                if(isItem)renamedItems++;else renamedBlocks++;
            }
            String registry=item.has("legacyRegistryName")?item.get("legacyRegistryName").getAsString():"";
            String fallbackEnglish=sourceDisplayName(registry);
            String englishValue=findTranslation(english,normalized);
            if(englishValue==null)englishValue=fallbackEnglish;
            for(var entry:languages.entrySet()) {
                JsonObject dict=entry.getValue();
                if(dict.has(normalized))continue;
                String local=findTranslation(dict,normalized);
                if(local==null)local=englishValue;
                dict.addProperty(normalized,local);
                addedLanguage++;
            }
            if(!isItem)continue;
            if(!item.has("kind"))continue;
            String kind=item.get("kind").getAsString();
            var field=field(item);
            String constructor=item.has("sourceConstructor")?item.get("sourceConstructor").getAsString():"";
            if(field==null)continue;
            JsonObject row=new JsonObject();row.addProperty("id",item.get("id").getAsString());
            if(kind.equals("armor")&&constructor.equals(ARMOR_CTOR)) {
                JsonArray args=item.getAsJsonArray("sourceConstructorArgs");
                if(args==null||args.size()!=3||!args.get(2).isJsonPrimitive()
                        ||!args.get(2).getAsJsonPrimitive().isNumber())continue;
                int slot=args.get(2).getAsInt();if(slot<0||slot>3)continue;
                item.addProperty("kind","armor_slot_"+slot);
                item.addProperty("legacyArmorSlot",(Number)Integer.valueOf(slot));
                row.addProperty("sourceArmorSlot",(Number)Integer.valueOf(slot));
                armorSlots++;
                var material=materials.armor().get(field.key());
                if(material!=null) {
                    int points=material.points()[slot];
                    item.addProperty("armor",(Number)Integer.valueOf(points));
                    item.addProperty("durability",(Number)Integer.valueOf(material.factor()*SLOT_DURABILITY_FACTOR[slot]));
                    item.addProperty("legacyArmorMaterialSource",field.key());
                    row.addProperty("sourceArmorPoints",(Number)Integer.valueOf(points));
                    armorStats++;
                }
                proofRows.add(row);
            } else if(kind.equals("sword")&&constructor.equals(TOOL_CTOR)) {
                var tool=materials.tools().get(field.key());
                if(tool==null)continue;
                // Vanilla 1.7.10 ItemSword base hit = 4 + ToolMaterial.getDamageVsEntity().
                // -2.4 is the modern sword attack-speed adapter, not a source gameplay proof.
                item.addProperty("attackDamage",(Number)Float.valueOf(4.0F+tool.damageBonus()));
                item.addProperty("attackSpeed",(Number)Float.valueOf(-2.4F));
                item.addProperty("durability",(Number)Integer.valueOf(tool.durability()));
                item.addProperty("legacyToolMaterialSource",field.key());
                row.addProperty("sourceSwordBaseAttack",(Number)Float.valueOf(4.0F+tool.damageBonus()));
                row.addProperty("modernAttackSpeedAdapter",(Number)Float.valueOf(-2.4F));
                proofRows.add(row);swords++;
            }
        }
        for(var locale:languages.entrySet()) {
            Path output=langDir.resolve(locale.getKey());
            Files.writeString(output,JSON.toJson(locale.getValue())+"\n",StandardCharsets.UTF_8);
        }
        Files.writeString(contentPath,JSON.toJson(content)+"\n",StandardCharsets.UTF_8);
        JsonObject result=new JsonObject();result.addProperty("sourceSha256",sourceHash);
        result.addProperty("sourceOnlySpecialBehaviorsUnchanged",Boolean.TRUE);
        result.addProperty("translationKeysFixedItems",(Number)Integer.valueOf(renamedItems));
        result.addProperty("translationKeysFixedBlocks",(Number)Integer.valueOf(renamedBlocks));
        result.addProperty("missingLocaleEntriesRecovered",(Number)Integer.valueOf(addedLanguage));
        result.addProperty("sourceArmorSlotsRecovered",(Number)Integer.valueOf(armorSlots));
        result.addProperty("sourceArmorProtectionRecovered",(Number)Integer.valueOf(armorStats));
        result.addProperty("sourceSwordBaselineRecovered",(Number)Integer.valueOf(swords));
        result.addProperty("specialWeaponSkillsRuntimeWired",Boolean.FALSE);
        result.addProperty("legacyServerGameplayReplayed",Boolean.FALSE);
        result.add("itemProofs",proofRows);
        Path proof=root.resolve(OUTPUT);Files.createDirectories(proof.getParent());
        Files.writeString(proof,JSON.toJson(result)+"\n",StandardCharsets.UTF_8);
        return result;
    }
    private static String normalize(String original, String prefix) {
        String suffix=original.substring(prefix.length());
        if(suffix.startsWith("item.item."))return prefix+"item."+suffix.substring(10);
        if(suffix.startsWith("tile.tile."))return prefix+"tile."+suffix.substring(10);
        return original;
    }
    /** Legacy language variants use name.0.name; the first variant is a *display* fallback only. */
    private static String findTranslation(JsonObject dict, String key) {
        if(dict==null)return null;
        for(String attempt:List.of(key,key.replaceFirst("\\.name$",".0.name"))) {
            if(dict.has(attempt) && dict.get(attempt).isJsonPrimitive()) {
                String value=dict.get(attempt).getAsString();if(!value.isBlank())return value;
            }
        }
        return null;
    }
    private static String sourceDisplayName(String registry) {
        String part=registry.replaceFirst("^(item|tile)\\.","").replaceAll("(?<=[a-z])(?=[A-Z])"," ")
                .replace('_',' ').replace('.',' ');
        return part.isBlank()?"Legacy Item":part;
    }
    private record SourceField(String owner,String name,String descriptor){String key(){return owner+"#"+name;}}
    private static SourceField field(JsonObject item){
        if(!item.has("sourceConstructorArgs"))return null;
        JsonArray args=item.getAsJsonArray("sourceConstructorArgs");
        if(args.isEmpty()||!args.get(0).isJsonPrimitive())return null;
        Matcher m=STATIC_FIELD.matcher(args.get(0).getAsString());
        return m.matches()?new SourceField(m.group(1),m.group(2),m.group(3)):null;
    }
}
